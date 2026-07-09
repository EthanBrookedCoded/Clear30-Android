package org.clear30.views.existinguser.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.clear30.data.GroupController
import org.clear30.data.supabase.getGroupActivityItems
import org.clear30.data.supabase.sendGroupPing
import org.clear30.data.supabase.updateGroupSubscriptions
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.Clear30GroupMember
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.GradientActionButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * GroupsTab — ported from the Group views. Shows the user's accountability group
 * (header + member ranking by days checked in) via [GroupController], or a
 * create/join prompt when they aren't in one. The create/join/leave actions
 * fan out to [GroupController.create] / [GroupController.join] /
 * [GroupController.leave], each of which writes through to Supabase and then
 * refreshes the group state.
 */
@Composable
fun GroupsTab(program: org.clear30.data.model.Program, userInfo: UserInfo) {
    val controller = remember { GroupController(userInfo, program) }
    val group by controller.group.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var creatingGroup by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { controller.refresh() }

    // Tap-to-detail for a member — full-bleed; system back returns to roster.
    var memberDetail by remember { mutableStateOf<org.clear30.data.model.Clear30GroupMember?>(null) }
    memberDetail?.let { m ->
        val g = group ?: return@let
        androidx.activity.compose.BackHandler { memberDetail = null }
        MemberDetailScreen(m, g, userInfo, onBack = { memberDetail = null })
        return
    }

    // Deep link → auto-join with a code from clear30://group/<code>. We only
    // fire when the user isn't already in a group; otherwise the link is a
    // no-op and we drop it. Errors surface through the regular `error` field.
    val sub by org.clear30.AppState.pendingSubRoute.collectAsStateWithLifecycle()
    LaunchedEffect(sub) {
        val r = sub as? org.clear30.data.DeepLinkRoute.Group ?: return@LaunchedEffect
        org.clear30.AppState.requestSubRoute(null)
        if (group != null) return@LaunchedEffect
        busy = true
        error = controller.join(r.code)
        busy = false
    }

    val g = group
    if (g == null) {
        // Not in a group — 1:1 of iOS GroupCreation: heading, the swipeable
        // benefit carousel (image cards, fixed 450dp, no dots), and the create
        // CTA. Creation is INSTANT — no name prompt; the group is auto-named
        // "<name>'s Group" with the default hue, exactly like iOS createGroup().
        // (The join-with-a-code affordance is Android-only: iOS joins via the
        // invite deep link, which Android also supports; the code entry is the
        // discoverable fallback.)
        fun createGroup() {
            if (creatingGroup) return
            creatingGroup = true
            error = null
            scope.launch {
                error = controller.create("${userInfo.name}'s Group")
                creatingGroup = false
            }
        }

        if (creatingGroup) {
            // iOS creatingGroupView: centered LoadingIcon + dim caption.
            Column(
                Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                org.clear30.views.components.LoadingIcon()
                Spacer(Modifier.height(Dimens.cardSpacing))
                SmallText("Creating your group...", color = Clear30Colors.text.copy(alpha = 0.5f))
                Spacer(Modifier.weight(1f))
            }
            return
        }

        val slides = remember {
            listOf(
                Triple(org.clear30.R.drawable.lobby_group_members, "🥇 Group Leaderboard", "See everyone's daily progress at a glance, and celebrate your wins together."),
                Triple(org.clear30.R.drawable.lobby_group_check_in, "🛎️ Accountability Tap", "Give your friends a gentle reminder to check in—help each other stay on track and committed."),
                Triple(org.clear30.R.drawable.lobby_group_note, "📝 Send Encouraging Notes", "Easily send supportive or congratulatory messages directly to your friends."),
            )
        }
        val pagerState = rememberPagerState(pageCount = { slides.size })
        LaunchedEffect(pagerState.currentPage) { org.clear30.views.theme.Haptics.lightImpact() }
        Column(
            Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        ) {
            org.clear30.views.components.Heading1("Groups")
            Spacer(Modifier.weight(1f))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.height(450.dp).fillMaxWidth(),
                pageSpacing = Dimens.cardSpacing,
            ) { page ->
                val (image, title, body) = slides[page]
                GroupBenefitSlide(image, title, body)
            }
            Spacer(Modifier.weight(1f))
            GradientActionButton("person.2.fill", Clear30Gradients.clear30, "Start Your Group") { createGroup() }
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            GradientActionButton("person.3", Clear30Gradients.community, "Join with a code") { showJoin = true }
            error?.let { msg ->
                SmallText(
                    msg,
                    Modifier.padding(top = Dimens.cardSpacing / 2),
                    color = Clear30Colors.red2.copy(alpha = 0.75f),
                )
            }
        }
    } else {
        // In a group — iOS GroupAll: header + pill picker + the selected tab.
        var section by remember { mutableStateOf(GroupSection.MEMBERS) }
        Column(
            Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        ) {
            GroupHeader(g, userInfo, section)
            Spacer(Modifier.size(Dimens.cardSpacing))
            GroupSectionPicker(section, g.gradient) { section = it }
            Spacer(Modifier.size(Dimens.cardSpacing))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (section) {
                    GroupSection.MEMBERS -> MembersTab(g, userInfo, controller, onMemberTap = { memberDetail = it })
                    GroupSection.CHAT -> GroupChat(g, userInfo, onBack = { section = GroupSection.MEMBERS })
                    GroupSection.NOTES -> NotesTab(g) { msg ->
                        scope.launch { busy = true; error = controller.addNote(msg); busy = false }
                    }
                    GroupSection.SETTINGS -> GroupSettingsTab(g, userInfo, controller, scope) { showLeaveConfirm = true }
                }
            }
            error?.let { msg -> SmallText(msg, color = Clear30Colors.red2.copy(alpha = 0.75f)) }
        }
    }

    if (showJoin) {
        JoinGroupDialog(
            busy = busy,
            onDismiss = { showJoin = false; error = null },
            onSubmit = { code ->
                if (code.isBlank()) return@JoinGroupDialog
                busy = true
                scope.launch {
                    error = controller.join(code.trim())
                    busy = false
                    if (error == null) showJoin = false
                }
            },
        )
    }
    if (showLeaveConfirm) {
        LeaveGroupDialog(
            busy = busy,
            onDismiss = { showLeaveConfirm = false },
            onConfirm = {
                busy = true
                scope.launch {
                    error = controller.leave()
                    busy = false
                    if (error == null) showLeaveConfirm = false
                }
            },
        )
    }
}

/**
 * Recent group activity — loads `groups.group_activity` and renders the latest
 * smoked / didn't-smoke / joined / note events with the member's emoji + name.
 */
@Composable
private fun GroupActivitySection(group: Clear30Group) {
    val items = remember { androidx.compose.runtime.mutableStateListOf<org.clear30.data.model.Clear30GroupActivityItem>() }
    LaunchedEffect(group.id) {
        org.clear30.data.supabase.SupabaseController.getGroupActivityItems(group.id, count = 12)
            .onSuccess { items.clear(); items.addAll(it) }
    }
    if (items.isEmpty()) return

    val labels = remember(group.members) { group.members.associate { it.memberID to ("${it.emoji}  ${it.name}").trim() } }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading2("Recent activity")
        items.forEach { item ->
            val verb = when (item.type) {
                org.clear30.data.model.Clear30GroupActivityItemType.SMOKED -> "smoked."
                org.clear30.data.model.Clear30GroupActivityItemType.SOBER -> "didn't smoke!"
                org.clear30.data.model.Clear30GroupActivityItemType.JOINED -> "joined the group!"
                org.clear30.data.model.Clear30GroupActivityItemType.MESSAGE -> "sent a note."
                null -> item.activity
            }
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    SmallText(labels[item.userId] ?: "A member", color = Clear30Colors.text.copy(alpha = 0.75f))
                    SmallText(verb)
                }
            }
        }
    }
}

@Composable
private fun GroupNotesSection(notes: List<org.clear30.data.model.Clear30GroupNote>, onPost: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Heading2("Notes")
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            TinyText("${notes.size} total", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        if (notes.isEmpty()) {
            // Richer empty state — gradient card + emoji + clear next-step copy.
            Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.community) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    SmallText("💬", color = androidx.compose.ui.graphics.Color.White)
                    SmallText("Be the first to share", color = androidx.compose.ui.graphics.Color.White)
                    TinyText(
                        "Notes appear here for everyone in the group.",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        } else {
            // Paginate locally — show 5, then "Show more" expands to all.
            var showAll by remember { mutableStateOf(false) }
            val sorted = notes.sortedByDescending { it.timestamp }
            val visible = if (showAll || sorted.size <= 5) sorted else sorted.take(5)
            visible.forEach { note ->
                Clear30Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        val entry = remember(note.fromMemberID) {
                            org.clear30.data.UserDirectory.lookup(note.fromMemberID)
                        }
                        TinyText(entry.displayName, color = Clear30Colors.text.copy(alpha = 0.5f))
                        SmallText(note.message)
                    }
                }
            }
            if (!showAll && sorted.size > 5) {
                TinyText(
                    "Show ${sorted.size - 5} more",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAll = true }
                        .padding(vertical = 6.dp),
                    color = Clear30Colors.green,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            OutlinedTextField(draft, { draft = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Share a note…") })
            DefaultButton("Post", gradient = Clear30Gradients.clear30) {
                if (draft.isNotBlank()) { onPost(draft); draft = "" }
            }
        }
    }
}

/** The four group tabs (iOS GroupAll pill picker). */
private enum class GroupSection(val title: String, val symbol: String) {
    MEMBERS("Members", "person.2.fill"),
    CHAT("Chat", "message.fill"),
    NOTES("Notes", "note.text"),
    SETTINGS("Settings", "gearshape.fill"),
}

/** Header card: group name (dim) + the current section title + invite chip. */
@Composable
private fun GroupHeader(group: Clear30Group, userInfo: UserInfo, section: GroupSection) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = group.gradient) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Column {
                SmallText(group.name ?: "Your Group", color = Color.White.copy(alpha = 0.5f))
                Heading2(section.title, color = Color.White)
                TinyText("${group.members.size} members · ${group.daysSober} days clear together", color = Color.White.copy(alpha = 0.75f))
            }
            // Invite chip — tapping fires the system share sheet with a
            // clear30:// deep link the recipient can tap on Android to
            // auto-join via the GroupsTab sub-route handler.
            InviteChip(group = group) {
                shareGroupInvite(context, group)
                org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.sharedGroupCode)
            }
        }
    }
}

/** Pill-picker for the four group tabs. */
@Composable
private fun GroupSectionPicker(current: GroupSection, gradient: androidx.compose.ui.graphics.Brush, onSelect: (GroupSection) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        GroupSection.entries.forEach { s ->
            val selected = s == current
            Column(
                Modifier.weight(1f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(99.dp))
                    .background(if (selected) gradient else Clear30Gradients.gray)
                    .clickable { org.clear30.views.theme.Haptics.lightImpact(); onSelect(s) }
                    .padding(vertical = Dimens.cardSpacing / 1.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                androidx.compose.material3.Icon(
                    org.clear30.views.components.sfSymbol(s.symbol),
                    contentDescription = s.title,
                    tint = if (selected) Color.White else Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
                org.clear30.views.components.MiniText(s.title, color = if (selected) Color.White else Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

/** A full-bleed benefit slide for the not-in-a-group carousel (iOS GroupCreation). */
@Composable
private fun GroupBenefitSlide(imageRes: Int, title: String, body: String) {
    // iOS AssessmentCarouselCardView: default (white) card, centered fit
    // illustration, then a leading Heading3 + SmallText pinned to the bottom.
    Column(
        Modifier.fillMaxSize().cardStyle(),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.weight(1f))
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().weight(4f),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
        Spacer(Modifier.weight(1f))
        org.clear30.views.components.Heading3(title)
        SmallText(body)
    }
}

/**
 * Members tab — month swiper + month stats + calendar, inner circle, leaderboard
 * roster, recent activity (iOS GroupMembers.swift).
 */
@Composable
private fun MembersTab(
    group: Clear30Group,
    userInfo: UserInfo,
    controller: GroupController,
    onMemberTap: (Clear30GroupMember) -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Months between the earliest member join month and now (iOS GroupMembers.init).
    val months = remember(group.members) { monthsForGroup(group) }
    var monthIndex by remember(months.size) { androidx.compose.runtime.mutableIntStateOf(months.lastIndex) }
    val currentMonth = months[monthIndex.coerceIn(0, months.lastIndex)]

    var noteTarget by remember { mutableStateOf<Clear30GroupMember?>(null) }
    var showInnerCirclePicker by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val ping: (Clear30GroupMember) -> Unit = { m ->
        scope.launch {
            actionError = org.clear30.data.supabase.SupabaseController
                .sendGroupPing(group.id, m.memberID, userInfo.userID)?.message
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        // Month swiper (iOS GroupMonthPicker) — chevrons + swipeable month label.
        GroupMonthPicker(months, monthIndex) { monthIndex = it }

        // Month stats (iOS GroupMembers.swift:111-118).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            MonthStatCard("${group.monthCheckIns(currentMonth, sober = false)}", "Smoked Check Ins", group.gradient, Modifier.weight(1f))
            MonthStatCard("${group.monthCheckIns(currentMonth, sober = true)}", "Sober Check Ins", group.gradient, Modifier.weight(1f))
        }

        Clear30Card(modifier = Modifier.fillMaxWidth()) { GroupCalendar(group, userInfo, month = currentMonth) }
        InnerCircleSection(
            group, userInfo,
            onEdit = { showInnerCirclePicker = true },
            onNote = { noteTarget = it },
            onPing = ping,
        )
        GroupLeaderboard(group, userInfo, onMemberTap = onMemberTap, onNote = { noteTarget = it }, onPing = ping)
        GroupActivitySection(group)
        actionError?.let { msg -> SmallText(msg, color = Clear30Colors.red2.copy(alpha = 0.75f)) }
        Spacer(Modifier.size(Dimens.cardSpacing))
    }

    noteTarget?.let { member ->
        SendNoteDialog(
            member = member,
            onDismiss = { noteTarget = null },
            onSend = { msg ->
                noteTarget = null
                scope.launch { actionError = controller.addNote(msg, member.memberID) }
            },
        )
    }
    if (showInnerCirclePicker) {
        InnerCirclePickerDialog(
            group = group,
            userInfo = userInfo,
            onDismiss = { showInnerCirclePicker = false },
            onSave = { ids ->
                showInnerCirclePicker = false
                scope.launch {
                    actionError = org.clear30.data.supabase.SupabaseController
                        .updateGroupSubscriptions(group.id, userInfo.userID, ids)?.message
                    controller.refresh()
                }
            },
        )
    }
}

/** First-of-month dates from the earliest member join month through today. */
private fun monthsForGroup(group: Clear30Group): List<org.clear30.data.model.PlainDate> {
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val today = kotlinx.datetime.Clock.System.todayIn(tz)
    val start = group.members.mapNotNull { it.joinDate }.minOrNull()
        ?.toLocalDateTime(tz)?.date ?: today
    val months = mutableListOf<org.clear30.data.model.PlainDate>()
    var y = start.year
    var m = start.monthNumber
    while (y < today.year || (y == today.year && m <= today.monthNumber)) {
        months.add(org.clear30.data.model.PlainDate(y, m, 1))
        m++
        if (m > 12) { m = 1; y++ }
    }
    if (months.isEmpty()) months.add(org.clear30.data.model.PlainDate(today.year, today.monthNumber, 1))
    return months
}

/** Group-wide sober/smoked check-in count within a month (iOS `daysSober(month:)` / `daysSmoked(month:)`). */
private fun Clear30Group.monthCheckIns(month: org.clear30.data.model.PlainDate, sober: Boolean): Int {
    val prefix = "%04d-%02d-".format(month.year, month.month)
    return members.sumOf { m ->
        m.dayInfo?.count { it.key.startsWith(prefix) && it.value.sober == sober } ?: 0
    }
}

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** "🎃 October" — iOS `Date.monthWithEmoji` (CalendarUtils.swift). */
private fun monthWithEmoji(month: org.clear30.data.model.PlainDate): String {
    val emoji = when (month.month) {
        1 -> "❄️"; 2 -> "💘"; 3 -> "🍀"; 4 -> "🌷"; 5 -> "🌸"; 6 -> "☀️"
        7 -> "🎆"; 8 -> "🏖️"; 9 -> "🍎"; 10 -> "🎃"; 11 -> "🦃"; 12 -> "🎄"
        else -> "📅"
    }
    return "$emoji ${MONTH_NAMES[month.month - 1]}"
}

/**
 * Month swiper (iOS GroupMonthPicker, GroupMembers.swift:244-294): left/right
 * chevrons around a swipeable month label; chevrons vanish at the ends.
 */
@Composable
private fun GroupMonthPicker(months: List<org.clear30.data.model.PlainDate>, index: Int, onIndexChange: (Int) -> Unit) {
    val pagerState = rememberPagerState(initialPage = index, pageCount = { months.size })
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != index) onIndexChange(pagerState.currentPage)
    }
    LaunchedEffect(index) {
        if (pagerState.currentPage != index) pagerState.animateScrollToPage(index)
    }
    Row(
        Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        if (months.size > 1) {
            if (index > 0) {
                org.clear30.views.components.IconButton("chevron.left", height = 12.dp, padding = 0.dp, tint = Clear30Colors.text.copy(alpha = 0.5f)) {
                    onIndexChange(index - 1)
                }
            } else {
                Spacer(Modifier.size(12.dp)) // keep the label from shifting (iOS uses opacity 0)
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            org.clear30.views.components.Heading3(monthWithEmoji(months[page]), Modifier.fillMaxWidth())
        }
        if (months.size > 1) {
            if (index < months.lastIndex) {
                org.clear30.views.components.IconButton("chevron.right", height = 12.dp, haptic = false, padding = 0.dp, tint = Clear30Colors.text.copy(alpha = 0.5f)) {
                    onIndexChange(index + 1)
                }
            } else {
                Spacer(Modifier.size(12.dp))
            }
        }
    }
}

/** One of the two month stat cards (iOS EmojiTextCard on the group gradient). */
@Composable
private fun MonthStatCard(title: String, subtitle: String, gradient: androidx.compose.ui.graphics.Brush, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier, gradient = gradient) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            org.clear30.views.components.Heading3(title, color = Color.White)
            SmallText(subtitle, color = Color.White, maxLines = 1)
        }
    }
}

/**
 * Inner Circle (iOS GroupMembers.swift:120-155): the members whose check-ins the
 * user subscribed to, with an add/edit picker. Hidden while the user is alone in
 * the group (iOS shows the invite button instead — the header's invite chip
 * covers that here).
 */
@Composable
private fun InnerCircleSection(
    group: Clear30Group,
    userInfo: UserInfo,
    onEdit: () -> Unit,
    onNote: (Clear30GroupMember) -> Unit,
    onPing: (Clear30GroupMember) -> Unit,
) {
    if (group.members.none { it.memberID != userInfo.userID }) return
    val subscribedIds = group.subscribed.orEmpty().map { it.subscribedTo }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SmallText("Inner Circle", color = Clear30Colors.text.copy(alpha = 0.5f))
            Spacer(Modifier.weight(1f))
            if (subscribedIds.isNotEmpty()) {
                org.clear30.views.components.IconButton("person.fill.badge.plus", height = 15.dp, padding = 0.dp, tint = Clear30Colors.text.copy(alpha = 0.5f), onClick = onEdit)
            }
        }
        if (subscribedIds.isEmpty()) {
            // iOS TextIconButton("Add to Inner Circle", person.fill.badge.plus).
            Clear30Card(modifier = Modifier.fillMaxWidth().pressScale { onEdit() }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    androidx.compose.material3.Icon(
                        org.clear30.views.components.sfSymbol("person.fill.badge.plus"),
                        contentDescription = null,
                        tint = Clear30Colors.text.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                    SmallText("Add to Inner Circle", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
        } else {
            group.members
                .filter { subscribedIds.contains(it.memberID) }
                .sortedByDescending { it.daysSoberCount }
                .forEach { m ->
                    Clear30Card(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                            SmallText(m.emoji)
                            SmallText(m.name)
                            Spacer(Modifier.weight(1f))
                            TinyText("${m.daysSoberCount} days clear", color = Clear30Colors.text.copy(alpha = 0.5f))
                            if (m.memberID != userInfo.userID) {
                                org.clear30.views.components.IconButton("message.fill", height = 15.dp, padding = Dimens.cardSpacing / 3, tint = Clear30Colors.text.copy(alpha = 0.5f)) { onNote(m) }
                                org.clear30.views.components.IconButton("bell.fill", height = 15.dp, padding = Dimens.cardSpacing / 3, tint = Clear30Colors.text.copy(alpha = 0.5f)) { onPing(m) }
                            }
                        }
                    }
                }
        }
    }
}

/** Compose-a-note dialog backing the leaderboard/inner-circle message action. */
@Composable
private fun SendNoteDialog(member: Clear30GroupMember, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send a note to ${member.name}") },
        text = {
            OutlinedTextField(draft, { draft = it }, placeholder = { Text("Something encouraging…") })
        },
        confirmButton = {
            TextButton(onClick = { if (draft.isNotBlank()) onSend(draft.trim()) }, enabled = draft.isNotBlank()) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Multi-select picker over the other group members (iOS GroupInnerCirclePicker). */
@Composable
private fun InnerCirclePickerDialog(
    group: Clear30Group,
    userInfo: UserInfo,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val selected = remember {
        androidx.compose.runtime.mutableStateListOf(*group.subscribed.orEmpty().map { it.subscribedTo }.toTypedArray())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inner Circle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Text("Get notified when these members check in.")
                group.members.filter { it.memberID != userInfo.userID }.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (selected.contains(m.memberID)) selected.remove(m.memberID) else selected.add(m.memberID)
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = selected.contains(m.memberID),
                            onCheckedChange = { on -> if (on) selected.add(m.memberID) else selected.remove(m.memberID) },
                        )
                        Text("${m.emoji} ${m.name}".trim())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Notes tab — the group note inbox + composer. */
@Composable
private fun NotesTab(group: Clear30Group, onPost: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        GroupNotesSection(notes = group.notes.orEmpty(), onPost = onPost)
        Spacer(Modifier.size(Dimens.cardSpacing))
    }
}

/** Settings tab — edit name + color, remove members, leave (iOS GroupSettings). */
@Composable
private fun GroupSettingsTab(
    group: Clear30Group,
    userInfo: UserInfo,
    controller: GroupController,
    scope: kotlinx.coroutines.CoroutineScope,
    onLeave: () -> Unit,
) {
    var name by remember(group.id) { mutableStateOf(group.name ?: "") }
    var hue by remember(group.id) { androidx.compose.runtime.mutableFloatStateOf(group.hue.toFloat()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        // iOS GroupSettings.swift:58-64: a settings card with a borderless,
        // right-aligned Lexend 19 field at half opacity that saves on focus loss.
        GroupNameCard(
            name = name,
            onChange = { name = it },
            onCommit = {
                val trimmed = name.trim()
                if (trimmed.isNotEmpty() && trimmed != (group.name ?: "")) {
                    scope.launch { controller.rename(trimmed, hue.toDouble()) }
                }
            },
        )
        SmallText("Group color", color = Clear30Colors.text.copy(alpha = 0.5f))
        androidx.compose.material3.Slider(
            value = hue,
            onValueChange = { hue = it },
            valueRange = 0f..1f,
            onValueChangeFinished = { scope.launch { controller.rename(name, hue.toDouble()) } },
        )
        SmallText("Members", color = Clear30Colors.text.copy(alpha = 0.5f))
        group.members.forEach { m ->
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    SmallText(m.emoji)
                    SmallText(m.name)
                    Spacer(Modifier.weight(1f))
                    if (m.memberID != userInfo.userID) {
                        androidx.compose.material3.Icon(
                            org.clear30.views.components.sfSymbol("minus.circle.fill"),
                            contentDescription = "Remove",
                            tint = Clear30Colors.red2.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp).clickable { scope.launch { controller.removeMember(m.memberID) } },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(Dimens.cardSpacing / 2))
        DefaultButton("Leave group", gradient = Clear30Gradients.red, modifier = Modifier.fillMaxWidth()) { onLeave() }
        Spacer(Modifier.size(Dimens.cardSpacing))
    }
}

/**
 * "Group name" settings card (iOS SettingsCard + trailing TextField,
 * GroupSettings.swift:58-64): label on the left, borderless right-aligned
 * Lexend field at half opacity; commits when focus leaves the field.
 */
@Composable
private fun GroupNameCard(name: String, onChange: (String) -> Unit, onCommit: () -> Unit) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            org.clear30.views.components.DefaultText("Group name")
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = onChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (hadFocus && !state.isFocused) onCommit()
                        hadFocus = state.isFocused
                    },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = org.clear30.views.theme.Lexend,
                    fontSize = 19.sp,
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { focusManager.clearFocus() },
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Clear30Colors.text),
            )
        }
    }
}

@Composable
private fun InviteChip(group: Clear30Group, onShare: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.25f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Icon(
            org.clear30.views.components.sfSymbol("square.and.arrow.up"),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            TinyText("Invite link", color = Color.White.copy(alpha = 0.75f))
            SmallText("clear30.org/group/${group.id}", color = Color.White)
        }
        DefaultButton("Share", gradient = Clear30Gradients.button) { onShare() }
    }
}

private fun shareGroupInvite(context: android.content.Context, group: Clear30Group) {
    val link = "https://clear30.org/group/${group.id}"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            android.content.Intent.EXTRA_TEXT,
            "Join my Clear30 group: ${group.name ?: "Group"}\n$link",
        )
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Invite a friend").apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

@Composable
private fun MemberRow(member: Clear30GroupMember) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            SmallText(member.emoji)
            SmallText(member.name)
            Spacer(Modifier.weight(1f))
            TinyText("${member.daysCheckedIn} days", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun JoinGroupDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                Text("Paste the group code from a friend.")
                OutlinedTextField(code, { code = it }, label = { Text("Group code") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(code) },
                enabled = !busy && code.isNotBlank(),
            ) { Text(if (busy) "Joining…" else "Join") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun LeaveGroupDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Step away from the group?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You'll lose access to the group's calendar, notes, and member updates.")
                Text("Your own check-ins stay with you. You can rejoin anytime with the same code.")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Leaving…" else "Yes, leave", color = Clear30Colors.red2)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Stay") } },
    )
}

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.clear30.Clear30Application
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import org.clear30.data.GroupController
import org.clear30.data.model.CheckInDefaults
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.Clear30GroupMember
import org.clear30.data.model.Clear30GroupNote
import org.clear30.data.model.GROUP_BRIGHTNESS
import org.clear30.data.model.GROUP_SATURATION
import org.clear30.data.model.PlainDate
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.sendGroupPing
import org.clear30.data.supabase.updateGroupSubscriptions
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.DefaultText
import org.clear30.views.components.GradientActionButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.HuePicker
import org.clear30.views.components.MultiLineInput
import org.clear30.views.components.PillPicker
import org.clear30.views.components.PillPickerStyle
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.TinyText
import org.clear30.views.existinguser.profile.achievements.GradientIcon
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * GroupsTab — 1:1 port of the iOS Group views (GroupTab/GroupAll/GroupCreation).
 * Shows the user's accountability group (header + pill picker + the selected
 * section) via [GroupController], or the creation carousel when they aren't in
 * one. Joining happens exclusively through the invite deep link, like iOS.
 */
@Composable
fun GroupsTab(program: org.clear30.data.model.Program, userInfo: UserInfo) {
    val controller = remember { GroupController(userInfo, program) }
    val group by controller.group.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var creatingGroup by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { controller.refresh() }

    // Deep link → auto-join from clear30://group/<code> (iOS invite link flow).
    // We only fire when the user isn't already in a group; otherwise the link
    // is a no-op and we drop it. Errors surface through the regular `error` field.
    val sub by org.clear30.AppState.pendingSubRoute.collectAsStateWithLifecycle()
    LaunchedEffect(sub) {
        val r = sub as? org.clear30.data.DeepLinkRoute.Group ?: return@LaunchedEffect
        org.clear30.AppState.requestSubRoute(null)
        if (group != null) return@LaunchedEffect
        // Run the join on the app scope: clearing pendingSubRoute above re-keys
        // this LaunchedEffect, which would otherwise cancel the in-flight join RPC
        // mid-flight (surfacing "The coroutine scope left the composition").
        org.clear30.Clear30Application.appScope.launch {
            busy = true
            error = controller.join(r.code)
            busy = false
        }
    }

    val g = group
    if (g == null) {
        // Not in a group — 1:1 of iOS GroupCreation: heading, the swipeable
        // benefit carousel (image cards, fixed 450dp, no dots), and the single
        // create CTA. Creation is INSTANT — no name prompt; the group is
        // auto-named "<name>'s Group" with the default hue, like iOS createGroup().
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
            Heading1("Groups")
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
            Spacer(Modifier.size(Dimens.cardSpacing / 2))
            PillPicker(
                selection = section,
                items = GroupSection.entries.toList(),
                label = { it.title },
                systemImage = { it.symbol },
                onChanged = { section = it },
                style = PillPickerStyle.Primary(g.gradient),
                expanded = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(Dimens.cardSpacing))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (section) {
                    GroupSection.MEMBERS -> MembersTab(g, userInfo, controller)
                    GroupSection.CHAT -> GroupChat(g, userInfo, controller)
                    GroupSection.NOTES -> NotesTab(g)
                    GroupSection.SETTINGS -> GroupSettingsTab(g, userInfo, controller, scope) { showLeaveConfirm = true }
                }
            }
            error?.let { msg -> SmallText(msg, color = Clear30Colors.red2.copy(alpha = 0.75f)) }
        }
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

/** The four group tabs (iOS GroupViewType). */
private enum class GroupSection(val title: String, val symbol: String) {
    MEMBERS("Members", "person.2.fill"),
    CHAT("Chat", "message.fill"),
    NOTES("Notes", "note.text"),
    SETTINGS("Settings", "gearshape.fill"),
}

/**
 * Plain header (iOS GroupAll.swift:59-74): dim group name over the current
 * section title as a Heading1, with a top-right "+" circle button that fires
 * the invite share sheet (a link the recipient taps to auto-join via the
 * GroupsTab sub-route handler).
 */
@Composable
private fun GroupHeader(group: Clear30Group, userInfo: UserInfo, section: GroupSection) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            SmallText(group.name ?: "Your Group", color = Clear30Colors.text.copy(alpha = 0.5f))
            Heading1(section.title)
        }
        CircleIconButton("plus") {
            shareGroupInvite(context, group)
            org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.sharedGroupCode)
        }
    }
}

/** Circle icon button (iOS `CircleIconButton`): 37.5 circle, 18 icon, opacity-gray fill. */
@Composable
private fun CircleIconButton(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(37.5.dp)
            .clip(CircleShape)
            .background(Clear30Colors.opacityGray)
            .pressScale { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            org.clear30.views.components.sfSymbol(icon),
            contentDescription = icon,
            tint = Clear30Colors.text,
            modifier = Modifier.size(18.dp),
        )
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
        Heading3(title)
        SmallText(body)
    }
}

// MARK: - Members tab (iOS GroupMembers.swift)

/**
 * Members tab — month swiper → month stats → Inner Circle → other members
 * (iOS GroupMembers.swift body order :63-80). Member cards carry the badge
 * actions (note / ping); card bodies themselves are not tappable, like iOS.
 */
@Composable
private fun MembersTab(
    group: Clear30Group,
    userInfo: UserInfo,
    controller: GroupController,
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Months between the earliest member join month and now (iOS GroupMembers.init).
    val months = remember(group.members) { monthsForGroup(group) }
    var monthIndex by remember(months.size) { androidx.compose.runtime.mutableIntStateOf(months.lastIndex) }
    val currentMonth = months[monthIndex.coerceIn(0, months.lastIndex)]
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val today = kotlinx.datetime.Clock.System.todayIn(tz)
    val thisMonth = currentMonth.year == today.year && currentMonth.month == today.monthNumber

    var noteTarget by remember { mutableStateOf<Clear30GroupMember?>(null) }
    var pingTarget by remember { mutableStateOf<Clear30GroupMember?>(null) }
    var showInnerCirclePicker by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    // iOS subscribedStrings: subscription ids that still resolve to a member.
    val subscribedIds = group.subscribed.orEmpty().map { it.subscribedTo }
        .filter { id -> group.members.any { it.memberID == id } }
    val innerCircleMembers = group.members
        .filter { subscribedIds.contains(it.memberID) }
        .sortedByDescending { it.daysClearIn(currentMonth) }
    val otherMembers = group.members
        .filter { !subscribedIds.contains(it.memberID) }
        .sortedByDescending { it.daysClearIn(currentMonth) }
    val daysClearMax = currentMonth.adding(months = 1, days = -1).day

    @Composable
    fun memberCard(m: Clear30GroupMember) {
        val isSelf = m.memberID == userInfo.userID
        GroupMemberCard(
            member = m,
            state = if (thisMonth) m.stateForToday() else GroupMemberState.NONE,
            streak = if (thisMonth) m.streakCount() else null,
            daysClear = m.daysClearIn(currentMonth),
            daysClearMax = daysClearMax,
            onNote = if (isSelf) null else ({ noteTarget = m }),
            onPing = if (isSelf) null else ({ pingTarget = m }),
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Month swiper (iOS GroupMonthPicker) — chevrons + swipeable month label.
        GroupMonthPicker(months, monthIndex) { monthIndex = it }
        Spacer(Modifier.height(Dimens.cardSpacing))

        // Month stats (iOS GroupMembers.swift:111-118) — two EmojiTextCards on
        // the group gradient.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            MonthStatCard("${group.monthCheckIns(currentMonth, sober = false)}", "Smoked Check Ins", group.gradient, Modifier.weight(1f))
            MonthStatCard("${group.monthCheckIns(currentMonth, sober = true)}", "Sober Check Ins", group.gradient, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Dimens.cardSpacing * 2))

        // Inner Circle (iOS GroupMembers.swift:120-155). When the user is alone
        // in the group iOS swaps the whole section for an "Invite to Group" button.
        if (group.members.any { it.memberID != userInfo.userID }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SmallText("Inner Circle", color = Clear30Colors.text.copy(alpha = 0.5f))
                Spacer(Modifier.weight(1f))
                if (subscribedIds.isNotEmpty()) {
                    org.clear30.views.components.IconButton("person.fill.badge.plus", height = 15.dp, padding = 0.dp, tint = Clear30Colors.text.copy(alpha = 0.5f)) {
                        showInnerCirclePicker = true
                    }
                }
            }
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            if (subscribedIds.isEmpty()) {
                GroupTextIconButton("Add to Inner Circle", "person.fill.badge.plus") { showInnerCirclePicker = true }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                    innerCircleMembers.forEach { m -> memberCard(m) }
                }
            }
        } else {
            GroupTextIconButton("Invite to Group", "person.fill.badge.plus", gradient = group.gradient) {
                shareGroupInvite(context, group)
                org.clear30.data.Logger.logEvent(userInfo.loggingID, org.clear30.data.LogEventType.sharedGroupCode)
            }
        }
        Spacer(Modifier.height(Dimens.cardSpacing * 2))

        // Other members (iOS GroupMembers.swift:158-172).
        SmallText(
            if (innerCircleMembers.isEmpty()) "Members" else "Other members",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(Dimens.cardSpacing / 2))
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            otherMembers.forEach { m -> memberCard(m) }
        }

        actionError?.let { msg ->
            SmallText(msg, Modifier.padding(top = Dimens.cardSpacing), color = Clear30Colors.red2.copy(alpha = 0.75f))
        }
        Spacer(Modifier.height(Dimens.cardSpacing))
    }

    noteTarget?.let { member ->
        SendNotePopup(
            group = group,
            member = member,
            onDismiss = { noteTarget = null },
            onSend = { msg ->
                scope.launch { actionError = controller.addNote(msg, member.memberID) }
            },
        )
    }
    pingTarget?.let { member ->
        PingConfirmDialog(
            member = member,
            onDismiss = { pingTarget = null },
            onConfirm = {
                scope.launch {
                    actionError = SupabaseController
                        .sendGroupPing(group.id, member.memberID, userInfo.userID)
                        ?.let { "Could not send ping. ${it.message}".trim() }
                }
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
                    actionError = SupabaseController
                        .updateGroupSubscriptions(group.id, userInfo.userID, ids)?.message
                    controller.refresh()
                }
            },
        )
    }
}

/**
 * GroupMemberCard (iOS GroupCards.swift:10-57): emoji avatar + name +
 * right-aligned "X/Y days clear", with the badge row below. Badge taps carry
 * the note / ping actions; pass null for the current user's own card.
 */
@Composable
private fun GroupMemberCard(
    member: Clear30GroupMember,
    state: GroupMemberState,
    streak: Int?,
    daysClear: Int,
    daysClearMax: Int,
    onNote: (() -> Unit)?,
    onPing: (() -> Unit)?,
) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                GroupMemberEmoji(member.emoji)
                SmallText(member.name, Modifier.weight(1f))
                TinyText("$daysClear/$daysClearMax days clear", color = Clear30Colors.text.copy(alpha = 0.5f))
            }

            // Badges (iOS GroupMemberCardBadges, GroupCards.swift:59-145).
            val showStreak = streak != null && streak > 1
            if (showStreak || state != GroupMemberState.NONE) {
                Row(
                    Modifier.padding(top = Dimens.cardSpacing / 2),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    if (showStreak) {
                        MemberBadge("🔥", "$streak", gradient = Clear30Gradients.clear30)
                    }
                    val complete = CheckInDefaults.weed.completeOption
                    val incomplete = CheckInDefaults.weed.incompleteOption
                    when (state) {
                        GroupMemberState.SOBER ->
                            MemberBadge(complete.emoji, "${complete.name}!", gradient = Clear30Gradients.clear30, onClick = onNote)
                        GroupMemberState.SMOKED ->
                            MemberBadge(incomplete.emoji, "${incomplete.name}.", onClick = onNote)
                        GroupMemberState.NOT_CHECK_IN ->
                            MemberBadge("🕑", "Didn't check in.", gradient = Clear30Gradients.red, onClick = onPing)
                        GroupMemberState.NONE -> {}
                    }
                }
            }
        }
    }
}

/**
 * One badge (iOS GroupMemberCardBadge → TinyTextButton with a 12-radius card,
 * no shadow). Non-null [onClick] makes it tappable; null renders it inert
 * (the current user's own card).
 */
@Composable
private fun MemberBadge(
    emoji: String,
    text: String,
    gradient: Brush? = null,
    onClick: (() -> Unit)? = null,
) {
    val tappable = if (onClick != null) Modifier.pressScale { onClick() } else Modifier
    TinyText(
        "$emoji $text",
        modifier = tappable
            .cardStyle(
                color = Clear30Colors.opacityGray,
                shadowColor = Color.Transparent,
                cornerRadius = 12.dp,
                gradient = gradient,
                padding = false,
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (gradient != null) Color.White else Clear30Colors.text,
        maxLines = 1,
    )
}

/** Member emoji avatar (iOS GroupMemberEmoji): 35dp opacity-gray circle + emoji. */
@Composable
internal fun GroupMemberEmoji(emoji: String) {
    Box(
        Modifier.size(35.dp).clip(CircleShape).background(Clear30Colors.opacityGray),
        contentAlignment = Alignment.Center,
    ) {
        DefaultText(emoji)
    }
}

/**
 * TextIconButton (iOS Buttons.swift) — full-width card button with trailing
 * icon, content centered (iOS puts a Spacer on both sides so the CardStyle
 * fill stretches). Gradient fill = white content; [foreground] overrides
 * (e.g. the red Leave button); otherwise text at half opacity.
 */
@Composable
private fun GroupTextIconButton(
    text: String,
    icon: String,
    gradient: Brush? = null,
    foreground: Color? = null,
    onClick: () -> Unit,
) {
    val fg = foreground ?: if (gradient != null) Color.White else Clear30Colors.text.copy(alpha = 0.5f)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale { onClick() }
            .cardStyle(gradient = gradient),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
    ) {
        DefaultText(text, color = fg)
        androidx.compose.material3.Icon(
            org.clear30.views.components.sfSymbol(icon),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
    }
}

// MARK: - Member day-info helpers (iOS Clear30GroupMember extensions)

/** iOS GroupMemberState. */
private enum class GroupMemberState { SOBER, SMOKED, NOT_CHECK_IN, NONE }

private fun dayKey(date: LocalDate): String = "%04d-%02d-%02d".format(date.year, date.monthNumber, date.dayOfMonth)

/** Day info from the member's join date onward (iOS `filteredDayInfo`). */
private fun Clear30GroupMember.filteredDayInfo(): Map<String, ProgramDayInfo> {
    val info = dayInfo ?: return emptyMap()
    val joinKey = joinDate?.toLocalDateTime(TimeZone.currentSystemDefault())?.date?.let { dayKey(it) }
        ?: return info
    return info.filterKeys { it >= joinKey }
}

/** iOS `getState(for: Date())` — the member's check-in state for today. */
private fun Clear30GroupMember.stateForToday(): GroupMemberState {
    val today = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())
    return when (filteredDayInfo()[dayKey(today)]?.sober) {
        true -> GroupMemberState.SOBER
        false -> GroupMemberState.SMOKED
        null -> GroupMemberState.NOT_CHECK_IN // today is always "today" here
    }
}

/** Consecutive sober days ending today (iOS `streak`). */
private fun Clear30GroupMember.streakCount(): Int {
    val info = filteredDayInfo()
    var day = kotlinx.datetime.Clock.System.todayIn(TimeZone.currentSystemDefault())
    if (info[dayKey(day)]?.sober != true) return 0
    var count = 0
    while (info[dayKey(day)]?.sober == true) {
        count++
        day = day.minus(DatePeriod(days = 1))
    }
    return count
}

/** Sober days within [month] (iOS `daysSoberCountFiltered(start:end:)`). */
private fun Clear30GroupMember.daysClearIn(month: PlainDate): Int {
    val prefix = "%04d-%02d-".format(month.year, month.month)
    return filteredDayInfo().count { it.key.startsWith(prefix) && it.value.sober == true }
}

/** First-of-month dates from the earliest member join month through today. */
private fun monthsForGroup(group: Clear30Group): List<PlainDate> {
    val tz = TimeZone.currentSystemDefault()
    val today = kotlinx.datetime.Clock.System.todayIn(tz)
    val start = group.members.mapNotNull { it.joinDate }.minOrNull()
        ?.toLocalDateTime(tz)?.date ?: today
    val months = mutableListOf<PlainDate>()
    var y = start.year
    var m = start.monthNumber
    while (y < today.year || (y == today.year && m <= today.monthNumber)) {
        months.add(PlainDate(y, m, 1))
        m++
        if (m > 12) { m = 1; y++ }
    }
    if (months.isEmpty()) months.add(PlainDate(today.year, today.monthNumber, 1))
    return months
}

/** Group-wide sober/smoked check-in count within a month (iOS `daysSober(month:)` / `daysSmoked(month:)`). */
private fun Clear30Group.monthCheckIns(month: PlainDate, sober: Boolean): Int {
    val prefix = "%04d-%02d-".format(month.year, month.month)
    return members.sumOf { m ->
        m.filteredDayInfo().count { it.key.startsWith(prefix) && it.value.sober == sober }
    }
}

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** "🎃 October" — iOS `Date.monthWithEmoji` (CalendarUtils.swift). */
private fun monthWithEmoji(month: PlainDate): String {
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
private fun GroupMonthPicker(months: List<PlainDate>, index: Int, onIndexChange: (Int) -> Unit) {
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
            Heading3(monthWithEmoji(months[page]), Modifier.fillMaxWidth())
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
private fun MonthStatCard(title: String, subtitle: String, gradient: Brush, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier, gradient = gradient) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Heading3(title, color = Color.White)
            SmallText(subtitle, color = Color.White, maxLines = 1)
        }
    }
}

// MARK: - Send note / ping (iOS GroupSendNote.swift + GroupController.sendPing)

/**
 * Send-note popup (iOS GroupSendMessage): a card with "Send a note to" + the
 * gradient note icon, the recipient, a bordered multiline input, and
 * Cancel / Send buttons (Send on the group gradient).
 */
@Composable
internal fun SendNotePopup(
    group: Clear30Group,
    member: Clear30GroupMember,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().cardStyle()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                SmallText("Send a note to", color = Clear30Colors.text.copy(alpha = 0.5f), maxLines = 1)
                Spacer(Modifier.weight(1f))
                GradientIcon("note.text", group.gradient, 20.dp)
            }
            Spacer(Modifier.height(4.dp))
            DefaultText("${member.emoji} ${member.name}".trim())
            Spacer(Modifier.height(10.dp))
            MultiLineInput(note, { note = it }, placeholder = "Note", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                StretchedButton("Cancel", weight = FontWeight.Normal, modifier = Modifier.weight(1f)) { onDismiss() }
                StretchedButton("Send", gradient = group.gradient, weight = FontWeight.Normal, modifier = Modifier.weight(1f)) {
                    if (note.isNotBlank()) {
                        onSend(note.trim())
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** Yes/no ping confirm (iOS GroupController.sendPing alert). */
@Composable
private fun PingConfirmDialog(member: Clear30GroupMember, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val label = "${member.emoji} ${member.name}".trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ping $label") },
        text = { Text("Do you want to ping $label to remind them to check in?") },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Yes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("No") } },
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
                Text("Add to inner circle to pin members and see their status in your \"Today\" feed.")
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
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// MARK: - Notes tab (iOS GroupNoteInbox.swift) — READ-ONLY inbox

/**
 * Notes tab — the read-only inbox of notes other members sent the user.
 * Sending happens from the member-card badges via [SendNotePopup], never here.
 */
@Composable
private fun NotesTab(group: Clear30Group) {
    val pairs = remember(group.notes, group.members) {
        group.notes.orEmpty()
            .mapNotNull { note ->
                group.members.firstOrNull { it.memberID == note.fromMemberID }?.let { note to it }
            }
            .sortedByDescending { it.first.timestamp ?: Instant.DISTANT_PAST }
    }
    if (pairs.isEmpty()) {
        // iOS GroupNoteInbox empty state (GroupNoteInbox.swift:33-48).
        Column(
            Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Heading3("Group Notes", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(Dimens.cardSpacing / 2))
            SmallText(
                "When members send you notes, they will appear here.",
                color = Clear30Colors.text.copy(alpha = 0.5f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            pairs.forEach { (note, from) -> NoteCard(group.gradient, note, from) }
            Spacer(Modifier.height(Dimens.cardSpacing))
        }
    }
}

/** NoteCard (iOS GroupNoteInbox.swift:55-94): gradient note icon + timestamp, message, sender. */
@Composable
private fun NoteCard(gradient: Brush, note: Clear30GroupNote, from: Clear30GroupMember) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                GradientIcon("note.text", gradient, 20.dp)
                Spacer(Modifier.weight(1f))
                note.timestamp?.let {
                    TinyText(groupNoteDateString(it), color = Clear30Colors.text.copy(alpha = 0.5f), maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            SmallText(note.message)
            Spacer(Modifier.height(3.dp))
            SmallText("from ${from.emoji} ${from.name}".trim(), color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

// MARK: - Settings tab (iOS GroupSettings.swift)

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

    // Debounced hue persistence (iOS scheduleHueUpdate: 1s after the last change).
    LaunchedEffect(hue) {
        if (kotlin.math.abs(hue - group.hue.toFloat()) > 0.0001f) {
            kotlinx.coroutines.delay(1_000)
            controller.rename(name.trim().ifEmpty { group.name ?: "" }, hue.toDouble())
        }
    }

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

        // iOS GroupSettings.swift:68-84: "Group color" card — label + live circle
        // swatch on top, the hue-gradient slider underneath.
        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DefaultText("Group color")
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(30.dp).clip(CircleShape)
                            .background(Color.hsv(hue.coerceIn(0f, 1f) * 360f, GROUP_SATURATION.toFloat(), GROUP_BRIGHTNESS.toFloat())),
                    )
                }
                HuePicker(
                    hue = hue,
                    onHueChange = { hue = it },
                    saturation = GROUP_SATURATION.toFloat(),
                    brightness = GROUP_BRIGHTNESS.toFloat(),
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }

        Spacer(Modifier.height(Dimens.cardSpacing / 2))
        SmallText("Members", color = Clear30Colors.text.copy(alpha = 0.5f))
        group.members.forEach { m ->
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    GroupMemberEmoji(m.emoji)
                    SmallText(m.name)
                    Spacer(Modifier.weight(1f))
                    if (m.memberID != userInfo.userID) {
                        androidx.compose.material3.Icon(
                            org.clear30.views.components.sfSymbol("minus.circle"),
                            contentDescription = "Remove",
                            tint = Clear30Colors.text.copy(alpha = 0.5f),
                            modifier = Modifier
                                .pressScale { scope.launch { controller.removeMember(m.memberID) } }
                                .size(25.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimens.cardSpacing / 2))
        // iOS GroupSettings leave button: a plain full-width card button with
        // red content (TextIconButton foregroundColor: redColor1) — not a
        // red gradient pill.
        GroupTextIconButton("Leave", "figure.walk.departure", foreground = Clear30Colors.red1) { onLeave() }
        Spacer(Modifier.height(Dimens.cardSpacing))
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
            DefaultText("Group name")
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
private fun LeaveGroupDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave group?") },
        text = { Text("Are you sure you want to leave the group?") },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Leaving…" else "Yes, leave", color = Clear30Colors.red2)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Stay") } },
    )
}

private fun shareGroupInvite(context: android.content.Context, group: Clear30Group) {
    // Match iOS's exact invite URL (Clear30Group.swift:267) — the `?group_id=`
    // QUERY form. iOS's URLManager joins ONLY off the `group_id` param, so the old
    // `clear30.org/group/<id>` PATH link silently failed for iOS recipients;
    // Android's own parser accepts both the path and query forms.
    val nameParam = group.name.orEmpty().replace(" ", "-")
    val link = "https://clear30.org/join-a-group/?group_id=${group.id}&name=$nameParam"
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            android.content.Intent.EXTRA_TEXT,
            "Join my Clear30 group! 🍃\n$link",
        )
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Invite a friend").apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

// MARK: - Shared date formatting (iOS CalendarUtils.swift)

private val SHORT_MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private fun daySuffix(day: Int): String = when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

/** "Jan 15th" (iOS `monthDayWithSuffix`). */
internal fun monthDayWithSuffix(date: LocalDate): String =
    "${SHORT_MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}${daySuffix(date.dayOfMonth)}"

/** Same calendar week (Sunday-start, matching the iOS US calendar). */
internal fun Instant.isSameWeekAs(other: Instant): Boolean {
    val tz = TimeZone.currentSystemDefault()
    fun startOfWeek(d: LocalDate): LocalDate = d.minus(DatePeriod(days = d.dayOfWeek.isoDayNumber % 7))
    return startOfWeek(toLocalDateTime(tz).date) == startOfWeek(other.toLocalDateTime(tz).date)
}

/** "Mon" if this week, else "Jan 15th" (iOS `groupNoteDateString`). */
internal fun groupNoteDateString(timestamp: Instant): String {
    val date = timestamp.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return if (timestamp.isSameWeekAs(org.clear30.util.now())) {
        date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    } else {
        monthDayWithSuffix(date)
    }
}

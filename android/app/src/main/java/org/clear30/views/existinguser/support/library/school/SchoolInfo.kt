package org.clear30.views.existinguser.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.getFutureActivities
import org.clear30.data.getMessages
import org.clear30.data.model.AppMode
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.SchoolData
import org.clear30.data.model.SchoolDataActivity
import org.clear30.data.model.SchoolDataResource
import org.clear30.data.model.UserInfo
import org.clear30.util.adding
import org.clear30.util.isSameDay
import org.clear30.util.justDay
import org.clear30.util.now
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultText
import org.clear30.views.components.Heading1
import org.clear30.views.components.IconButton
import org.clear30.views.components.SectionMenu
import org.clear30.views.components.SectionMenuOption
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.colorFromHex

/**
 * School library screen — ported from SchoolInfo.swift. Three sections behind a
 * [SectionMenu]: Resources (always), Messages (when the school has feed messages),
 * Activities (when it has activities). Cards route into the shared
 * [SchoolInfoDetailScreen] (activities/resources) or the message viewer.
 */

/** iOS `SchoolData.getGradient()` — color1→color2, bottomLeading→topTrailing. */
internal fun SchoolData.schoolGradient(): Brush = Clear30Gradients.linear(
    listOf(colorFromHex(color1), colorFromHex(color2)),
    Clear30Gradients.bottomLeading,
    Clear30Gradients.topTrailing,
)

/**
 * Gradient-tinted icon (iOS `Rectangle().fill(gradient).mask(Image)` /
 * `foregroundStyle(gradient)`). Same offscreen SrcAtop technique as the
 * achievements `GradientIcon`, but takes an [ImageVector] directly so school
 * screens can use Material icons that have no `sfSymbol()` mapping yet
 * (globe / figure.run / building.columns.fill / location.fill / link).
 */
@Composable
internal fun SchoolGradientIcon(image: ImageVector, brush: Brush, size: Dp, modifier: Modifier = Modifier) {
    Icon(
        imageVector = image,
        contentDescription = null,
        tint = Color.White,
        modifier = modifier
            .size(size)
            // Alpha just below 1 forces the offscreen layer SrcAtop needs
            // (same as achievements/AchievementCards.GradientIcon).
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                drawRect(brush, blendMode = BlendMode.SrcAtop)
            },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Hub section (Support tab)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * "Your School" hub section (iOS Support2.swift `schoolInfoSection`): a library
 * heading with a gradient graduation cap, then a gradient action card carrying
 * the school's long name. Rendered only when the user has school data.
 */
@Composable
internal fun SchoolInfoHubSection(userInfo: UserInfo, onOpen: () -> Unit) {
    val schoolData = userInfo.schoolData ?: return
    val gradient = remember(schoolData.color1, schoolData.color2) { schoolData.schoolGradient() }
    Column(Modifier.fillMaxWidth()) {
        // iOS `libraryHeading`: 18pt gradient icon + SmallText, cardSpacing/2 padding.
        Row(
            Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            SchoolGradientIcon(sfSymbol("graduationcap.fill"), gradient, 18.dp)
            SmallText("Your School", color = Clear30Colors.text)
        }
        Spacer(Modifier.height(Dimens.cardSpacing / 2))
        // GradientActionButton's exact shape, but with an ImageVector icon —
        // "building.columns.fill" has no sfSymbol() mapping (SfSymbols.kt is
        // outside this feature's file set), so the icon is passed directly.
        val icon = if (userInfo.mode == AppMode.B2B) sfSymbol("person.3.fill") else Icons.Rounded.AccountBalance
        Clear30Card(modifier = Modifier.fillMaxWidth().pressScale(onClick = onOpen), gradient = gradient) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                Box(
                    Modifier.size(36.dp)
                        .clip(RoundedCornerShape(Dimens.cornerRadius / 1.75f))
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = schoolData.long_name, tint = Color.White, modifier = Modifier.size(17.dp))
                }
                SmallText(schoolData.long_name, color = Color.White)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SchoolInfo screen
// ─────────────────────────────────────────────────────────────────────────────

private const val SECTION_RESOURCES = "Resources"
private const val SECTION_MESSAGES = "Messages"
private const val SECTION_ACTIVITIES = "Activities"

@Composable
internal fun SchoolInfoScreen(
    schoolData: SchoolData,
    program: Program,
    userInfo: UserInfo,
    onBack: () -> Unit,
    onOpenMessage: (ProgramMessage) -> Unit,
    onOpenActivity: (SchoolDataActivity) -> Unit,
    onOpenResource: (SchoolDataResource) -> Unit,
) {
    val gradient = remember(schoolData.color1, schoolData.color2) { schoolData.schoolGradient() }
    // iOS SchoolInfo.init: Resources always; Messages / Activities when non-empty.
    val sections = remember(schoolData) {
        buildList {
            add(SectionMenuOption(SECTION_RESOURCES, gradient, Icons.Rounded.Public))
            if (schoolData.messages.isNotEmpty()) {
                add(SectionMenuOption(SECTION_MESSAGES, gradient, sfSymbol("bubble.fill")))
            }
            if (schoolData.activities.isNotEmpty()) {
                add(SectionMenuOption(SECTION_ACTIVITIES, gradient, Icons.AutoMirrored.Rounded.DirectionsRun))
            }
        }
    }
    var currentSectionIndex by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(vertical = Dimens.headingTopPadding)) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(bottom = Dimens.headingTopPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1(schoolData.short_name)
        }

        // Full-bleed like iOS (SectionMenu supplies its own horizontal insets).
        SectionMenu(
            index = currentSectionIndex,
            items = sections,
            onSelect = { currentSectionIndex = it },
        )

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(top = Dimens.headingTopPadding),
        ) {
            when (sections[currentSectionIndex].type) {
                SECTION_MESSAGES -> SchoolMessagesSection(schoolData, program, userInfo, gradient, onOpenMessage)
                SECTION_ACTIVITIES -> SchoolActivitiesSection(schoolData, gradient, onOpenActivity)
                SECTION_RESOURCES -> SchoolResourcesSection(schoolData, gradient, onOpenResource)
            }
            Spacer(Modifier.height(Dimens.cardSpacing))
        }
    }
}

/** Messages tab — the school's feed messages sorted by unlock date (iOS `messages`). */
@Composable
private fun SchoolMessagesSection(
    schoolData: SchoolData,
    program: Program,
    userInfo: UserInfo,
    gradient: Brush,
    onOpenMessage: (ProgramMessage) -> Unit,
) {
    val messages = remember(schoolData, program.startDate) {
        schoolData.getMessages(program, userInfo).sortedBy { it.unlockOn }
    }
    messages.forEach { message ->
        SchoolMessageCard(message, program, gradient) { onOpenMessage(message) }
        Spacer(Modifier.height(Dimens.cardSpacing))
    }
}

/**
 * One school message row — port of iOS `MessageCard.from(message:gradient:program:)`
 * (white card: emoji+title on the left, a "Day N" pill outlined/filled with the
 * school gradient on the right; checkmark once the day's content is completed).
 */
@Composable
private fun SchoolMessageCard(message: ProgramMessage, program: Program, gradient: Brush, onClick: () -> Unit) {
    val dayString = program.getBreak(message.unlockOn)?.getBreakDay(message.unlockOn)?.let { "Day $it" }
        ?: message.unlockOn.shortMonthDateWithSuffix()
    val progress = program.contentInfo[PlainDate.from(message.unlockOn)]?.progress ?: 0.0

    Clear30Card(modifier = Modifier.fillMaxWidth().pressScale(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            SmallText(
                "${message.topicEmoji ?: "💬"} ${message.topicTitle}",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Dimens.cardSpacing / 2))
            val pill: @Composable () -> Unit = {
                Row(
                    // iOS MessageCard.pill literals: 10pt horizontal, 5pt vertical.
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    TinyText(dayString, maxLines = 1)
                    Icon(
                        sfSymbol(if (progress >= 1) "checkmark" else "arrow.right"),
                        contentDescription = null,
                        tint = Clear30Colors.text.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            if (progress >= 1) {
                Clear30Card(shadowColor = Color.Transparent, gradient = gradient, padding = false) { pill() }
            } else {
                Clear30Card(
                    shadowColor = Color.Transparent,
                    outlineGradient = gradient,
                    outlineOpacity = 0.25f,
                    padding = false,
                ) { pill() }
            }
        }
    }
}

/** Activities tab — Today / This week / Later buckets (iOS SchoolInfo.swift:103-153). */
@Composable
private fun SchoolActivitiesSection(
    schoolData: SchoolData,
    gradient: Brush,
    onOpenActivity: (SchoolDataActivity) -> Unit,
) {
    val activities = remember(schoolData) { schoolData.getFutureActivities() }
    val today = now().justDay

    val todayActivities = activities.filter { it.date_time.isSameDay(today) }
    val thisWeekActivities = activities.filter {
        it !in todayActivities && it.date_time < today.adding(days = 8)
    }
    val otherActivities = activities.filter {
        it !in todayActivities && it !in thisWeekActivities
    }

    @Composable
    fun bucket(title: String, items: List<SchoolDataActivity>) {
        if (items.isEmpty()) return
        SchoolSectionHeading(title, gradient)
        Spacer(Modifier.height(Dimens.cardSpacing))
        TwoColumnGrid(items) { activity ->
            SchoolInfoCard(
                title = activity.title,
                subtitle = activity.subtitle,
                badge = activity.date_time.schoolCardDateString(),
                gradient = gradient,
            ) { onOpenActivity(activity) }
        }
        Spacer(Modifier.height(Dimens.cardSpacing))
    }

    bucket("Today", todayActivities)
    bucket("This week", thisWeekActivities)
    bucket("Later", otherActivities)
}

/** Resources tab — sections sorted by title, 2-col grids (iOS SchoolInfo.swift:155-175). */
@Composable
private fun SchoolResourcesSection(
    schoolData: SchoolData,
    gradient: Brush,
    onOpenResource: (SchoolDataResource) -> Unit,
) {
    schoolData.resources.entries.sortedBy { it.key }.forEach { (sectionTitle, resources) ->
        SchoolSectionHeading(sectionTitle, gradient)
        Spacer(Modifier.height(Dimens.cardSpacing))
        TwoColumnGrid(resources) { resource ->
            SchoolInfoCard(
                icon = sfSymbol("lightbulb"),
                title = resource.title,
                subtitle = resource.subtitle,
                badge = resource.badge,
                gradient = gradient,
            ) { onOpenResource(resource) }
        }
        Spacer(Modifier.height(Dimens.cardSpacing))
    }
}

/**
 * Gradient pill heading (iOS Today-feed `SectionHeading` with a title-only
 * config): medium DefaultText on a 13pt-radius gradient card, self-sized.
 */
@Composable
private fun SchoolSectionHeading(title: String, gradient: Brush) {
    Row {
        Clear30Card(cornerRadius = 13.dp, gradient = gradient, padding = false) {
            // iOS SectionHeading literals: 5pt vertical / 12pt horizontal.
            DefaultText(title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = Color.White)
        }
    }
}

/** iOS `GridView(columns: 2, spacing: cardSpacing)` — equal-height rows of two. */
@Composable
private fun <T> TwoColumnGrid(items: List<T>, content: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                rowItems.forEach { item ->
                    Box(Modifier.weight(1f).fillMaxHeight()) { content(item) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Grid cell (iOS `SchoolInfoCard`): gradient icon top-left, dimmed badge (first
 * line only) top-right, then title and optional subtitle.
 */
@Composable
private fun SchoolInfoCard(
    title: String,
    gradient: Brush,
    icon: ImageVector = sfSymbol("calendar"),
    subtitle: String? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Clear30Card(modifier = Modifier.fillMaxSize().pressScale(onClick = onClick)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                SchoolGradientIcon(icon, gradient, 20.dp)
                Spacer(Modifier.weight(1f))
                TinyText(
                    badge?.lineSequence()?.firstOrNull() ?: "",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(Dimens.cardSpacing / 3))
            SmallText(title)
            subtitle?.let {
                Spacer(Modifier.weight(1f))
                TinyText(it)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date strings (iOS CalendarUtils ports)
// ─────────────────────────────────────────────────────────────────────────────

private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** iOS `daySuffixForDate()` / `Int.suffix()` — st / nd / rd / th. */
internal fun daySuffix(dayOfMonth: Int): String = when (dayOfMonth) {
    1, 21, 31 -> "st"
    2, 22 -> "nd"
    3, 23 -> "rd"
    else -> "th"
}

/** iOS `schoolCardDateString()` — "E, MMM d" (e.g. "Mon, Jul 13"). */
internal fun Instant.schoolCardDateString(): String {
    val dt = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${WEEKDAYS[dt.dayOfWeek.isoDayNumber - 1].take(3)}, ${MONTHS[dt.monthNumber - 1].take(3)} ${dt.dayOfMonth}"
}

/** iOS `Date.shortMonthDate` — "MMM d" + suffix (e.g. "Jan 15th"). */
internal fun Instant.shortMonthDateWithSuffix(): String {
    val dt = toLocalDateTime(TimeZone.currentSystemDefault())
    return "${MONTHS[dt.monthNumber - 1].take(3)} ${dt.dayOfMonth}${daySuffix(dt.dayOfMonth)}"
}

/** iOS `getActivityDateString` — "EEEE, MMMM d'th'\nh:mm a". */
internal fun Instant.activityDateString(): String {
    val dt = toLocalDateTime(TimeZone.currentSystemDefault())
    val hour12 = ((dt.hour + 11) % 12) + 1
    val amPm = if (dt.hour < 12) "AM" else "PM"
    val minute = dt.minute.toString().padStart(2, '0')
    return "${WEEKDAYS[dt.dayOfWeek.isoDayNumber - 1]}, ${MONTHS[dt.monthNumber - 1]} " +
        "${dt.dayOfMonth}${daySuffix(dt.dayOfMonth)}\n$hour12:$minute $amPm"
}

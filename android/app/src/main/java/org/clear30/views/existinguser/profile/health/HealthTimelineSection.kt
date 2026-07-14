package org.clear30.views.existinguser.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.HealthCategory
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramHealthProgress
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * HealthTimelinePage — full-page host for [HealthTimelineSection], opened by
 * tapping a health gauge card (iOS pushes
 * `NavigationDestination.healthProgressTimeline` from Profile.swift).
 */
@Composable
fun HealthTimelinePage(program: Program, userInfo: UserInfo, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                IconButton("chevron.backward", onClick = onBack)
                Heading1("Health")
            }
            HealthTimelineSection(program, userInfo)
        }
    }
}

/**
 * HealthTimelineSection — ported from the Profile health timeline.
 *
 * Renders the user's per-category milestone progress, marking each as
 * `Unlocked` (green checkmark) when the live `currentDay - setbackDays` has
 * reached the milestone's [ProgramHealthProgress.unlockedOnDay], or `Upcoming`
 * (gray, days-until label) otherwise. Per-card taps fire the `openedHealth`
 * analytic with the category as extra data.
 *
 * The category-grouped layout mirrors the iOS Profile health view; the section
 * is intentionally read-only — milestone definitions come from the program
 * sync, no local editing.
 */
@Composable
fun HealthTimelineSection(program: Program, userInfo: UserInfo) {
    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedHealthTimeline)
    }
    val milestones = program.healthProgress.sortedBy { it.unlockedOnDay }
    if (milestones.isEmpty()) {
        // Empty state — the program sync hasn't populated healthProgress yet.
        // Render a single placeholder card rather than nothing so the section
        // has consistent vertical rhythm with the rest of the profile.
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            Heading3("Health timeline")
            Clear30Card(modifier = Modifier.fillMaxWidth()) {
                SmallText(
                    "Your health milestones will appear here as the program syncs.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Heading3("Health timeline")
        milestones.forEach { m -> MilestoneRow(m, program, userInfo) }
    }
}

@Composable
private fun MilestoneRow(milestone: ProgramHealthProgress, program: Program, userInfo: UserInfo) {
    val effectiveDay = (program.currentDay - milestone.setbackDays).coerceAtLeast(0)
    val unlocked = effectiveDay >= milestone.unlockedOnDay
    val daysUntil = (milestone.unlockedOnDay - effectiveDay).coerceAtLeast(0)
    Clear30Card(
        modifier = Modifier.fillMaxWidth(),
        // Subtle gradient on unlocked milestones to make the green stand out
        // from the gray upcoming rows — same idea as the iOS gradient swap.
        gradient = if (unlocked) Clear30Gradients.clear30 else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(milestone.category, unlocked)
            Spacer(Modifier.width(Dimens.cardSpacing))
            Column(Modifier.weight(1f)) {
                SmallText(milestone.title)
                TinyText(
                    if (unlocked) "Unlocked on day ${milestone.unlockedOnDay}"
                    else "$daysUntil days to go",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
        }
    }
    // Per-card analytic; fires once per first render via LaunchedEffect(id).
    LaunchedEffect(milestone.id, unlocked) {
        if (unlocked) Logger.logEvent(
            userInfo.loggingID,
            LogEventType.openedHealth,
            mapOf(LogEventExtraDataType.HEALTH_CATEGORY to milestone.category.rawValue),
        )
    }
}

@Composable
private fun CategoryIcon(category: HealthCategory, unlocked: Boolean) {
    val symbol = when (category) {
        HealthCategory.BRAIN -> "brain.head.profile"
        HealthCategory.LUNGS -> "lungs.fill"
        HealthCategory.HEART -> "heart.fill"
        HealthCategory.SLEEP -> "moon.fill"
        HealthCategory.MOOD -> "face.smiling"
        HealthCategory.ENERGY -> "bolt.fill"
        HealthCategory.MEMORY -> "sparkles"
        HealthCategory.OTHER -> "heart.fill"
    }
    Box(
        Modifier.size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (unlocked) Clear30Colors.green.copy(alpha = 0.25f)
                else Clear30Colors.text.copy(alpha = 0.25f)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(symbol), contentDescription = null)
    }
}

package org.clear30.views.existinguser.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import org.clear30.data.model.ProgramMessage
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.ConfettiOverlay
import org.clear30.views.components.ElectricProgressBar
import org.clear30.views.components.Heading2
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * FeedEndCelebration — the end-of-feed page (iOS `TodayTabEndFeedView`, and its
 * viewer sibling `SingleMessageEndFeedView` when [showCta] is false). When the
 * page gains focus the topic card's progress bar animates to 100% in 7% ticks
 * every 90ms with a light haptic per tick, then a 100-piece confetti burst +
 * success haptic fire, then the CTA reveals at +1s and "Back to Top" at +2s
 * (viewer variant: Back to Top at +1s, no CTA).
 */
@Composable
internal fun FeedEndCelebration(
    message: ProgramMessage?,
    badge: Pair<String, String>? = null,
    focused: Boolean,
    alreadyComplete: Boolean,
    showCta: Boolean = false,
    unreadMessages: Int = 0,
    onAllMessages: () -> Unit = {},
    onCommunity: () -> Unit = {},
    onScrollToTop: () -> Unit,
    onCompleted: () -> Unit = {},
) {
    var animatedProgress by remember { mutableFloatStateOf(if (alreadyComplete) 1f else 0.01f) }
    var showConfetti by remember { mutableStateOf(false) }
    var showSecondary by remember { mutableStateOf(alreadyComplete) }
    var showBackToTop by remember { mutableStateOf(alreadyComplete) }
    var ranOnce by remember { mutableStateOf(alreadyComplete) }

    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        if (ranOnce) {
            animatedProgress = 1f
            showSecondary = true
            showBackToTop = true
            return@LaunchedEffect
        }
        ranOnce = true
        delay(100)
        animatedProgress = 0.01f
        while (animatedProgress < 1f) {
            delay(90)
            Haptics.lightImpact()
            animatedProgress = (animatedProgress + 0.07f).coerceAtMost(1f)
        }
        showConfetti = true
        Haptics.successHeavy()
        onCompleted()
        delay(1000)
        if (showCta) {
            showSecondary = true
            Haptics.mediumImpact()
            delay(1000)
        }
        showBackToTop = true
        Haptics.mediumImpact()
    }
    // The confetti canvas plays once (~1.5s) and holds its faded last frame;
    // remove it shortly after so it doesn't sit over the buttons.
    LaunchedEffect(showConfetti) {
        if (showConfetti) { delay(2500); showConfetti = false }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            message?.let { TopicProgressCard(it.topicEmoji, it.topicTitle, badge, animatedProgress) }
            if (showCta) {
                AnimatedVisibility(visible = showSecondary, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
                    if (unreadMessages > 0) {
                        FeedEndCta("All Messages", "$unreadMessages unread message${if (unreadMessages == 1) "" else "s"}", Clear30Gradients.clear30, onAllMessages)
                    } else {
                        FeedEndCta("Enter the Community", "Connect with others like you", Clear30Gradients.community, onCommunity)
                    }
                }
            }
            AnimatedVisibility(visible = showBackToTop, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
                BackToTopPill(onScrollToTop)
            }
        }
        if (showConfetti) ConfettiOverlay(count = 100)
    }
}

/**
 * The topic card with the animated completion state (iOS
 * `ProgramMessageTopicCard`): emoji + title (+ optional break badge); below,
 * a progress bar + live percentage that becomes a "100% Completed 🥹" button.
 */
@Composable
internal fun TopicProgressCard(
    emoji: String?,
    title: String,
    badge: Pair<String, String>? = null,
    progress: Float?,
) {
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                emoji?.let { Heading2(it) }
                Heading3(title, modifier = Modifier.weight(1f))
                badge?.let { (subtitle, badgeTitle) ->
                    Column(
                        Modifier.cardStyle(
                            outlineGradient = Clear30Gradients.clear30,
                            outlineOpacity = 0.5f,
                            padding = false,
                        ).padding(horizontal = Dimens.cardSpacing / 2, vertical = Dimens.cardSpacing / 3),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TinyText(subtitle, color = Clear30Colors.text.copy(alpha = 0.5f))
                        SmallText(badgeTitle)
                    }
                }
            }
            when {
                progress == null -> Unit
                progress >= 1f -> Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.cornerRadius)).background(Clear30Gradients.clear30)
                        .padding(vertical = Dimens.cardSpacing * 0.75f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallText("100% Completed 🥹", color = Color.White)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    Box(Modifier.weight(1f)) {
                        ElectricProgressBar(current = (progress * 100).toInt(), max = 100, height = 8.dp)
                    }
                    SmallText("${(progress * 100).toInt()}%", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/** Gradient CTA with a dim subtext line (iOS `TextIconButton` with subtext). */
@Composable
private fun FeedEndCta(text: String, subtext: String, gradient: Brush, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.cornerRadius)).background(gradient)
            .pressScale(onClick = onClick)
            .padding(vertical = Dimens.cardSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            SmallText(text, color = Color.White)
            Icon(sfSymbol("arrow.right"), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        TinyText(subtext, color = Color.White.copy(alpha = 0.75f))
    }
}

/** "Back to Top" pill (iOS `TinyTextButton(background: true, icon: chevron.up)`). */
@Composable
private fun BackToTopPill(onClick: () -> Unit) {
    Row(
        Modifier
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Clear30Colors.opacityGray)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
    ) {
        TinyText("Back to Top", color = Clear30Colors.text)
        Icon(sfSymbol("chevron.up"), contentDescription = null, tint = Clear30Colors.text, modifier = Modifier.size(10.dp))
    }
}

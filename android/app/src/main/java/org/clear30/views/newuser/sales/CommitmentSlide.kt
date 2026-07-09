package org.clear30.views.newuser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * OnboardingCheckmarkCommitment — 1:1 port of
 * `OnboardingCheckmarkCommitment.swift` (text + layout). White content on the
 * brand `clear30` gradient.
 *
 * The iOS view lets the user **draw a checkmark with their finger** to "sign"
 * their commitment, firing a confetti pop on completion and offering a "Set as
 * lockscreen" share. We render the pre-draw (visible) state faithfully — the
 * finger-drawing canvas, confetti, and lockscreen share are deferred (see
 * // TODO(port) markers) — so the prose and framing match iOS.
 *
 * Layout (top-leading):
 *   SmallText (opacity 0.5)  "Ready to commit?"
 *   Heading3                 "Users who commit to their goals, are more likely to succeed."
 *   Spacer
 *   TinyText (opacity 0.5, centered)  "Draw a checkmark to\nsignal your commitment!"
 *   [Drawing canvas]  — rounded rect, white@0.5 3px border, bottom commitment text
 *   Spacer
 *   "Skip" pill (pre-draw)  /  "Next" + "Set as lockscreen" (post-draw, TODO)
 */
@Composable
fun CommitmentSlide(userInfo: UserInfo, onNext: () -> Unit) {
    @Suppress("UNUSED_PARAMETER") val u = userInfo

    Column(
        Modifier
            .fillMaxSize()
            .background(Clear30Gradients.clear30)
            .statusBarsPadding()
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        horizontalAlignment = Alignment.Start,
    ) {
        SmallText(
            "Ready to commit?",
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        )
        Heading3(
            "Users who commit to their goals, are more likely to succeed.",
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Pre-draw instruction (iOS hides this once hasDrawnCheckmark).
            TinyText(
                "Draw a checkmark to\nsignal your commitment!",
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            )

            // TODO(port): finger-drawing canvas (DrawScope strokes + DragGesture),
            // confetti pop on first stroke, and the clear (xmark) button. For now
            // render the static framed prompt the iOS canvas wraps.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = Dimens.horizontalPadding)
                    .clip(RoundedCornerShape(Dimens.cornerRadius))
                    .border(
                        width = 3.dp,
                        color = Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(Dimens.cornerRadius),
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                MiniText(
                    "I am making a commitment to personal growth during my Clear30 and beyond.",
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.cardSpacing),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Pre-draw state shows "Skip"; after drawing iOS swaps to a "Next"
        // TextIconButton + a "Set as lockscreen" TinyTextButton.
        // TODO(port): post-draw branch (Next + Set-as-lockscreen share) once the
        // drawing canvas is wired. The skip pill advances the flow exactly like
        // iOS `goToNextScreen()`.
        SkipPillButton(
            text = "Skip",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            onClick = onNext,
        )
    }
}

/**
 * TinyTextButton — port of the iOS `TinyTextButton(foregroundColor: .white,
 * backgroundColor: .white.opacity(0.25))`: a small white-on-translucent-white
 * rounded pill (corner radius 12, horizontal 10 / vertical 5 padding).
 */
@Composable
private fun SkipPillButton(
    text: String,
    icon: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier
            .pressScale(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
    ) {
        TinyText(text, color = Color.White, maxLines = 1)
        if (icon != null) {
            Icon(
                sfSymbol(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.height(10.dp),
            )
        }
    }
}

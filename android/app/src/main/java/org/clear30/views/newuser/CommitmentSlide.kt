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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.model.UserInfo
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3
import org.clear30.views.components.MiniText
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * OnboardingCheckmarkCommitment — port of OnboardingCheckmarkCommitment.swift
 * (text + layout). The iOS view lets the user **draw a checkmark with their
 * finger** to "sign" their commitment, with a confetti pop on completion and
 * a "Set as lockscreen" share helper. The drawing-gesture canvas is a TODO
 * port; the text + structure below match iOS so the prose is faithful.
 *
 * Layout (white text on brand gradient bg):
 *   SmallText (opacity 0.5)  "Ready to commit?"
 *   Heading3                 "Users who commit to their goals, are more likely to succeed."
 *   Spacer
 *   TinyText (opacity 0.5)   "Draw a checkmark to\nsignal your commitment!"
 *   [Drawing canvas]         — rounded rect with white border + the commitment text
 *   Spacer
 *   "Skip" (before drawing)  /  "Next" + "Set as lockscreen" (after drawing)
 */
@Composable
fun CommitmentSlide(userInfo: UserInfo, onNext: () -> Unit) {
    @Suppress("UNUSED_PARAMETER") val u = userInfo

    Column(
        Modifier
            .fillMaxSize()
            .background(Clear30Gradients.clear30)
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
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
            TinyText(
                "Draw a checkmark to\nsignal your commitment!",
                color = Color.White.copy(alpha = 0.5f),
            )

            // TODO(port): Drawing canvas with DrawScope + gesture handling.
            // For now render the static framed prompt that the iOS canvas
            // wraps so the prose is in place.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
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
                    modifier = Modifier.padding(Dimens.cardSpacing),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // TODO(port): swap between Skip (pre-draw) and Next + Set-as-lockscreen
        // (post-draw) once the drawing canvas is wired. For now ship Next so
        // the flow can advance.
        DefaultButton(
            title = "Next",
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
            onClick = onNext,
        )
    }
}

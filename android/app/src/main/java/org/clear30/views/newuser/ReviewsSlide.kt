package org.clear30.views.newuser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.clear30.data.model.UserInfo
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * OnboardingReviews — port of OnboardingReviews.swift.
 *
 *   Heading3   "Clear30 was designed for people like you."
 *   SmallText  "Give us a rating!" (opacity 0.5)
 *
 *   InfoScreenLaurels (big)            [TODO: laurel branches + 5 big stars]
 *   users row: 3 emoji circles + "+ 15,000 people"
 *   reviewsList — fetched from Supabase
 *
 *   "Next" button (disabled for the first 2s on appear)
 *
 * The Supabase getReviews fetch is a TODO — the iOS view starts with an empty
 * list and populates after the network round-trip; we mirror that with an
 * empty list until the RPC is wired.
 */
@Composable
fun ReviewsSlide(userInfo: UserInfo, onNext: () -> Unit) {
    @Suppress("UNUSED_PARAMETER") val u = userInfo
    var canProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2000)
        canProgress = true
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(
            horizontal = Dimens.horizontalPadding,
            vertical = Dimens.headingTopPadding,
        ),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            Heading3("Clear30 was designed for people like you.")
            SmallText(
                "Give us a rating!",
                modifier = Modifier.padding(top = Dimens.cardSpacing / 2, bottom = Dimens.cardSpacing * 3),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )

            // InfoScreenLaurels (big) lives in IntroScreen.kt — TODO: reuse it
            // here once it's promoted to a shared component.

            UsersRow(modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing * 3))

            // TODO(port): Supabase getReviews → reviewsList rendering. Empty
            // until the RPC is wired (matches iOS first-paint state).
        }

        DefaultButton(
            title = "Next",
            gradient = if (canProgress) Clear30Gradients.clear30 else Clear30Gradients.grayFlat,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing),
        ) { if (canProgress) onNext() }
    }
}

@Composable
private fun UsersRow(modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        listOf("☺️", "😎", "🥹").forEach { e ->
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Clear30Colors.opacityGray),
                contentAlignment = Alignment.Center,
            ) { SmallText(e) }
        }
        SmallText("+ 15,000 people", color = Clear30Colors.text.copy(alpha = 0.5f))
    }
}

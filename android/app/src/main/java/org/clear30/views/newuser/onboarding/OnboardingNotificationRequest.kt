package org.clear30.views.newuser

import kotlinx.coroutines.launch
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.clear30.R
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * OnboardingNotificationRequest — 1:1 port of `OnboardingNotificationRequest.swift`.
 *
 * Leading VStack(spacing: 0): Heading3 "Reach your goals with notifications."
 * (+cardSpacing/2 bottom), a half-opacity explainer SmallText, then the
 * `MockNotificationPopUp` — the iOS notification illustration centered in the
 * remaining space with a "👆" Heading1 nudge and the "80% more likely" line. The
 * whole illustration region is tappable and advances the flow.
 *
 * Android also fires the POST_NOTIFICATIONS runtime request (API 33+) on tap, then
 * advances regardless of grant.

 */
@Composable
fun OnboardingNotificationRequest(onComplete: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // iOS re-anchors the abandoned-onboarding reminders the moment the
        // permission lands (OnboardingNotificationRequest.swift:27) — before
        // this, none could ever be delivered.
        if (granted) {
            org.clear30.Clear30Application.appScope.launch {
                runCatching {
                    val userInfo = org.clear30.data.Clear30Store.loadUserInfo()
                    if (userInfo.completedOnboarding != true) {
                        org.clear30.data.NotificationHandler.scheduleAbandonedOnboarding(
                            userInfo,
                            org.clear30.data.Clear30Store.loadProgram(),
                        )
                    }
                }
            }
        }
        onComplete()
    }

    val request: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onComplete()
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(
            horizontal = Dimens.horizontalPadding,
            vertical = Dimens.headingTopPadding,
        ),
        horizontalAlignment = Alignment.Start,
    ) {
        Heading3("Reach your goals with notifications.")
        SmallText(
            "Clear30 uses daily notifications to keep you on track with your goals and become the best version of yourself.",
            modifier = Modifier.padding(top = Dimens.cardSpacing / 2),
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )

        MockNotificationPopUp(
            extraText = "Users who enable notifications are 80% more likely to succeed.",
            modifier = Modifier.weight(1f).fillMaxWidth(),
            onTap = request,
        )
    }
}

/**
 * MockNotificationPopUp — 1:1 port of the iOS `MockNotificationPopUp`: centered in
 * its frame, a Spacer, the notification illustration (fit), a right-aligned "👆"
 * Heading1 (~65% width), a centered half-opacity `extraText`, then a Spacer. The
 * whole region is tappable.
 */
@Composable
private fun MockNotificationPopUp(
    extraText: String? = null,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    Column(
        modifier.fillMaxHeight().pressScale(onClick = onTap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Image(
            painter = painterResource(R.drawable.assessment_notification_popup),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )

        // Right-aligned "👆" nudge, ~65% of the width (iOS: geometry.size.width * 0.65, trailing).
        Box(Modifier.fillMaxWidth(0.65f).padding(vertical = Dimens.cardSpacing)) {
            Heading1("👆", Modifier.align(Alignment.CenterEnd))
        }

        if (!extraText.isNullOrEmpty()) {
            SmallText(
                extraText,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(horizontal = Dimens.cardSpacing),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

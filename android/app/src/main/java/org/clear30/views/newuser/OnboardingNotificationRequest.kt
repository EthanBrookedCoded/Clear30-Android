package org.clear30.views.newuser

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * OnboardingNotificationRequest — ported from OnboardingNotificationRequest.swift.
 * iOS requested UN authorization; Android requests the POST_NOTIFICATIONS runtime
 * permission (API 33+), then advances regardless of the grant result.
 *
 * TODO(port): NotificationHandler.scheduleAbandonedOnboarding on grant; the mock
 * notification image awaits asset migration.
 */
@Composable
fun OnboardingNotificationRequest(onComplete: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> onComplete() }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Heading3("Reach your goals with notifications.", Modifier.padding(bottom = Dimens.cardSpacing / 2))
        SmallText(
            "Clear30 uses daily notifications to keep you on track with your goals and become the best version of yourself.",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
        SmallText(
            "Users who enable notifications are 80% more likely to succeed.",
            Modifier.padding(vertical = Dimens.cardSpacing),
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
        DefaultButton("Enable notifications", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onComplete()
            }
        }
        SmallText("Maybe later", Modifier.padding(top = Dimens.cardSpacing).fillMaxWidth().padding(8.dp), color = Color.Gray)
    }
}

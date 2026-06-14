package org.clear30.views.newuser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.UserInfo
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * OnboardingReferralCode — port of OnboardingReferralCode.swift. Minimal
 * "Do you have a referral code?" prompt with an inline input and a Next button
 * that handles the code (or skips when empty). The
 * `SupabaseController.checkReferralCodeJson` round-trip is a TODO matching the
 * iOS handler; the local persistence + analytics fire here for parity.
 */
@Composable
fun ReferralSlide(userInfo: UserInfo, onNext: () -> Unit) {
    var code by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(
            horizontal = Dimens.horizontalPadding,
            vertical = Dimens.headingTopPadding,
        ),
        horizontalAlignment = Alignment.Start,
    ) {
        Heading3("Do you have a referral code?")
        SmallText(
            "You can skip this step.",
            modifier = Modifier.padding(top = Dimens.cardSpacing / 2),
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )

        Spacer(Modifier.weight(1f))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            placeholder = { Text("Referral code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        DefaultButton(
            title = "Next",
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val trimmed = code.trim().lowercase()
            scope.launch {
                if (trimmed.isNotEmpty()) {
                    userInfo.referralCode = trimmed
                    Clear30Store.save(userInfo)
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.usedReferralCode,
                        mapOf(LogEventExtraDataType.TYPE to trimmed),
                    )
                    // TODO(port): SupabaseController.checkReferralCodeJson —
                    // group_id handling, freeCode flag, Helium init.
                }
                onNext()
            }
        }
    }
}

@Suppress("unused") private val unusedArrangement = Arrangement.Top

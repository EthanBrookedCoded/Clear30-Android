package org.clear30.views.newuser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.checkReferralCodeJson
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.OffWhiteInput
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * OnboardingReferralCode — 1:1 port of `OnboardingReferralCode.swift`.
 *
 * Leading VStack(spacing: 0): Heading3 prompt, a half-opacity "You can skip this
 * step." SmallText, a flexible spacer, the `OffWhiteInput` referral field, another
 * spacer, then the white `TextIconButton` ("Next" + trailing arrow). Runs the
 * iOS `handleReferralCode` round-trip: `checkReferralCodeJson` → free-code
 * unlock + group-join alert (`onboardingSetup.groupToJoin`). Note the code path
 * does NOT resolve a school_id — school assignment comes from the email-domain
 * check or deep link. Where iOS re-inits Helium so the referral trait hits
 * paywall targeting, Android re-pushes the RC subscriber attributes.
 */
@Composable
fun ReferralSlide(
    userInfo: UserInfo,
    onboardingSetup: org.clear30.data.model.OnboardingSetup,
    onNext: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(
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

        OffWhiteInput(
            value = code,
            onValueChange = { code = it },
            placeholder = "Referral code",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.weight(1f))

        TextIconButton(text = "Next", icon = "arrow.right") {
            val trimmed = code.trim().lowercase()
            scope.launch {
                if (trimmed.isNotEmpty()) {
                    userInfo.referralCode = trimmed
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.usedReferralCode,
                        mapOf(LogEventExtraDataType.TYPE to trimmed),
                    )
                    // iOS OnboardingReferralCode.handleReferralCode: validate the
                    // code; a free code unlocks the app, a group code offers the
                    // group join.
                    val result = SupabaseController.checkReferralCodeJson(trimmed)
                    if (result?.is_free == true) userInfo.freeCode = trimmed
                    Clear30Store.save(userInfo)
                    // iOS re-inits Helium with fresh getUserParams so the
                    // referral_code trait reaches paywall targeting; Android
                    // re-pushes the RC subscriber attributes instead.
                    org.clear30.data.PaywallController.updateUserAttributes(
                        userInfo,
                        org.clear30.data.PaywallController.getUserParams(
                            userInfo,
                            onboardingSetup.assessmentInfo?.responses ?: emptyList(),
                        ),
                    )
                    val groupID = result?.group_id
                    if (groupID != null) {
                        org.clear30.data.AlertHandler.show(
                            org.clear30.data.AlertHandler.Alert(
                                title = "Join Group?",
                                message = "This referral code includes access to a group. Would you like to join it?",
                                primaryLabel = "Yes",
                                onPrimary = {
                                    onboardingSetup.groupToJoin = groupID
                                    scope.launch { Clear30Store.save(onboardingSetup) }
                                    onNext()
                                },
                                secondaryLabel = "No",
                                onSecondary = { onNext() },
                            ),
                        )
                        return@launch
                    }
                }
                onNext()
            }
        }
    }
}

/**
 * TextIconButton — 1:1 port of the iOS `TextIconButton`: a full-width off-white
 * card pill with centered dark text + a trailing icon (`imageSize` = 12).
 */
@Composable
private fun TextIconButton(text: String, icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Clear30Card(modifier = modifier.fillMaxWidth().pressScale(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (text.isNotEmpty()) SmallText(text, maxLines = 1)
            if (icon.isNotBlank()) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(sfSymbol(icon), contentDescription = null, tint = LocalContentColor.current, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

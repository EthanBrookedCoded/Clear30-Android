package org.clear30.views.newuser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
 * spacer, then the white `TextIconButton` ("Next" + trailing arrow). The
 * `SupabaseController.checkReferralCodeJson` round-trip (group-join alert, freeCode
 * flag, Helium init) is a TODO matching the iOS handler; the local persistence +
 * analytics fire here for parity.
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
                    Clear30Store.save(userInfo)
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.usedReferralCode,
                        mapOf(LogEventExtraDataType.TYPE to trimmed),
                    )
                    // TODO(port): SupabaseController.checkReferralCodeJson —
                    // group_id join alert, freeCode flag, Helium init.
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

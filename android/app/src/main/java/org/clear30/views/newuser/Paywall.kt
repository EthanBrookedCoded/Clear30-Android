package org.clear30.views.newuser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.clear30.data.model.EntitlementType
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Paywall — ported from Paywall.swift. iOS rendered a Helium-managed paywall
 * over RevenueCat; on Android we render RevenueCat offerings directly (a Helium
 * Android paywall view can replace this body later — see HeliumPaywallView TODO).
 * Purchasing a package resolves to [EntitlementType.DEFAULT].
 */
@Composable
fun Paywall(
    popup: Boolean = false,
    hard: Boolean = true,
    onCompleted: (EntitlementType?) -> Unit,
) {
    // TODO(port): RevenueCat Android SDK 8.x changed the offerings/purchase API
    // surface — `getOfferingsWith`, `Offerings.current`, `Package.product.price`
    // shape, `PurchaseParams.Builder` — all need reconciliation against the
    // version resolved in libs.versions.toml (currently a placeholder paywall
    // renders so the onboarding flow continues to compile + run). Wire the
    // real offerings list once the paywall views land; see android/TODO.md.
    @Suppress("UNUSED_VARIABLE") val unused = popup to hard

    Column(Modifier.fillMaxSize().padding(Dimens.horizontalPadding)) {
        if (popup && !hard) {
            IconButton("xmark", modifier = Modifier.align(Alignment.End)) { onCompleted(null) }
        }
        Spacer(Modifier.weight(1f))
        Heading1("Unlock Clear30")
        Spacer(Modifier.padding(Dimens.cardSpacing))
        SmallText(
            "Paywall placeholder — wire RevenueCat offerings here.",
            color = androidx.compose.ui.graphics.Color.Gray,
        )
        Spacer(Modifier.padding(Dimens.cardSpacing))
        // Debug-only continue button so the onboarding flow can reach the main
        // app while the real RevenueCat offerings UI is unimplemented. Released
        // builds drop this — the paywall is a hard wall until real packages
        // are wired.
        if (org.clear30.BuildConfig.DEBUG) {
            DefaultButton(
                "Continue (debug)",
                gradient = Clear30Gradients.clear30,
                modifier = Modifier.fillMaxWidth(),
            ) { onCompleted(EntitlementType.DEFAULT) }
        }
        Spacer(Modifier.weight(1f))
    }
}

package org.clear30.views.newuser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import kotlinx.coroutines.launch
import org.clear30.data.PaywallController
import org.clear30.data.model.EntitlementType
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Paywall entry point. When a `HELIUM_API_KEY` is configured this presents the
 * Helium remote/A-B-tested paywall (iOS parity — iOS renders Helium over
 * RevenueCat); otherwise, or when Helium can't show (holdout / error / not
 * configured), it falls back to [NativePaywall]. Dev builds (blank key) always get
 * the native path, unchanged.
 */
@Composable
fun Paywall(
    userInfo: UserInfo? = null,
    popup: Boolean = false,
    hard: Boolean = true,
    onCompleted: (EntitlementType?) -> Unit,
) {
    var fellBack by remember { mutableStateOf(false) }
    if (PaywallController.heliumEnabled() && !fellBack) {
        HeliumPaywall(userInfo, popup, hard, onCompleted, onFallback = { fellBack = true })
        return
    }
    NativePaywall(userInfo, popup, hard, onCompleted)
}

/**
 * NativePaywall — ported from Paywall.swift's RevenueCat path. Renders the
 * RevenueCat `Default` offering directly (`$rc_annual` / `$rc_monthly`). Tapping a
 * package launches the Google Play purchase; success resolves the [EntitlementType]
 * (`Plus` / `Core`) via [PaywallController]. When RevenueCat isn't configured (no
 * API key / no offering, e.g. local dev), a dev "Continue" pass-through keeps
 * onboarding runnable.
 */
@Composable
private fun NativePaywall(
    userInfo: UserInfo? = null,
    popup: Boolean = false,
    hard: Boolean = true,
    onCompleted: (EntitlementType?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offering by remember { mutableStateOf<Offering?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // iOS Paywall auto-completes when a free code is present
        // (HeliumPaywallView.registerFreeCodePayment — logs a free `subscribed`).
        val freeCode = userInfo?.freeCode
        if (userInfo != null && freeCode != null) {
            org.clear30.data.Logger.logEvent(
                userInfo.loggingID,
                org.clear30.data.LogEventType.subscribed,
                mapOf(org.clear30.data.LogEventExtraDataType.FREE_CODE to freeCode),
            )
            onCompleted(EntitlementType.DEFAULT)
            return@LaunchedEffect
        }
        offering = PaywallController.currentOffering()
        loading = false
        // iOS logs `.openedPaywall` when the paywall renders and stamps
        // currentPaywallID (the hard-paywall lookup keys off it).
        offering?.let { off ->
            PaywallController.setCurrentPaywallID(off.identifier)
            if (userInfo != null) {
                org.clear30.data.Logger.logEvent(
                    userInfo.loggingID,
                    org.clear30.data.LogEventType.openedPaywall,
                    mapOf(
                        org.clear30.data.LogEventExtraDataType.TYPE to off.identifier,
                        org.clear30.data.LogEventExtraDataType.TITLE to "revenuecat",
                        org.clear30.data.LogEventExtraDataType.PLACEMENT to
                            (if (popup) org.clear30.data.PaywallTrigger.CLEAR30_POPUP.raw
                            else org.clear30.data.PaywallTrigger.CLEAR30_ONBOARDING.raw),
                    ),
                )
            }
        }
    }

    // iOS Paywall.onDisappear — drop the render-scoped paywall state.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { PaywallController.clearPaywallPresentation() }
    }

    Column(
        Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        if (popup && !hard) {
            IconButton("xmark", modifier = Modifier.align(Alignment.End)) { onCompleted(null) }
        }
        Spacer(Modifier.weight(1f))
        Heading1("Unlock Clear30")
        SmallText(
            "Daily content, check-ins, meditations, and your Clear30 break — all in one place.",
            color = Clear30Colors.text.copy(alpha = 0.5f),
        )
        Spacer(Modifier.padding(Dimens.cardSpacing / 2))

        val packages = offering?.availablePackages.orEmpty()
        when {
            loading -> CircularProgressIndicator(color = Clear30Colors.accent)
            packages.isEmpty() -> {
                // RevenueCat not configured (no key / offering) — dev pass-through.
                SmallText("Store unavailable — continue in developer mode.", color = Color.Gray)
                DefaultButton("Continue", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
                    onCompleted(EntitlementType.DEFAULT)
                }
            }
            else -> {
                packages.forEach { pkg ->
                    PackageCard(pkg, enabled = !busy) {
                        val activity = context.findActivity() ?: return@PackageCard
                        busy = true
                        scope.launch {
                            val entitlement = PaywallController.purchase(activity, pkg)
                            busy = false
                            if (entitlement != null) {
                                // iOS HeliumPaywallView.handlePaid — log the sale.
                                userInfo?.let {
                                    org.clear30.data.Logger.logEvent(
                                        it.loggingID,
                                        org.clear30.data.LogEventType.subscribed,
                                        mapOf(
                                            org.clear30.data.LogEventExtraDataType.POPUP to "$popup",
                                            org.clear30.data.LogEventExtraDataType.TYPE to pkg.product.id,
                                        ),
                                    )
                                }
                                onCompleted(entitlement)
                            }
                        }
                    }
                }
                SmallText(
                    if (busy) "Processing…" else "Restore purchases",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 2)
                        .pressScale {
                            if (busy) return@pressScale
                            busy = true
                            scope.launch {
                                val entitlement = PaywallController.restore()
                                busy = false
                                if (entitlement != null) {
                                    // iOS handlePaid(restored: true).
                                    userInfo?.let {
                                        org.clear30.data.Logger.logEvent(
                                            it.loggingID,
                                            org.clear30.data.LogEventType.subscribed,
                                            mapOf(
                                                org.clear30.data.LogEventExtraDataType.POPUP to "$popup",
                                                org.clear30.data.LogEventExtraDataType.EXTRA to "restored",
                                            ),
                                        )
                                    }
                                    onCompleted(entitlement)
                                } else {
                                    // iOS restore surfaces a "nothing to restore" /
                                    // failure state via the master alert.
                                    org.clear30.data.AlertHandler.error(
                                        message = "No purchases were found to restore.",
                                    )
                                }
                            }
                        },
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PackageCard(pkg: Package, enabled: Boolean, onClick: () -> Unit) {
    Clear30Card(
        modifier = Modifier.fillMaxWidth().pressScale { if (enabled) onClick() },
        gradient = Clear30Gradients.clear30,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Heading3(pkg.label(), color = Color.White)
                pkg.perPeriodHint()?.let { SmallText(it, color = Color.White.copy(alpha = 0.75f)) }
            }
            Heading3(pkg.product.price.formatted, color = Color.White)
        }
    }
}

/** Friendly period label for the package (RevenueCat `PackageType`). */
private fun Package.label(): String = when (packageType) {
    PackageType.ANNUAL -> "Yearly"
    PackageType.MONTHLY -> "Monthly"
    PackageType.WEEKLY -> "Weekly"
    PackageType.SIX_MONTH -> "6 months"
    PackageType.THREE_MONTH -> "3 months"
    PackageType.TWO_MONTH -> "2 months"
    PackageType.LIFETIME -> "Lifetime"
    else -> product.title.ifBlank { identifier }
}

private fun Package.perPeriodHint(): String? = when (packageType) {
    PackageType.ANNUAL -> "Billed yearly"
    PackageType.MONTHLY -> "Billed monthly"
    else -> null
}

/** Unwrap the Compose [Context] to the hosting [Activity] (Play Billing needs it). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

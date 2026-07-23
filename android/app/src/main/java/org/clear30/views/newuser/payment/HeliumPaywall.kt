package org.clear30.views.newuser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tryhelium.paywall.core.Helium
import com.tryhelium.paywall.core.HeliumPresentationStyle
import com.tryhelium.paywall.core.PaywallPresentationConfig
import com.tryhelium.paywall.core.event.PaywallEventHandlers
import com.tryhelium.paywall.core.event.PaywallWebViewRendered
import com.tryhelium.paywall.revenuecat.RevenueCatDelegate
import com.tryhelium.paywall.ui.PaywallNotShownReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.PaywallController
import org.clear30.data.PaywallTrigger
import org.clear30.data.model.EntitlementType
import org.clear30.data.model.UserInfo
import org.clear30.views.theme.Clear30Colors

/**
 * HeliumPaywall — port of iOS `HeliumPaywallView`. Presents the remote,
 * A/B-tested Helium paywall (imperative full-screen SDK presentation — Android has
 * no Compose component) and maps its events back onto the existing
 * [PaywallController] flow, exactly as iOS's `HeliumRevenueCatDelegate` drives
 * `PaywallController.event`. RevenueCat sits underneath as the purchase engine
 * (Helium's `RevenueCatDelegate`), so entitlement resolution is unchanged.
 *
 * Deliberately NOT ported (§6 / out of scope): the one-time-offer downsell,
 * background down-sell notification, Stripe Apple Pay, and the sale/free-code
 * remote checks — free codes ARE honored here (iOS `registerFreeCodePayment`).
 *
 * [onFallback] is invoked when Helium can't show the paywall (targeting holdout /
 * error), so the caller ([Paywall]) renders the native RevenueCat paywall instead.
 */
@Composable
fun HeliumPaywall(
    userInfo: UserInfo?,
    popup: Boolean,
    hard: Boolean,
    onCompleted: (EntitlementType?) -> Unit,
    onFallback: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember { context.findActivity() }

    LaunchedEffect(Unit) {
        if (activity == null) { onFallback(); return@LaunchedEffect }
        presentHeliumPaywall(activity, userInfo, popup, hard, scope, onCompleted, onFallback)
    }

    // iOS Paywall.onDisappear — drop the render-scoped paywall state.
    DisposableEffect(Unit) { onDispose { PaywallController.clearPaywallPresentation() } }

    // Loading scrim behind Helium's own full-screen presentation.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Clear30Colors.accent)
    }
}

private fun presentHeliumPaywall(
    activity: Activity,
    userInfo: UserInfo?,
    popup: Boolean,
    hard: Boolean,
    scope: CoroutineScope,
    onCompleted: (EntitlementType?) -> Unit,
    onFallback: () -> Unit,
) {
    // Trigger mirrors the native path's placement (Paywall.kt). The life/advanced
    // variants are dashboard-configurable refinements once program state is threaded.
    val trigger = if (popup) PaywallTrigger.CLEAR30_POPUP.raw else PaywallTrigger.CLEAR30_ONBOARDING.raw

    // Attach the RevenueCat purchase bridge (its constructor requires an Activity,
    // so it's created per-presentation rather than at global init).
    runCatching { Helium.config.heliumPaywallDelegate = RevenueCatDelegate(activity) }
        .onFailure { android.util.Log.w("Paywall", "Helium RC delegate failed: ${it.message}") }

    val traits = userInfo?.let { PaywallController.heliumTraits(PaywallController.getUserParams(it)) }

    // Resolve the active entitlement (RevenueCat has it post-purchase/restore) and
    // finish onboarding — iOS handlePaid → completed(entitlement).
    val complete: () -> Unit = {
        scope.launch {
            val entitlement = PaywallController.activeEntitlement(userInfo) ?: EntitlementType.DEFAULT
            userInfo?.let {
                Logger.logEvent(
                    it.loggingID,
                    LogEventType.subscribed,
                    mapOf(LogEventExtraDataType.POPUP to "$popup"),
                )
            }
            onCompleted(entitlement)
        }
    }

    val eventHandlers = PaywallEventHandlers(
        // Soft paywalls close to free mode on dismiss; hard paywalls block
        // dismissal (disableSystemBackNavigation = hard).
        onDismissed = { if (!hard) onCompleted(null) },
        onOpenFailed = { onFallback() },
        onAnyEvent = { event ->
            // iOS PaywallWebViewRenderedEvent → log openedPaywall + stamp the ID.
            if (event is PaywallWebViewRendered) {
                PaywallController.setCurrentPaywallID(event.paywallName)
                userInfo?.let {
                    Logger.logEvent(
                        it.loggingID,
                        LogEventType.openedPaywall,
                        mapOf(
                            LogEventExtraDataType.TYPE to event.paywallName,
                            LogEventExtraDataType.TITLE to "helium",
                            LogEventExtraDataType.PLACEMENT to event.triggerName,
                        ),
                    )
                }
            }
        },
    )

    val config = PaywallPresentationConfig(
        fromActivityContext = activity,
        customPaywallTraits = traits,
        dontShowIfAlreadyEntitled = false,
        disableSystemBackNavigation = hard,
        presentationStyle = HeliumPresentationStyle.SLIDE_UP,
        fullscreen = true,
    )

    runCatching {
        Helium.presentPaywall(
            trigger = trigger,
            config = config,
            onEntitled = complete, // purchased, restored, or already entitled
            eventListener = eventHandlers,
            onPaywallNotShown = { reason ->
                when (reason) {
                    is PaywallNotShownReason.AlreadyEntitled -> complete()
                    else -> onFallback() // targeting holdout / error → native paywall
                }
            },
        )
    }.onFailure {
        android.util.Log.w("Paywall", "Helium present failed: ${it.message}")
        onFallback()
    }
}

/** Unwrap the Compose [Context] to the hosting [Activity] (Helium/Play need it). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

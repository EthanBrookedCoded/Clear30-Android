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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tryhelium.paywall.core.Helium
import com.tryhelium.paywall.core.HeliumPresentationStyle
import com.tryhelium.paywall.core.PaywallPresentationConfig
import com.tryhelium.paywall.core.event.PaywallContextEvent
import com.tryhelium.paywall.core.event.PaywallEventHandlers
import com.tryhelium.paywall.core.event.PaywallOpenFailed
import com.tryhelium.paywall.core.event.PaywallWebViewRendered
import com.tryhelium.paywall.core.event.ProductSelected
import com.tryhelium.paywall.core.event.PurchaseCancelled
import com.tryhelium.paywall.core.event.PurchaseFailed
import com.tryhelium.paywall.core.event.PurchasePending
import com.tryhelium.paywall.core.event.PurchaseSucceeded
import com.tryhelium.paywall.core.event.PurchasedPressed
import com.tryhelium.paywall.revenuecat.RevenueCatDelegate
import com.tryhelium.paywall.ui.PaywallNotShownReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.PaywallController
import org.clear30.data.PaywallDiag
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

    // Helium presents into its OWN Activity. When the system tears that Activity
    // down while the app is backgrounded, Helium emits PaywallClose and nothing
    // brings it back — this composable is still mounted, so the user returns to a
    // bare loading scrim. On a hard paywall there is no dismiss, so that is a dead
    // end. Re-present on the next foreground instead.
    val completed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    // Whether Helium's Activity is currently up. Driven by its own open/close
    // events, so the decision below never depends on close-vs-resume ordering.
    val showing = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    // Set the first time Helium's Activity actually opens, so a resume during the
    // initial present (before PaywallOpen arrives) can't trigger a duplicate.
    val hasOpened = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var presentation by remember { mutableIntStateOf(0) }
    // Set when the paywall closes while we are NOT resumed, so the next resume
    // picks it up. Helium delivers PaywallClose *after* ON_RESUME, so the resume
    // handler alone can never see the close that just happened.
    val pendingRepresent = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(presentation) {
        if (activity == null) { onFallback(); return@LaunchedEffect }
        PaywallDiag.log("helium presenting paywall (attempt ${presentation + 1})")
        presentHeliumPaywall(
            activity, userInfo, popup, hard, scope, completed, showing, hasOpened,
            onCompleted, onFallback,
            onClosedWhileIncomplete = {
                // Hops to the main thread: this arrives on Helium's thread, and
                // presenting an Activity requires the main looper.
                org.clear30.Clear30Application.appScope.launch {
                    val resumed = lifecycleOwner.lifecycle.currentState
                        .isAtLeast(Lifecycle.State.RESUMED)
                    PaywallDiag.log("helium close handled; resumed=$resumed")
                    if (resumed) presentation++ else pendingRepresent.set(true)
                }
            },
        )
    }

    // If we are RESUMED, Helium's Activity is by definition not in front. So any
    // resume where the paywall isn't showing and nothing completed means the user
    // is staring at the loading scrim — re-present. Helium can't start an Activity
    // from the background, which is why this is tied to resume rather than to the
    // close event itself.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        PaywallDiag.log(
            "helium ON_RESUME showing=${showing.get()} completed=${completed.get()} " +
                "pending=${pendingRepresent.get()}",
        )
        if (completed.get()) return@LifecycleEventEffect
        if (pendingRepresent.getAndSet(false) || (hasOpened.get() && !showing.get())) {
            PaywallDiag.log("helium paywall not showing after resume — re-presenting")
            presentation++
        }
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
    completed: java.util.concurrent.atomic.AtomicBoolean,
    showing: java.util.concurrent.atomic.AtomicBoolean,
    hasOpened: java.util.concurrent.atomic.AtomicBoolean,
    onCompleted: (EntitlementType?) -> Unit,
    onFallback: () -> Unit,
    onClosedWhileIncomplete: () -> Unit,
) {
    // Trigger mirrors the native path's placement (Paywall.kt). The life/advanced
    // variants are dashboard-configurable refinements once program state is threaded.
    val trigger = if (popup) PaywallTrigger.CLEAR30_POPUP.raw else PaywallTrigger.CLEAR30_ONBOARDING.raw

    // Helium resolves each paywall button's product against Google Play itself
    // (RevenueCatDelegate.makePurchase takes a Billing ProductDetails), so a button
    // pointing at an id Play can't sell fails silently. Print what Play CAN sell
    // now, while the paywall is loading, so the comparison is in the same log.
    PaywallDiag.auditStore(org.clear30.Clear30Application.appScope)

    // Attach the RevenueCat purchase bridge (its constructor requires an Activity,
    // so it's created per-presentation rather than at global init).
    runCatching { Helium.config.heliumPaywallDelegate = RevenueCatDelegate(activity) }
        .onSuccess { PaywallDiag.log("helium RevenueCat purchase delegate attached; trigger=$trigger") }
        .onFailure {
            android.util.Log.w("Paywall", "Helium RC delegate failed: ${it.message}")
            PaywallDiag.warn(
                "helium BLOCKER: the RevenueCat purchase delegate failed to attach " +
                    "(${it.message}). With no delegate, paywall buttons cannot purchase anything.",
            )
        }

    val traits = userInfo?.let { PaywallController.heliumTraits(PaywallController.getUserParams(it)) }

    // Resolve the active entitlement (RevenueCat has it post-purchase/restore) and
    // finish onboarding — iOS handlePaid → completed(entitlement).
    val complete: () -> Unit = {
        completed.set(true)
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
        onDismissed = { if (!hard && !completed.getAndSet(true)) onCompleted(null) },
        onOpenFailed = { onFallback() },
        // PaywallClose fires whenever Helium's Activity goes away — including a
        // system teardown while backgrounded, which is NOT a user dismissal. A soft
        // paywall treats it as one; a hard paywall must come back, or the user is
        // stranded on the loading scrim with no way forward.
        onClose = {
            showing.set(false)
            PaywallDiag.log("helium onClose fired; hard=$hard completed=${completed.get()}")
            // A soft paywall treats a close as dismissal. A hard one must not:
            // this also fires on a system teardown, and the ON_RESUME check in
            // [HeliumPaywall] brings it back rather than stranding the user.
            if (!hard) {
                if (!completed.getAndSet(true)) onCompleted(null)
            } else if (!completed.get()) {
                onClosedWhileIncomplete()
            }
        },
        onOpen = {
            showing.set(true)
            hasOpened.set(true)
            PaywallDiag.log("helium onOpen fired — paywall Activity is up")
        },
        onAnyEvent = { event ->
            logHeliumEvent(event)
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
                PaywallDiag.log("helium paywall NOT shown reason=$reason")
                when (reason) {
                    is PaywallNotShownReason.AlreadyEntitled -> complete()
                    else -> onFallback() // targeting holdout / error → native paywall
                }
            },
        )
    }.onFailure {
        android.util.Log.w("Paywall", "Helium present failed: ${it.message}")
        PaywallDiag.warn("helium present threw: ${it.message} — falling back to the native paywall")
        onFallback()
    }
}

/**
 * One line per Helium paywall event (PAYWALL_DIAG only). This is the log that
 * answers "I tapped Start Now and nothing happened": a tap emits
 * [PurchasedPressed] with the product id the button is wired to, and the absence
 * of a following Succeeded/Failed/Cancelled is what [PaywallDiag]'s watchdog
 * turns into a diagnosis.
 */
private fun logHeliumEvent(event: PaywallContextEvent) {
    if (!PaywallDiag.enabled) return
    // The events are data classes, so toString() is a complete fallback dump.
    when (event) {
        is PurchasedPressed ->
            PaywallDiag.purchasePressed(org.clear30.Clear30Application.appScope, event.productId)
        is PurchaseSucceeded ->
            PaywallDiag.purchaseResolved("SUCCEEDED", event.productId, "processor=${event.paymentProcessor} order=${event.orderId}")
        is PurchaseFailed ->
            PaywallDiag.purchaseResolved("FAILED", event.productId, "error=${event.error}")
        is PurchaseCancelled ->
            PaywallDiag.purchaseResolved("CANCELLED", event.productId)
        is PurchasePending ->
            PaywallDiag.purchaseResolved("PENDING (Play is awaiting approval)", event.productId)
        is ProductSelected ->
            PaywallDiag.log("helium ProductSelected productId=${event.productId}")
        is PaywallOpenFailed ->
            PaywallDiag.warn("helium PaywallOpenFailed reason=${event.paywallUnavailableReason} error=${event.error}")
        else -> PaywallDiag.log("helium $event")
    }
}

/** Unwrap the Compose [Context] to the hosting [Activity] (Helium/Play need it). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

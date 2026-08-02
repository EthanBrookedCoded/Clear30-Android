package org.clear30.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.awaitGetProducts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.BuildConfig

/**
 * PaywallDiag — first-run purchase diagnostics.
 *
 * The purchase path spans four systems (Google Play Billing → Helium's WebView
 * paywall → Helium's RevenueCatDelegate → RevenueCat entitlements) and every one
 * of them fails *silently*: a paywall button whose product Play cannot sell just
 * does nothing when tapped. This object turns each of those silences into one
 * loud, self-explaining logcat line.
 *
 * All output is tagged [TAG]; gated on `BuildConfig.PAYWALL_DIAG` (local.properties,
 * default on). Logging only — it never changes purchase behaviour.
 */
object PaywallDiag {

    const val TAG = "PaywallDiag"

    /** How long a tapped purchase may stay silent before we call it stalled. */
    private const val PURCHASE_WATCHDOG_MS = 8_000L

    val enabled: Boolean get() = BuildConfig.PAYWALL_DIAG

    /** Play product ids this device can actually buy, learned from [auditStore]. */
    private val playProductIds = mutableListOf<String>()

    /** Product id of a tap we have not yet seen resolve (drives the watchdog). */
    @Volatile private var pendingPurchaseProductId: String? = null

    fun log(message: String) { if (enabled) Log.i(TAG, message) }
    fun warn(message: String) { if (enabled) Log.w(TAG, message) }

    /**
     * One-shot environment dump — call from Application.onCreate. Catches the two
     * setup mistakes that make every purchase button a no-op before any store
     * call is even attempted.
     */
    fun dumpEnvironment(context: Context) {
        if (!enabled) return
        val pkg = context.packageName
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(pkg).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(pkg)
            }
        }.getOrNull()

        log("env package=$pkg installer=${installer ?: "none (sideloaded)"}")
        log(
            "env revenueCatKey=${BuildConfig.REVENUECAT_API_KEY.take(5).ifBlank { "<BLANK>" }}… " +
                "heliumKey=${if (BuildConfig.HELIUM_API_KEY.isBlank()) "<BLANK>" else "set"} " +
                "supabase=${if (BuildConfig.SUPABASE_LOCAL) "LOCAL" else "PROD"}",
        )

        if (pkg.endsWith(".debug")) {
            warn(
                "env BLOCKER: '$pkg' is the debug variant. Google Play has no record of that " +
                    "package, so Billing returns ZERO products and every purchase button is a " +
                    "silent no-op. Purchases can only be tested from a release build installed " +
                    "through Play.",
            )
        }
        if (installer != "com.android.vending") {
            // Google documents that a license tester MAY sideload a package-name-matching
            // build and bypass the upload check — but that bypass was observed NOT to work
            // on this project's test device (Billing returned SERVICE_UNAVAILABLE for both
            // Helium and RevenueCat), so treat it as a strong warning rather than a rule.
            warn(
                "env WARNING: this build was not installed by Google Play " +
                    "(installer=${installer ?: "none"}). Billing may still work if this Google " +
                    "account is a license tester and the package name matches the published app, " +
                    "but a Play-installed build is the reliable path. Prefer Play Console → " +
                    "Internal app sharing.",
            )
        }
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) {
            warn("env BLOCKER: REVENUECAT_API_KEY is blank — PaywallController no-ops every purchase.")
        }
    }

    /**
     * Ask RevenueCat what this device can actually sell and print it. The listed
     * ids are the ONLY strings a paywall button may reference; anything else
     * (typically an App Store id carried over from iOS) cannot open a purchase
     * sheet. Safe to call repeatedly.
     */
    fun auditStore(scope: CoroutineScope) {
        if (!enabled) return
        scope.launch {
            val offering = PaywallController.currentOffering()
            dumpOffering(offering)
            // Helium runs its OWN BillingClient, independent of RevenueCat's. Probing
            // Play for the same bare subscription id at the moment Helium is resolving
            // it separates "Play isn't answering right now" from "Play answers
            // RevenueCat but not Helium" — the two cases have completely different fixes.
            offering?.availablePackages
                ?.map { it.product.id.substringBefore(':') }
                ?.distinct()
                ?.forEach { probePlayProduct(it) }
        }
    }

    /** Ask Play directly for [productId] (the bare subscription id Helium queries). */
    private suspend fun probePlayProduct(productId: String) {
        runCatching { Purchases.sharedInstance.awaitGetProducts(listOf(productId)) }
            .onSuccess { products ->
                if (products.isEmpty()) {
                    warn(
                        "probe Play returned NO product for bare id '$productId' at paywall time. " +
                            "Helium queries this exact id, so it will render without a price.",
                    )
                } else {
                    products.forEach {
                        log("probe Play CAN sell '$productId' right now → id=${it.id} price=${it.price.formatted}")
                    }
                }
            }
            .onFailure { warn("probe Play query for '$productId' failed: ${it.message}") }
    }

    /** Print the current offering and cache its Play product ids. */
    fun dumpOffering(offering: Offering?) {
        if (!enabled) return
        if (offering == null) {
            warn(
                "store BLOCKER: RevenueCat returned NO current offering. Either the fetch failed " +
                    "(see the preceding Purchases/BillingClient lines), the API key is wrong, or " +
                    "no offering is marked current in the RevenueCat dashboard.",
            )
            return
        }

        val packages = offering.availablePackages
        log("store offering=${offering.identifier} purchasablePackages=${packages.size}")
        packages.forEach { pkg ->
            log(
                "store  · package=${pkg.identifier} playProductId=${pkg.product.id} " +
                    "price=${pkg.product.price.formatted} type=${pkg.packageType}",
            )
        }

        playProductIds.clear()
        playProductIds.addAll(packages.map { it.product.id })

        if (packages.isEmpty()) {
            warn(
                "store BLOCKER: the current offering has no packages Google Play can sell on this " +
                    "device, so every purchase button is a silent no-op. Causes, in order of " +
                    "likelihood: (1) debug/sideloaded build — see the env lines above; (2) no Play " +
                    "product attached to this package in the RevenueCat offering; (3) the Play base " +
                    "plan is not ACTIVE; (4) the product is not available in this country.",
            )
        } else {
            log("store ^ those playProductId values are the only ids this device can purchase.")
        }
    }

    /** RevenueCat's own verbose logging — the Billing response codes live here. */
    fun enableRevenueCatDebugLogging() {
        if (!enabled) return
        runCatching { Purchases.logLevel = LogLevel.DEBUG }
    }

    /** Log the RevenueCat identity a purchase will be attributed to. */
    fun dumpIdentity(label: String) {
        if (!enabled) return
        val appUserId = runCatching {
            if (Purchases.isConfigured) Purchases.sharedInstance.appUserID else "<not configured>"
        }.getOrElse { "<error: ${it.message}>" }
        log("identity $label appUserID=$appUserId")
    }

    // MARK: - Purchase watchdog

    /**
     * Record a purchase tap. If nothing resolves it within [PURCHASE_WATCHDOG_MS],
     * [diagnoseStalledPurchase] explains the silence — this is the case where the
     * user taps "Start Now" and simply nothing happens.
     *
     * [scope] must outlive composition (use `Clear30Application.appScope`): the Play
     * sheet is a separate Activity, so a composition-scoped job can be cancelled
     * before the watchdog fires.
     */
    fun purchasePressed(scope: CoroutineScope, productId: String) {
        if (!enabled) return
        pendingPurchaseProductId = productId
        log("purchase ▶ tapped productId=$productId — waiting for Play to open a purchase sheet")
        scope.launch {
            delay(PURCHASE_WATCHDOG_MS)
            if (pendingPurchaseProductId == productId) diagnoseStalledPurchase(productId)
        }
    }

    /** A purchase produced a terminal outcome — cancel the watchdog. */
    fun purchaseResolved(outcome: String, productId: String?, detail: String? = null) {
        if (!enabled) return
        pendingPurchaseProductId = null
        val suffix = detail?.let { " detail=$it" }.orEmpty()
        log("purchase ■ $outcome productId=${productId ?: "?"}$suffix")
    }

    private fun diagnoseStalledPurchase(productId: String) {
        warn(
            "purchase STALLED: tapping productId=$productId produced no success/failure/cancel " +
                "within ${PURCHASE_WATCHDOG_MS / 1000}s and no Play purchase sheet appeared.",
        )
        val known = playProductIds.toList()
        when {
            known.isEmpty() -> warn(
                "purchase   → Google Play returned no purchasable products for this build at all, " +
                    "so Helium had nothing to buy. Fix the build/store setup first (see the env " +
                    "and store BLOCKER lines above).",
            )
            known.none { it.matchesProductId(productId) } -> warn(
                "purchase   → '$productId' is NOT one of the ids Play can sell here: $known. The " +
                    "paywall button is configured with a product this device cannot buy — almost " +
                    "always an App Store id left over from iOS. Point that button at the Play id " +
                    "(note Play subscription ids are 'subscriptionId:basePlanId').",
            )
            else -> warn(
                "purchase   → '$productId' IS purchasable per Play, so the block is downstream. " +
                    "Check the Purchases/BillingClient lines just above for a Billing response " +
                    "code (ITEM_UNAVAILABLE, DEVELOPER_ERROR, SERVICE_DISCONNECTED).",
            )
        }
    }

    /**
     * Play subscription ids are `subscriptionId:basePlanId`, but a paywall is
     * usually configured with the bare subscription id — treat those as equal.
     */
    private fun String.matchesProductId(other: String): Boolean =
        this == other || substringBefore(':') == other.substringBefore(':')
}

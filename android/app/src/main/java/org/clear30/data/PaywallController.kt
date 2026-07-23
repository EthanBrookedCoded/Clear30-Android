package org.clear30.data

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PeriodType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitGetProducts
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.awaitSyncPurchases
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.logOutWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.clear30.BuildConfig
import org.clear30.data.model.EntitlementType
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.SignUpType
import org.clear30.data.model.UserInfo
import org.clear30.data.model.toDictionary
import org.clear30.util.justDay

/**
 * PaywallController — ported from PaywallController.swift.
 *
 * iOS stacked RevenueCat + Helium + StoreKit + Stripe Apple Pay. On Android:
 *  - RevenueCat -> com.revenuecat.purchases (Google Play Billing under the hood)
 *  - Helium -> Helium Android SDK (different API; wired in the paywall-views segment)
 *  - StoreKit Message.messages -> no Android analog (Play/RevenueCat surface
 *    billing issues themselves), so [listenForStoreKitMessages] is a no-op
 *  - Apple Pay -> not applicable
 *
 * The Swift original is kept for reference until the paywall views are built.
 */
object PaywallController {

    private val _currentPaywallID = MutableStateFlow<String?>(null)
    val currentPaywallID: StateFlow<String?> = _currentPaywallID.asStateFlow()

    private val _event = MutableStateFlow<PaywallEvent?>(null)
    val event: StateFlow<PaywallEvent?> = _event.asStateFlow()

    private val _externalTriggerOverride = MutableStateFlow<String?>(null)
    val externalTriggerOverride: StateFlow<String?> = _externalTriggerOverride.asStateFlow()

    /** iOS sets `currentPaywallID` when a paywall renders and nils it on
     *  disappear (Paywall.onDisappear) — `isHardPaywall` keys off it. */
    fun setCurrentPaywallID(id: String?) { _currentPaywallID.value = id }

    /** iOS Paywall.onDisappear — clear the render-scoped paywall state. */
    fun clearPaywallPresentation() {
        _currentPaywallID.value = null
        _externalTriggerOverride.value = null
    }

    /** RevenueCat init — call from Application.onCreate (Swift `initRevenueCat`). */
    fun initRevenueCat(context: Context) {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return
        Purchases.configure(
            PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_API_KEY).build()
        )
    }

    /**
     * True when a Helium API key is configured. When false the app uses the native
     * RevenueCat paywall (`Paywall.kt`), exactly like a blank RevenueCat key no-ops
     * purchases — so dev builds (both keys blank) keep the existing behavior.
     */
    fun heliumEnabled(): Boolean = BuildConfig.HELIUM_API_KEY.isNotBlank()

    /**
     * Boot the Helium SDK — call from Application.onCreate AFTER [initRevenueCat]
     * (iOS `initHelium` runs in the AppDelegate / ContentView after RC configure).
     * No-op without a key. The per-paywall RevenueCat purchase bridge
     * (`RevenueCatDelegate`) is attached at present time in `HeliumPaywall`, where an
     * Activity is available (its constructor requires one).
     */
    fun initHeliumSDK(context: Context) {
        if (!heliumEnabled()) return
        runCatching {
            com.tryhelium.paywall.core.Helium.initialize(
                context,
                BuildConfig.HELIUM_API_KEY,
                // Helium's config environment (matches iOS, which ships production).
                com.tryhelium.paywall.core.HeliumEnvironment.PRODUCTION,
            )
        }.onFailure { android.util.Log.w("Paywall", "Helium init failed: ${it.message}") }
    }

    /**
     * Identify the user with RevenueCat and push subscriber attributes used by
     * targeting / paywalls (Swift `signIn` → logIn + attribute setters; the iOS
     * per-field setters fold into the Android `setAttributes(map)`).
     */
    fun signIn(userInfo: UserInfo, userProperties: Map<String, Any> = emptyMap()) {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return
        val purchases = Purchases.sharedInstance
        val signUpID = userInfo.signUpID
        val signUpType = userInfo.signUpType
        purchases.logInWith(
            userInfo.userID,
            onError = { },
            onSuccess = { _, _ ->
                runCatching {
                    purchases.setDisplayName(userInfo.name)
                    when (signUpType) {
                        SignUpType.PHONE -> purchases.setPhoneNumber(signUpID)
                        // Apple sign-in can hand back a nil/placeholder email for
                        // returning users — only forward real-looking addresses.
                        SignUpType.EMAIL, SignUpType.APPLE ->
                            if (signUpID.contains("@")) purchases.setEmail(signUpID)
                    }
                    // Android has no setAmplitudeUserID — the reserved attribute
                    // key feeds the same RevenueCat → Amplitude integration.
                    purchases.setAttributes(mapOf("\$amplitudeUserId" to userInfo.loggingID))
                    AttributionHandler.getAppStackID()?.let {
                        purchases.setAttributes(mapOf("appStackID" to it))
                    }
                }
            },
        )
        // Push the paywall-targeting traits (iOS forwards them to Helium; Android's
        // trait vehicle is the RC attribute store). Base params guarantee the
        // standard keys even for a partial caller map; the caller's
        // userProperties — which carry the assessment-response traits — win.
        val merged = getUserParams(userInfo).also { it.putAll(userProperties) }
        updateUserAttributes(userInfo, merged)
    }

    fun signOut(context: Context? = null) {
        // logOut resets RevenueCat to an anonymous user (Swift `signOut`). The
        // Android SDK's callback overload is wrapped by the logOutWith extension.
        if (BuildConfig.REVENUECAT_API_KEY.isNotBlank()) {
            runCatching { Purchases.sharedInstance.logOutWith(onError = { }, onSuccess = { }) }
        }
        // Strip Stripe-managed-sub shortcut (iOS removed this on sign-out so
        // the next account doesn't inherit the old user's billing entry).
        context?.let { ShortcutHandler.removeQuickAction(it, ShortcutHandler.ID_MANAGE_SUB_STRIPE) }
    }

    /**
     * Identify the user to Helium and push the paywall-targeting traits (iOS
     * `ContentView.initHelium` re-derives `getUserParams`). Helium's RevenueCat
     * bridge keys off `revenueCatAppUserId` = the Supabase userID (the same
     * appUserID [signIn] logs into RevenueCat). No-op without a Helium key — the
     * traits still reach RevenueCat as subscriber attributes via [signIn].
     */
    fun initHelium(userInfo: UserInfo, userProperties: Map<String, Any>, onInitialized: (() -> Unit)? = null) {
        if (!heliumEnabled()) { onInitialized?.invoke(); return }
        runCatching {
            val identity = com.tryhelium.paywall.core.Helium.identity
            identity.userId = userInfo.loggingID
            identity.revenueCatAppUserId = userInfo.userID
            val merged = getUserParams(userInfo).also { it.putAll(userProperties) }
            identity.setUserTraits(heliumTraits(merged))
        }.onFailure { android.util.Log.w("Paywall", "Helium identify failed: ${it.message}") }
        onInitialized?.invoke()
    }

    /** Map the [getUserParams] trait bag to Helium's typed trait arguments. */
    internal fun heliumTraits(params: Map<String, Any>): com.tryhelium.paywall.core.HeliumUserTraits {
        val traits = params.mapValues { (_, v) ->
            when (v) {
                is Boolean -> com.tryhelium.paywall.core.HeliumUserTraitsArgument.BooleanParam(v)
                is Int -> com.tryhelium.paywall.core.HeliumUserTraitsArgument.IntParam(v)
                is Long -> com.tryhelium.paywall.core.HeliumUserTraitsArgument.LongParam(v)
                is Double -> com.tryhelium.paywall.core.HeliumUserTraitsArgument.DoubleParam(v)
                else -> com.tryhelium.paywall.core.HeliumUserTraitsArgument.StringParam(v.toString())
            }
        }
        return com.tryhelium.paywall.core.HeliumUserTraits(traits)
    }

    /** No-op on Android — Google Play / RevenueCat surface billing messages. */
    suspend fun listenForStoreKitMessages() { /* iOS StoreKit Message.messages only */ }

    // MARK: - Offerings / purchase / restore / entitlement (RevenueCat 8.x coroutines)

    /**
     * The current RevenueCat offering (`Default`, packages `$rc_annual` /
     * `$rc_monthly`). Null when RevenueCat isn't configured (no API key) or the
     * fetch fails — the paywall then shows a retryable error in release builds.
     */
    suspend fun currentOffering(): Offering? {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return null
        return runCatching { Purchases.sharedInstance.awaitOfferings().current }
            .onFailure { android.util.Log.w("Paywall", "offerings failed: ${it.message}") }
            .getOrNull()
    }

    /**
     * Launch the Google Play purchase flow for [pkg]. Returns the resolved
     * [EntitlementType] on success, or null on cancel/failure. Requires the current
     * [Activity] (Play Billing launches its sheet from an Activity).
     */
    suspend fun purchase(activity: Activity, pkg: Package): EntitlementType? {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return null
        return runCatching {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            _event.value = PaywallEvent.USER_SUBSCRIBED
            entitlementFrom(result.customerInfo) ?: EntitlementType.DEFAULT
        }.getOrElse { e ->
            if (e is PurchasesException && e.error.code == PurchasesErrorCode.PurchaseCancelledError) {
                _event.value = PaywallEvent.USER_CANCELED
            } else {
                android.util.Log.w("Paywall", "purchase failed: ${e.message}")
            }
            null
        }
    }

    /** Restore prior purchases (Swift `restore`). Returns the active entitlement, if any. */
    suspend fun restore(): EntitlementType? {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return null
        return runCatching {
            val info = Purchases.sharedInstance.awaitRestore()
            _event.value = PaywallEvent.USER_RESTORED
            entitlementFrom(info)
        }.onFailure { android.util.Log.w("Paywall", "restore failed: ${it.message}") }.getOrNull()
    }

    /**
     * The user's currently-active paid entitlement, or null (Swift
     * `getCurrentEntitlement`). With a [userInfo], also honours the
     * `bypassPaidUntil` bridge (Stripe purchases reach RevenueCat with a delay)
     * and tracks trial → paid conversion for analytics.
     */
    suspend fun activeEntitlement(
        userInfo: UserInfo? = null,
        bypassEntitlement: EntitlementType? = null,
    ): EntitlementType? {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return null
        if (userInfo?.bypassPaidUntil?.let { it > org.clear30.util.now() } == true) {
            return bypassEntitlement ?: userInfo.currentEntitlementType ?: EntitlementType.DEFAULT
        }
        return try {
            val info = Purchases.sharedInstance.awaitSyncPurchases()
            if (userInfo != null) checkTrialStatus(userInfo, info)
            entitlementFrom(info)
                ?: entitlementFrom(Purchases.sharedInstance.awaitCustomerInfo())
        } catch (e: Exception) {
            android.util.Log.w("Paywall", "entitlement check failed: ${e.message}")
            // Preserve a previously confirmed entitlement while offline, but do
            // not grant paid access merely because a user record exists.
            userInfo?.currentEntitlementType
        }
    }

    /**
     * iOS `checkEntitlementChanged` — refresh the entitlement from RevenueCat and
     * report whether it changed. Free-code users always keep their access.
     * Mutates [userInfo].currentEntitlementType (caller persists).
     */
    suspend fun checkEntitlementChanged(userInfo: UserInfo): Pair<Boolean, EntitlementType?> {
        if (userInfo.freeCode != null) return false to null
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return false to null

        // Expire a scheduled downgrade (setPaidFalseOn) before comparing.
        val setPaidFalseOn = userInfo.setPaidFalseOn
        if (userInfo.currentEntitlementType != null && setPaidFalseOn != null &&
            setPaidFalseOn.justDay <= org.clear30.util.now().justDay
        ) {
            userInfo.currentEntitlementType = null
            userInfo.setPaidFalseOn = null
        }

        val initial = userInfo.currentEntitlementType
        val new = activeEntitlement(userInfo)
        userInfo.currentEntitlementType = new
        return if (initial == new) false to null else true to new
    }

    /** Map RevenueCat's active entitlements (`Plus` / `Core`) to our [EntitlementType]. */
    private fun entitlementFrom(info: CustomerInfo): EntitlementType? {
        val active = info.entitlements.active.keys
        return when {
            active.any { it.equals("Plus", true) } -> EntitlementType.PLUS
            active.any { it.equals("Core", true) } -> EntitlementType.CORE
            else -> null
        }
    }

    // MARK: - Trial conversion detection (Swift checkTrialStatus / logTrialConversion)

    private suspend fun checkTrialStatus(userInfo: UserInfo, info: CustomerInfo) {
        val entitlement = info.entitlements.active.values.firstOrNull { it.isActive } ?: return
        val onTrial = entitlement.periodType == PeriodType.TRIAL || entitlement.periodType == PeriodType.INTRO
        if (onTrial && userInfo.wasOnTrial != true) userInfo.wasOnTrial = true
        if (entitlement.periodType == PeriodType.NORMAL &&
            userInfo.wasOnTrial == true && userInfo.trialConversionLogged != true
        ) {
            logTrialConversion(userInfo, entitlement.productIdentifier)
        }
    }

    private suspend fun logTrialConversion(userInfo: UserInfo, productId: String) {
        val product = runCatching {
            Purchases.sharedInstance.awaitGetProducts(listOf(productId)).firstOrNull()
        }.getOrNull() ?: return
        val price = product.price.amountMicros / 1_000_000.0
        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.trialConverted,
            mapOf(
                LogEventExtraDataType.REVENUE to String.format(java.util.Locale.US, "%.2f", price),
                LogEventExtraDataType.CURRENCY to product.price.currencyCode,
                LogEventExtraDataType.TYPE to productId,
            ),
        )
        userInfo.trialConversionLogged = true
    }

    /**
     * Paywall-targeting user traits (Swift static `getUserParams(userInfo:assessmentResponses:)`).
     * On iOS these become Helium traits; on Android they reach RevenueCat as
     * subscriber attributes via [signIn] / [updateUserAttributes]. The
     * assessment answers are expanded per-option to `"<strippedPrompt>-<i>"`
     * (raw options) and `"<strippedPrompt>-display-<i>"` (displayed options),
     * exactly as iOS does.
     */
    fun getUserParams(
        userInfo: UserInfo,
        assessmentResponses: List<ProgramAssessmentResponse> = emptyList(),
    ): MutableMap<String, Any> = buildMap {
        put("Name", userInfo.name)
        put("random_int_num", (kotlin.math.abs(userInfo.userID.hashCode()) % 100) + 1)
        put("apple_pay_enabled", false) // iOS-only (StripeHandler.applePayEnabled)
        put("QA", false) // iOS `Paywall.QA` compile-time flag
        put("rc_entitlement", userInfo.currentEntitlementType?.rawValue ?: "NA")
        userInfo.signUpReturning?.let { put("returning_user", it) }
        userInfo.referralCode?.let { put("referral_code", it) }

        // Assessment responses (raw options): "id-0", "id-1", ...
        assessmentResponses.toDictionary(displayOptions = false).forEach { (key, values) ->
            values.forEachIndexed { index, value -> put("$key-$index", value) }
        }
        // Assessment responses (display options): "id-display-0", "id-display-1", ...
        assessmentResponses.toDictionary(displayOptions = true).forEach { (key, values) ->
            values.forEachIndexed { index, value -> put("$key-display-$index", value) }
        }
    }.toMutableMap()

    /**
     * Re-push the paywall-targeting traits as RevenueCat subscriber attributes.
     * iOS refreshes these by re-running `initHelium` with fresh `getUserParams`
     * (e.g. after a referral code is applied); Android's trait vehicle is the
     * RC attribute store, so this is the equivalent. No-op without an API key.
     */
    fun updateUserAttributes(userInfo: UserInfo, userProperties: Map<String, Any>) {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return
        val attrs = mutableMapOf<String, String?>("Name" to userInfo.name)
        userProperties.forEach { (k, v) -> attrs[k] = v.toString() }
        runCatching { Purchases.sharedInstance.setAttributes(attrs) }
    }
}

/** PaywallEvent — ported 1:1. */
enum class PaywallEvent(val raw: String) {
    USER_CANCELED("userCanceled"),
    USER_DISMISSED("userDismissed"),
    USER_SUBSCRIBED("userSubscribed"),
    USER_RESTORED("userRestored"),
}

/** PaywallTrigger — ported 1:1. */
enum class PaywallTrigger(val raw: String) {
    PRO_TO_CORE("pro_to_core"),
    DEFAULT_TRIGGER("default_trigger"),
    CLEAR30_ONBOARDING("clear30_onboarding"),
    LIFE_ONBOARDING("life_onboarding"),
    CLEAR30_POPUP("clear30_popup"),
    LIFE_POPUP("life_popup"),
    CORE_TO_PRO("core_to_pro"),
    TEST("test"),
    ADVANCED_MODE("advanced_mode"),
}

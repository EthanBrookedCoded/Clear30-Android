package org.clear30.data

import android.content.Context
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.clear30.BuildConfig
import org.clear30.data.model.UserInfo

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

    /** RevenueCat init — call from Application.onCreate (Swift `initRevenueCat`). */
    fun initRevenueCat(context: Context) {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank()) return
        Purchases.configure(
            PurchasesConfiguration.Builder(context, BuildConfig.REVENUECAT_API_KEY).build()
        )
    }

    fun signIn(userInfo: UserInfo, userProperties: Map<String, Any> = emptyMap()) {
        Purchases.sharedInstance.logIn(userInfo.userID, onError = { }, onSuccess = { _, _ ->
            Purchases.sharedInstance.apply {
                setDisplayName(userInfo.name)
                when (userInfo.signUpType) {
                    org.clear30.data.model.SignUpType.PHONE -> setPhoneNumber(userInfo.signUpID)
                    else -> if (userInfo.signUpID.contains("@")) setEmail(userInfo.signUpID)
                }
                // Amplitude / AppStack attribution -> see AttributionHandler
            }
            // TODO(port): Helium login (Helium Android SDK)
        })
    }

    fun signOut(context: Context? = null) {
        Purchases.sharedInstance.logOut()
        // Strip Stripe-managed-sub shortcut (iOS removed this on sign-out so
        // the next account doesn't inherit the old user's billing entry).
        context?.let { ShortcutHandler.removeQuickAction(it, ShortcutHandler.ID_MANAGE_SUB_STRIPE) }
    }

    /** TODO(port): Helium init (Android SDK API differs from iOS). */
    fun initHelium(userInfo: UserInfo, userProperties: Map<String, Any>, onInitialized: (() -> Unit)? = null) {
        onInitialized?.invoke()
    }

    /** No-op on Android — Google Play / RevenueCat surface billing messages. */
    suspend fun listenForStoreKitMessages() { /* iOS StoreKit Message.messages only */ }

    /**
     * Helium / paywall user traits (Swift `getUserParams`). Assessment-response
     * fields are added once the assessment engine is ported (they need
     * ProgramAssessmentResponse.toDictionary).
     */
    fun getUserParams(userInfo: UserInfo): MutableMap<String, Any> = buildMap {
        put("Name", userInfo.name)
        put("random_int_num", (kotlin.math.abs(userInfo.userID.hashCode()) % 100) + 1)
        put("apple_pay_enabled", false) // iOS-only
        put("rc_entitlement", userInfo.currentEntitlementType?.name ?: "NA")
        userInfo.signUpReturning?.let { put("returning_user", it) }
        userInfo.referralCode?.let { put("referral_code", it) }
        // TODO(port): assessment-response options/display-options key expansion
    }.toMutableMap()
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

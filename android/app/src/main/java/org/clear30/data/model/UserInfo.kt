package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.isSameDay
import org.clear30.util.isToday
import org.clear30.util.now

/**
 * UserInfo — ported from UserInfo.swift (`@Model class UserInfo`).
 *
 * SwiftData stored this as the single UserInfo row, mutated in place and saved
 * via `context.save()`. Here it's a mutable @Serializable class persisted as
 * JSON by [org.clear30.data.LocalStore] under [STORE_KEY]; computed `@Model`
 * properties become Kotlin computed `val`s. @SerialName preserves the original
 * SwiftData `originalName` attributes so existing payloads keep decoding.
 */
@Serializable
class UserInfo(
    @SerialName("supabaseUserID") var _userID: String? = "",
    @SerialName("phoneNumber") var signUpID: String = "",
    var _signUpType: SignUpType? = SignUpType.PHONE,
    var signUpReturning: Boolean? = false,
    var _loggingID: String? = "",

    // General
    var name: String = "",
    var emoji: String? = "😁",
    var firstAppOpen: Instant = now(),
    var fcmToken: String? = null,
    var sessions: MutableList<Instant> = mutableListOf(),
    var userWhy: String? = null,
    var cache: MutableMap<String, String> = mutableMapOf(),

    // Payment
    var referralCode: String? = null,
    var freeCode: String? = null,
    var currentEntitlementType: EntitlementType? = null,
    var paywallHard: Boolean = false,
    var setPaidFalseOn: Instant? = null,
    var bypassPaidUntil: Instant? = null,

    // Trial tracking
    var wasOnTrial: Boolean? = false,
    var trialConversionLogged: Boolean? = false,

    // Notification settings
    var notificationSettings: ToggleSettings? = null,
    var sentCheckInNotificationIDs: MutableList<Int> = mutableListOf(),
    var checkInNotificationHour: Int? = null,   // 0-23, null = Automatic
    var mostRecentPopInNotificationDate: Instant = now(),

    // SMS settings
    var smsSettings: ToggleSettings? = null,

    // Peer Support
    var peerSupportMigrated: Boolean? = false,

    // Onboarding
    var completedOnboarding: Boolean? = null,
    var agreedToClaire: Boolean? = null,
    // Claire chat thread id (claire_handle_threads) — created lazily on first chat.
    var claireThreadID: String? = null,
    var requestedReview: Boolean? = null,
    var agreedToTerms: Boolean? = null,
    // Feedback Monster: once the user submits feedback the monster is "full" (smiles).
    var gaveFeedback: Boolean? = null,

    // Groups
    var groupID: String? = null,

    // Modes
    var mode: AppMode? = null,
    var schoolData: SchoolData? = null,
    var schoolId: String? = null,
    var midPilotAssessmentCompleted: Boolean? = false,

    // Community
    var respondedCommunityPromptIDs: MutableList<Int>? = mutableListOf(),

    // Deprecated
    var paid: Boolean = false,
    var startDate: Instant = now(),
    var longTermStartDate: Instant? = null,
    var longTermModeration: Boolean? = null,
    var longTermDayIDMap: MutableMap<Int, String>? = null,
    var completedPostAssessment: Boolean? = null,
    var accountabilityTextsEnabled: Boolean? = null,
    var smsNumber: String? = null,
    var notificationsEnabled: Boolean? = null,
    var group: Clear30Group? = null,
) {
    // MARK: - Computed
    val userID: String get() = _userID ?: ""
    val signUpType: SignUpType get() = _signUpType ?: SignUpType.PHONE
    val loggingID: String get() = _loggingID ?: ""

    val sessionsForToday: Int get() = sessions.count { it.isSameDay(now()) }
    val previousSessionDate: Instant? get() = sessions.filter { !it.isToday }.lastOrNull()
    val firstSessionOfDay: Boolean get() = sessionsForToday <= 1

    val isPaid: Boolean
        get() = currentEntitlementType != null ||
            freeCode != null ||
            (bypassPaidUntil?.let { it > now() } ?: false)

    val inLongTerm: Boolean
        get() = longTermStartDate != null && startDate.daysTo(now()) > 30

    /** UserInfo.patchUserInfo — migration/cleanup run on load. */
    fun patch() {
        if (_signUpType == null) _signUpType = SignUpType.PHONE

        // iOS migration parity: old installs persisted only `paid=true`.
        // Preserve that confirmed access even before RevenueCat/network refresh.
        if (paid && currentEntitlementType == null) {
            currentEntitlementType = EntitlementType.DEFAULT
        }

        // Push back first app open by 4 days for legacy users
        if ((completedOnboarding == true) && firstAppOpen.adding(seconds = 5) >= now()) {
            firstAppOpen = firstAppOpen.adding(days = -4)
        }

        // Prune sessions older than 30 days to bound array growth
        val cutoff = now().adding(days = -30)
        if (sessions.size > 500 || (sessions.firstOrNull()?.let { it < cutoff } == true)) {
            sessions = sessions.filter { it >= cutoff }.toMutableList()
        }
    }

    // MARK: - Caching (ported from the UserInfo extension)
    fun getCachedBool(key: String, fallback: Boolean = false): Boolean =
        cache[key]?.toBooleanStrictOrNull() ?: fallback

    fun setCacheBool(key: String, value: Boolean) { cache[key] = value.toString() }

    fun getCachedDate(key: String, fallback: Instant = now()): Instant =
        cache[key]?.toDoubleOrNull()
            ?.let { Instant.fromEpochMilliseconds((it * 1000).toLong()) } ?: fallback

    fun setCacheDate(key: String, value: Instant) {
        cache[key] = (value.toEpochMilliseconds() / 1000.0).toString()
    }

    fun getCachedInt(key: String, fallback: Int = 0): Int = cache[key]?.toIntOrNull() ?: fallback
    fun setCacheInt(key: String, value: Int) { cache[key] = value.toString() }

    fun getCachedString(key: String, fallback: String = ""): String = cache[key] ?: fallback
    fun setCacheString(key: String, value: String) { cache[key] = value }

    companion object {
        const val STORE_KEY = "user_info"
    }
}

package org.clear30.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.data.model.ExperimentKey
import org.clear30.data.model.ExperimentOption
import org.clear30.data.supabase.SupabaseController
import org.clear30.util.now
import io.github.jan.supabase.postgrest.postgrest

/**
 * Logger — ported from Logger.swift. Inserts events into the Supabase `events`
 * table and forwards to attribution SDKs.
 *
 * The iOS event taxonomy (`LogEventType`) is hundreds of cases; it's modeled
 * here as a value class wrapping the rawValue, with constants added as call
 * sites are ported (Logger.swift is retained until the full enum is transcribed).
 * Facebook/AppStack mapping moves to AttributionHandler (Android attribution).
 */
@Serializable
data class LogEvent(
    val user_id: String,
    val event: String,
    val extra_data: Map<String, String>? = null,
    val timestamp: Instant = now(),
)

/** Event name — see class note. rawValue matches the iOS enum rawValue exactly. */
@JvmInline
value class LogEventType(val rawValue: String) {
    companion object {
        // App lifecycle
        val openedApp = LogEventType("opened_app")
        val endedSession = LogEventType("ended_session")
        val exposure = LogEventType("exposure")

        // Onboarding
        val openedOnboardingScreen = LogEventType("opened_onboarding_screen")
        val signedUp = LogEventType("signed_up")
        val startedAssessmentQuestion = LogEventType("started_assessment_question")
        val completedAssessmentQuestion = LogEventType("completed_assessment_question")
        val startedAssessmentSlide = LogEventType("started_assessment_slide")
        val completedAssessmentSlide = LogEventType("completed_assessment_slide")
        val completedAssessment = LogEventType("completed_assessment")
        val openedFeedback = LogEventType("opened_feedback")
        val startedWithTracking = LogEventType("started_with_tracking")
        val walkthrough = LogEventType("walkthrough")
        val openedPaywall = LogEventType("opened_paywall")
        val stripePaywallError = LogEventType("stripe_paywall_error")
        val subscribed = LogEventType("subscribed")
        val unsubscribed = LogEventType("unsubscribed")
        val trialConverted = LogEventType("trial_converted")
        val startedFreeMode = LogEventType("started_free_mode")
        val usedReferralCode = LogEventType("used_referral_code")

        // Tabs / content
        val openedHome = LogEventType("opened_home")
        val openedContent = LogEventType("opened_content")
        val openedProfile = LogEventType("opened_profile")
        val openedCommunity = LogEventType("opened_community_tab")
        val focusedMessageCover = LogEventType("focused_message_cover")
        val openedMessage = LogEventType("opened_message")
        val listenedToMeditation = LogEventType("listened_to_meditation")
        val openedRedditThread = LogEventType("opened_reddit_thread")

        // Notification settings (referenced by AppRoot/ContentView)
        val enabledContentNotifications = LogEventType("enabled_content_notifications")
        val enabledCheckInNotifications = LogEventType("enabled_check_in_notifications")
        val enabledPopInNotifications = LogEventType("enabled_pop_in_notifications")
        val enabledCommunityNotifications = LogEventType("enabled_community_notifications")
        val enabledGroupNotifications = LogEventType("enabled_group_notifications")
        val enabledHealthNotifications = LogEventType("enabled_health_notifications")
        val enabledAchievementNotifications = LogEventType("enabled_achievement_notifications")

        // Check-in
        val loggedCheckIn = LogEventType("logged_check_in")
        val smokedCheckIn = LogEventType("smoked_check_in")
        val unloggedCheckIn = LogEventType("unlogged_check_in")
        val openedCheckInRewards = LogEventType("opened_check_in_rewards")

        // Journal
        val createdJournalEntry = LogEventType("created_journal_entry")
        val viewedJournalEntry = LogEventType("viewed_journal_entry")
        val deletedJournalEntry = LogEventType("deleted_journal_entry")

        // Community
        val openedCommunityPost = LogEventType("opened_community_post")
        val createdCommunityPost = LogEventType("created_community_post")
        val reactedToCommunityPost = LogEventType("reacted_to_community_post")
        val commentedOnCommunityPost = LogEventType("commented_on_community_post")

        // Groups
        val createdGroup = LogEventType("created_group")
        val joinedGroup = LogEventType("joined_group")
        val leftGroup = LogEventType("left_group")
        val openedGroup = LogEventType("opened_group")

        // Chat
        val sentClaireMessage = LogEventType("sent_claire_message")
        val receivedClaireResponse = LogEventType("received_claire_response")
        val sentDrFredMessage = LogEventType("sent_dr_fred_message")
        val receivedDrFredResponse = LogEventType("received_dr_fred_response")

        // Achievements
        val earnedAchievement = LogEventType("earned_achievement")
        val openedAchievement = LogEventType("opened_achievement")
        val viewedAchievementsList = LogEventType("viewed_achievements_list")

        // Sales / sales-slides
        val allowedNotifications = LogEventType("allowed_notifications")
        val deniedNotifications = LogEventType("denied_notifications")
        val openedReviews = LogEventType("opened_reviews")
        val openedReferralCode = LogEventType("opened_referral_code")
        val enteredReferralCode = LogEventType("entered_referral_code")
        val openedCommitment = LogEventType("opened_commitment")

        // Library
        val openedMeditations = LogEventType("opened_meditations")
        val completedMeditation = LogEventType("completed_meditation")
        val openedYouTube = LogEventType("opened_youtube")
        val openedReddit = LogEventType("opened_reddit")
        val openedJournalPrompts = LogEventType("opened_journal_prompts")

        // Profile / settings
        val openedSettings = LogEventType("opened_settings")
        val signedOut = LogEventType("signed_out")
        val deletedAccount = LogEventType("deleted_account")
        val openedHealth = LogEventType("opened_health")
        val openedPreviousBreak = LogEventType("opened_previous_break")
        val sharedCalendar = LogEventType("shared_calendar")

        // Deep links / pushes — fired from URLManager / FCM dispatcher.
        val deepLinkReceived = LogEventType("deep_link_received")
        val pushReceived = LogEventType("push_received")
        val pushOpened = LogEventType("push_opened")

        // Quick actions (long-press app icon) — match iOS ShortcutHandler keys.
        val openedQuickAction = LogEventType("opened_quick_action")

        // Paywall events beyond opened/subscribed (parity with iOS Logger calls).
        val tappedRestore = LogEventType("tapped_restore")
        val restoredPurchases = LogEventType("restored_purchases")
        val cancelledSubscription = LogEventType("cancelled_subscription")
        val billingIssue = LogEventType("billing_issue")
        val openedManageSubscription = LogEventType("opened_manage_subscription")

        // Tutorial system
        val openedTutorialScreen = LogEventType("opened_tutorial_screen")
        val completedTutorial = LogEventType("completed_tutorial")
        val skippedTutorial = LogEventType("skipped_tutorial")

        // Today / check-in surface
        val openedCalendar = LogEventType("opened_calendar")
        val openedDayDetail = LogEventType("opened_day_detail")
        val openedCheckInSheet = LogEventType("opened_check_in_sheet")
        val openedCustomCheckIn = LogEventType("opened_custom_check_in")
        val tappedPopIn = LogEventType("tapped_pop_in")
        val dismissedPopIn = LogEventType("dismissed_pop_in")

        // Symptoms
        val loggedSymptom = LogEventType("logged_symptom")
        val openedSymptoms = LogEventType("opened_symptoms")

        // Media library beyond opened
        val pausedMeditation = LogEventType("paused_meditation")
        val resumedMeditation = LogEventType("resumed_meditation")
        val openedYouTubeVideo = LogEventType("opened_youtube_video")

        // SMS
        val signedUpForSms = LogEventType("signed_up_for_sms")
        val confirmedSms = LogEventType("confirmed_sms")
        val unsubscribedFromSms = LogEventType("unsubscribed_from_sms")

        // Groups depth
        val sharedGroupCode = LogEventType("shared_group_code")
        val openedGroupCalendar = LogEventType("opened_group_calendar")
        val openedGroupMember = LogEventType("opened_group_member")
        val createdGroupNote = LogEventType("created_group_note")

        // Profile depth
        val openedHealthTimeline = LogEventType("opened_health_timeline")
        val changedProgramStartDate = LogEventType("changed_program_start_date")
        val openedDopamineTimer = LogEventType("opened_dopamine_timer")

        // Misc — add more as views call them. Untyped events still work via
        // [Logger.log(loggingID, eventName)] for one-off use.
    }
}

/** Extra-data keys — ported 1:1 from LogEventExtraDataType. */
enum class LogEventExtraDataType(val rawValue: String) {
    HOME_PREVIEW("home_preview"), ASSESSMENT_QUESTION("question"), TYPE("type"),
    RETURNING("returning"), POPUP("pop_up"), PROMPT("prompt"), SYMPTOM("symptom"),
    SYMPTOMS("symptoms"), SYMPTOM_MESSAGE_FREQUENCY("frequency"), FROM("from"),
    MESSAGE_TITLE("message_title"), SEARCH("search"), USER_MESSAGE("user_message"),
    CLAIRE_RESPONSE("claire_message"), FRED_RESPONSE("fred_message"), PEER_RESPONSE("peer_message"),
    URL("url"), PLACEMENT("placement"), MEDITATION_NAME("meditation_name"), SOBER("sober"),
    DATE("date"), FREE_CODE("free_code"), TITLE("title"), EXTRA("extra"), FLAG_KEY("flag_key"),
    VARIANT("variant"), EXPERIMENT_KEY("experiment_key"), TAGS("tags"), ID("id"),
    USER_ID("user_id"), EMAIL("email"), PHONE_NUMBER("phone_number"),
    REVENUE("revenue"), CURRENCY("currency"),
    HEALTH_CATEGORY("health_category"), WAS_ANIMATED("was_animated"),
    ACHIEVEMENT_KEY("achievement_key"), ACHIEVEMENT_NAME("achievement_name"), RARITY("rarity"),
    IS_FIRST_VIEW("is_first_view"), SOURCE("source"), TOTAL_ACHIEVEMENTS("total_achievements"),
    CHECK_IN_METHOD("check_in_method"), CHECK_IN_AMOUNT("check_in_amount"),
    CHECK_IN_COMPLETION("check_in_completion"), REWARD_TYPE("reward_type"),
    CUSTOM_CHECK_IN_ID("custom_check_in_id"), DAY_NUMBER("day_number"),
    SECTION_NAME("section_name"), EXIT_REASON("exit_reason"),
    TUTORIAL_SCREEN("tutorial_screen"), TUTORIAL_PARENT_SCREEN("tutorial_parent_screen"),
    TUTORIAL_SCREENS_COMPLETED("tutorial_screens_completed"),
}

object Logger {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun logEvent(
        loggingID: String,
        event: LogEventType,
        extraData: Map<LogEventExtraDataType, String>? = null,
    ) {
        val extra = extraData?.entries?.associate { it.key.rawValue to it.value }
        scope.launch {
            val supabaseEvent = LogEvent(user_id = loggingID, event = event.rawValue, extra_data = extra)
            runCatching {
                SupabaseController.client.postgrest.from("events").insert(supabaseEvent)
            }.onFailure { println("Failed to log event to Supabase: ${it.message}") }

            // TODO(port): forward to AttributionHandler (Facebook/AppStack mapping)
            //   incl. the openedApp first_launch + signedUp returning->LOGIN special cases.
        }
    }

    /** Convenience matching the CLAUDE.md `Logger.shared.log(eventName:)` style. */
    fun log(loggingID: String, eventName: String, extraData: Map<LogEventExtraDataType, String>? = null) =
        logEvent(loggingID, LogEventType(eventName), extraData)

    fun logExperimentExposure(
        loggingID: String,
        flagKey: ExperimentKey,
        variant: ExperimentOption,
        experimentKey: String,
    ) = logEvent(
        loggingID = loggingID,
        event = LogEventType.exposure,
        extraData = mapOf(
            LogEventExtraDataType.FLAG_KEY to flagKey.rawValue,
            LogEventExtraDataType.VARIANT to variant.rawValue,
            LogEventExtraDataType.EXPERIMENT_KEY to experimentKey,
        ),
    )
}

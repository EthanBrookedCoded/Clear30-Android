package org.clear30.views.newuser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.clear30.data.AssessmentSubmissionHandler
import org.clear30.data.AttributionHandler
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.Logger
import org.clear30.data.model.EntitlementType
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.ExperimentKey
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.Program
import org.clear30.data.model.ToggleSettings
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.isHardPaywall

/**
 * Onboarding flow controller — ported from `AllNewUser` (AllNewUser.swift).
 *
 * Drives the intro -> assessment -> signUp -> (feedback) -> sales-slides ->
 * payment state machine, including the experiment-gated sales-slide sequencing
 * and terms agreement. Handler calls that depend on not-yet-ported subsystems
 * (initialFeedback, AssessmentSubmissionHandler, onboardingSetup.setup,
 * isHardPaywall, NotificationHandler) are marked TODO and ported with them.
 */
class AllNewUserViewModel(
    private val userInfo: UserInfo,
    private val program: Program,
    private val onboardingSetup: OnboardingSetup,
    private val experimentController: ExperimentController,
    private val scope: CoroutineScope,
    private val onCompletedOnboarding: () -> Unit,
) {
    private val _screen = MutableStateFlow<NewUserScreen>(NewUserScreen.Intro)
    val screen: StateFlow<NewUserScreen> = _screen.asStateFlow()

    private var availableSalesSlides: List<NewUserScreen> = emptyList()
    private var signInOnlyMode: Boolean = false

    /** True when the sign-up screen was entered via "Sign In" (existing user) —
     *  in that case we skip the create-user + assessment submission. */
    val isSignInOnly: Boolean get() = signInOnlyMode

    val clear30: Boolean get() = onboardingSetup.assessmentInfo?.choseClear30 ?: true

    init {
        setInitialPage()
        setAvailableSalesSlides()
    }

    private fun setInitialPage() {
        // A returning mid-onboarding user with feedback jumps straight to it
        // (iOS setInitialPage).
        program.initialFeedback?.let { _screen.value = NewUserScreen.Feedback(it) }
        logScreen(_screen.value)
    }

    fun setAvailableSalesSlides() {
        availableSalesSlides = buildList {
            if (experimentController.showFeature(ExperimentKey.onboardingNotifications)) add(NewUserScreen.Notifications)
            if (experimentController.showFeature(ExperimentKey.onboardingReferral)) add(NewUserScreen.Referral)
            if (experimentController.showFeature(ExperimentKey.onboardingReviews)) add(NewUserScreen.Reviews)
            add(NewUserScreen.Payment)
        }
    }

    fun handleSignIn() {
        signInOnlyMode = true
        _screen.value = NewUserScreen.SignUp
        logScreen(NewUserScreen.SignUp)
    }

    fun handleNextScreen() {
        val next: NewUserScreen? = when (_screen.value) {
            is NewUserScreen.Intro -> NewUserScreen.Assessment
            is NewUserScreen.Assessment -> NewUserScreen.SignUp
            is NewUserScreen.SignUp ->
                // Clear30 signups see their normative feedback first; Life (no
                // feedback) goes straight to the sales slides (iOS handleNextScreen).
                program.initialFeedback?.let { NewUserScreen.Feedback(it) }
                    ?: handleSalesSlide(_screen.value) ?: NewUserScreen.Payment
            is NewUserScreen.Feedback,
            is NewUserScreen.Notifications,
            is NewUserScreen.Reviews,
            is NewUserScreen.Commitment,
            is NewUserScreen.Referral -> handleSalesSlide(_screen.value)
            is NewUserScreen.Payment -> null
        }

        if (next != null) {
            when (next) {
                is NewUserScreen.Assessment -> agreeToTerms()
                is NewUserScreen.SignUp -> signInOnlyMode = false
                is NewUserScreen.Feedback -> setAvailableSalesSlides()
                else -> {}
            }
            _screen.value = next
            logScreen(next)
        }
    }

    fun back(to: NewUserScreen) { _screen.value = to }

    private fun agreeToTerms() {
        if (userInfo.agreedToTerms == true) return
        userInfo.agreedToTerms = true
        if (onboardingSetup.guardianInfo == null) AttributionHandler.initSDK()
        scope.launch { Clear30Store.save(userInfo) }
    }

    /** Sales-slide sequencing (AllNewUser.handleSalesSlide). */
    private fun handleSalesSlide(current: NewUserScreen): NewUserScreen? {
        val slides = availableSalesSlides
        if (current !in slides) return slides.firstOrNull()
        val pos = slides.indexOf(current)
        return if (pos >= 0 && pos + 1 < slides.size) slides[pos + 1] else null
    }

    private fun logScreen(screen: NewUserScreen) =
        Logger.logEvent(userInfo.loggingID, LogEventType.openedOnboardingScreen, mapOf(LogEventExtraDataType.TYPE to screen.type))

    /** AllNewUser.handlePayment — finalise onboarding. */
    fun handlePayment(entitlement: EntitlementType?, setPaidFalseOn: kotlinx.datetime.Instant? = null) {
        val isPaid = entitlement != null
        userInfo.notificationSettings = ToggleSettings.notificationDefaults(paid = isPaid)
        // TODO(port): if paid, onboardingSetup.setup (groups)

        // iOS: look up whether the paywall the user saw is hard (payment.hard_paywalls).
        // currentPaywallID is only set by Helium-driven paywalls, so this is dormant
        // until the Helium Android SDK lands — same guard as iOS.
        org.clear30.data.PaywallController.currentPaywallID.value?.let { paywallID ->
            scope.launch {
                SupabaseController.isHardPaywall(paywallID)?.let { hard ->
                    userInfo.paywallHard = hard
                    Clear30Store.save(userInfo)
                }
            }
        }
        userInfo.currentEntitlementType = entitlement
        userInfo.setPaidFalseOn = setPaidFalseOn
        userInfo.completedOnboarding = true

        // iOS: NotificationHandler.removeAbandonedOnboarding() — they made it,
        // cancel the "come back" nudges.
        org.clear30.data.NotificationHandler.removeAbandonedOnboarding()
        // Arm the daily check-in reminder now that the user has notification
        // settings (notificationDefaults was just written above).
        org.clear30.data.NotificationHandler.scheduleCheckInReminder(userInfo)

        scope.launch {
            // The user + assessment + program timeline were all created right after
            // OTP verification (AssessmentSubmissionHandler.submitAssessment, ONCE
            // per signup — re-submitting here would duplicate the
            // program_assessment_responses row and the break). At payment, iOS only
            // verifies the setup: if the paywall was finished on a later day than
            // sign-up, shift the whole timeline so today is still Day 1.
            AssessmentSubmissionHandler.verifyProgramSetup(userInfo, program)
            Clear30Store.save(userInfo)
            Clear30Store.save(program)
            onCompletedOnboarding()
        }
    }
}

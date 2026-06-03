package org.clear30.views.newuser

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val clear30: Boolean get() = onboardingSetup.assessmentInfo?.choseClear30 ?: true

    init {
        setInitialPage()
        setAvailableSalesSlides()
    }

    private fun setInitialPage() {
        // TODO(port): program.initialFeedback -> jump straight to Feedback (ProgramContent)
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
                // TODO(port): if program.initialFeedback != null -> Feedback(it)
                handleSalesSlide(_screen.value) ?: NewUserScreen.Payment
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
        // TODO(port): AssessmentSubmissionHandler.verifyProgramSetup; if paid onboardingSetup.setup(groups);
        //   SupabaseController.isHardPaywall -> userInfo.paywallHard; NotificationHandler.removeAbandonedOnboarding
        userInfo.currentEntitlementType = entitlement
        userInfo.setPaidFalseOn = setPaidFalseOn
        userInfo.completedOnboarding = true

        // Bridge onboarding's lastSmoked into the program so the home "time
        // clear" timer reads correctly from day 1. The full submitAssessment
        // RPC + break creation lives in AssessmentSubmissionHandler (TODO);
        // until that lands, this is the minimum required for the timer + the
        // sober streak to be honest.
        onboardingSetup.assessmentInfo?.let { info ->
            program.lastSmoked = info.lastSmoked
            program.startDate = org.clear30.util.now()
        }

        // iOS: NotificationHandler.removeAbandonedOnboarding() — they made it,
        // cancel the "come back" nudges.
        org.clear30.data.NotificationHandler.removeAbandonedOnboarding()
        // Arm the daily check-in reminder now that the user has notification
        // settings (notificationDefaults was just written above).
        org.clear30.data.NotificationHandler.scheduleCheckInReminder(userInfo)

        scope.launch {
            Clear30Store.save(userInfo)
            Clear30Store.save(program)
            onCompletedOnboarding()
        }
    }
}

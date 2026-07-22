package org.clear30.data

import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.AssessmentType
import org.clear30.data.model.CheckInMethod
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.ProgramBreakType
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.SupabaseNewUser
import org.clear30.data.supabase.createUser
import org.clear30.data.supabase.getNormativeFeedback
import org.clear30.data.supabase.submitAssessment
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.justDay
import org.clear30.util.now

/**
 * AssessmentSubmissionHandler — ported from AssessmentSubmissionHandler.swift.
 *
 * Runs ONCE per signup, right after OTP verification (both RPCs read `auth.uid()`
 * server-side): creates the `public.users` row, submits the onboarding assessment,
 * and starts the program through the faithful scheduler
 * ([ProgramTimelineHandler.start] → `ProgramMessageHandler.schedule`), mirroring
 * iOS `submitAssessment` → `handleClear30` / `handleLife`. The payment screen must
 * NOT re-submit — it only runs [verifyProgramSetup] (iOS `AllNewUser.handlePayment`).
 *
 * Still-unported iOS pieces, marked inline: `program_get_feedback` normative
 * feedback onto the break (O4/X7) and the adolescent/guardian branch.
 */
object AssessmentSubmissionHandler {

    /** UserInfo cache key stamping when the assessment was submitted (iOS uses the same string). */
    private const val INITIAL_SUBMISSION_KEY = "initialAssessmentSubmission"

    /**
     * Create the user, submit the assessment, and start the program timeline.
     * Returns an error message on failure, or null on success. Mutates
     * [userInfo]._userID with the resolved `users.id`.
     */
    suspend fun submitAssessment(
        userInfo: UserInfo,
        program: Program,
        onboardingSetup: OnboardingSetup,
    ): String? {
        // The onboarding assessment may be skipped/stubbed, so this can be null —
        // we DON'T bail in that case. An assessment response is what assigns the
        // user a program; without one `program_get_messages` returns no content
        // and the Today feed is empty. Default to the clear30 30-day program.
        val info = onboardingSetup.assessmentInfo

        // 1. Create (upsert) the user. Phone/email come from auth.users server-side.
        val newUser = SupabaseNewUser(
            name = userInfo.name,
            emoji = userInfo.emoji ?: "😁",
            day_info = program.dayInfo,
            fcm_token = userInfo.fcmToken,
            sms_settings = userInfo.smsSettings,
            logging_id = userInfo.loggingID,
        )
        val (userID, createError) = SupabaseController.createUser(newUser)
        if (createError != null) return createError.message
        if (userID.isNullOrEmpty()) return "No user ID"

        userInfo._userID = userID
        userInfo.peerSupportMigrated = true

        // Identify the user with RevenueCat so purchases attach to their account and
        // subscriber attributes are set for targeting (iOS PaywallController.signIn).
        // No-ops when RevenueCat isn't configured (blank API key).
        PaywallController.signIn(userInfo, PaywallController.getUserParams(userInfo))

        // Clear any existing breaks and messages (iOS does the same before starting)
        // — a fresh signup starts clean. Accounts with existing server data never
        // reach this path: the sign-up screen routes them to restore instead.
        program.breaks.clear()
        program.contentInfo = mutableMapOf()

        // Stamp the submission time — verifyProgramSetup shifts the timeline by
        // the days elapsed between now and payment (midnight-crossing signups).
        userInfo.setCacheDate(INITIAL_SUBMISSION_KEY, now())

        val responses = info?.responses ?: emptyList()
        val lastSmoked = info?.lastSmoked ?: program.lastSmoked
        return if (info?.choseClear30 != false) {
            handleClear30(userInfo, program, responses, lastSmoked)
        } else {
            handleLife(userInfo, program, responses, lastSmoked)
        }
    }

    /**
     * The Clear30 track (iOS `handleClear30`): build the break(s), submit under
     * `"clear30"`, keep the response ID on the break, and start the program —
     * which schedules the content (incl. start-soon topics for a future-dated
     * break) and pushes the whole state to Supabase.
     */
    private suspend fun handleClear30(
        userInfo: UserInfo,
        program: Program,
        responses: List<ProgramAssessmentResponse>,
        lastSmoked: kotlinx.datetime.Instant,
    ): String? {
        val (startSoonBreak, mainBreak) = ProgramTimelineHandler.handleBreaks(ProgramBreakType.CLEAR30, responses)

        val (responseID, submitError) = SupabaseController.submitAssessment(AssessmentType.Clear30.string, responses)
        if (submitError != null) return submitError.message
        mainBreak.assessmentResponseID = responseID?.toInt()

        // Normative feedback → the Feedback onboarding screen (iOS handleClear30 →
        // getNormativeFeedback). iOS anchors startDate on the assessment's
        // start-date answer, defaulting to tomorrow — the live flow asks no
        // start-date, so tomorrow it is. Failing the signup on a feedback error
        // mirrors iOS; locally this needs `supabase functions serve` (E1).
        val (feedback, feedbackError) = SupabaseController.getNormativeFeedback(
            userID = userInfo.userID,
            lastSmoked = lastSmoked,
            startDate = now().justDay.adding(days = 1),
        )
        if (feedbackError != null) return feedbackError.message
        mainBreak.normativeFeedback = feedback

        // Consumption method → the default check-in method (iOS handleClear30).
        val methodResponse = mainBreak.getAssessmentResponse(AssessmentQuestionID.CONSUMPTION_METHOD.raw)
            ?.responses?.firstOrNull() ?: 1
        val consumptionMethod = when (methodResponse) {
            1 -> CheckInMethod.PEN
            2 -> CheckInMethod.DAB
            3 -> CheckInMethod.EDIBLE
            else -> CheckInMethod.BUD
        }

        ProgramTimelineHandler.start(
            program = program,
            mainBreak = mainBreak,
            startSoonBreak = startSoonBreak,
            lastSmoked = lastSmoked,
            checkInMethod = consumptionMethod,
            clientName = userInfo.name,
        )?.let { return it }

        Clear30Store.save(userInfo)
        Logger.logEvent(userInfo.loggingID, LogEventType.completedAssessment, mapOf(LogEventExtraDataType.TYPE to "clear30"))
        // iOS re-anchors the abandoned-onboarding reminders to the successful
        // submit (AllSignUpViewModel.swift:181) — the pre-payment window is
        // where they matter.
        runCatching { NotificationHandler.scheduleAbandonedOnboarding(userInfo, program) }
        return null
    }

    /**
     * Start a brand-new Clear30 from an existing account — the Profile "New
     * Break" flow (iOS `handleNewBreak`, AssessmentSubmissionHandler.swift:265-353).
     * Builds the break(s), submits under `"clear30"`, keeps the response ID +
     * normative feedback on the break, then splices the new content in via
     * [ProgramTimelineHandler.newClear30]. Returns an error message or null.
     */
    suspend fun handleNewBreak(
        userInfo: UserInfo,
        program: Program,
        breakType: ProgramBreakType,
        responses: List<ProgramAssessmentResponse>,
    ): String? {
        // 1. Create the break object(s). NOTE: unlike iOS, the breaks are NOT
        //    appended here — Android's newClear30 appends them itself (guarding
        //    on identity), so appending twice would duplicate.
        val (startSoonBreak, mainBreak) = ProgramTimelineHandler.handleBreaks(breakType, responses)

        // 2. Submit the responses under the break's assessment id ("clear30").
        val (responseID, submitError) = SupabaseController.submitAssessment(breakType.assessmentType.string, responses)
        if (submitError != null) return "Error with submitting responses: ${submitError.message}"
        mainBreak.assessmentResponseID = responseID?.toInt()

        // 3. Normative feedback, anchored on the chosen start date (default tomorrow).
        val startDate = responses
            .firstOrNull { it.question.strippedPrompt == AssessmentQuestionID.START_DATE.raw }
            ?.question?.options?.firstOrNull()
            ?.let { runCatching { org.clear30.data.model.PlainDate.parse(it) }.getOrNull() }
            ?.dateObject
            ?: now().justDay.adding(days = 1)
        val (feedback, feedbackError) = SupabaseController.getNormativeFeedback(
            userID = userInfo.userID,
            lastSmoked = program.lastSmoked,
            startDate = startDate,
        )
        if (feedbackError != null) return "Could not get feedback. ${feedbackError.message}"
        if (feedback == null) return "Could not get feedback. Feedback is nil."
        mainBreak.normativeFeedback = feedback

        // 4. Start the break — content splice, health recompute, save + push.
        ProgramTimelineHandler.newClear30(
            program = program,
            mainBreak = mainBreak,
            startSoonBreak = startSoonBreak,
            clientName = userInfo.name,
        )?.let { return "Could not start break: $it" }

        Logger.logEvent(userInfo.loggingID, LogEventType.completedAssessment, mapOf(LogEventExtraDataType.TYPE to "new_clear30"))
        return null
    }

    /**
     * The Life ("Better Life Program") track (iOS `handleLife`): submit under
     * `"life-onboarding"` (NOT `"life"`, the post-assessment ID), set the
     * moderation mode from LO-Use-State, and start the core program.
     */
    private suspend fun handleLife(
        userInfo: UserInfo,
        program: Program,
        responses: List<ProgramAssessmentResponse>,
        lastSmoked: kotlinx.datetime.Instant,
    ): String? {
        val loUseState = responses.firstOrNull { it.question.strippedPrompt == AssessmentQuestionID.LO_USE_STATE.raw }
            ?.responses?.firstOrNull() ?: 0
        val weedFree = loUseState == 0

        val (_, submitError) = SupabaseController.submitAssessment(AssessmentType.LifeOnboarding.string, responses)
        if (submitError != null) return submitError.message

        program.coreModeration = !weedFree

        ProgramTimelineHandler.start(
            program = program,
            mainBreak = null,
            startSoonBreak = null,
            lastSmoked = lastSmoked,
            clientName = userInfo.name,
        )?.let { return "Failed to start core program: $it" }

        Clear30Store.save(userInfo)
        Logger.logEvent(userInfo.loggingID, LogEventType.completedAssessment, mapOf(LogEventExtraDataType.TYPE to "life"))
        return null
    }

    /**
     * iOS `verifyProgramSetup`, run at payment: if the user signed up (and got
     * their timeline scheduled) but didn't finish the paywall until N days later,
     * shift the whole program forward by N so today is still Day 1.
     */
    suspend fun verifyProgramSetup(userInfo: UserInfo, program: Program) {
        val submissionDate = userInfo.getCachedDate(INITIAL_SUBMISSION_KEY) // fallback = now → shift 0
        val daysToShift = submissionDate.daysTo(now())
        if (daysToShift <= 0) return
        ProgramTimelineHandler.adjustBreakTime(program, daysToShift)
    }
}

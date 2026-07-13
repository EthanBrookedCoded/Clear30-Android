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
import org.clear30.data.supabase.submitAssessment
import org.clear30.util.daysTo
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

        // TODO(port O4/X7): getNormativeFeedback (`program_get_feedback`) →
        // mainBreak.normativeFeedback, shown on the Feedback onboarding screen.

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

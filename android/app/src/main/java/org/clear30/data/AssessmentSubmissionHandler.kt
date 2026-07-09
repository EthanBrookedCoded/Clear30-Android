package org.clear30.data

import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramBreakType
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.SupabaseNewUser
import org.clear30.data.supabase.createUser
import org.clear30.data.supabase.submitAssessment
import org.clear30.data.supabase.syncProgramState
import org.clear30.util.justDay
import org.clear30.util.now

/**
 * AssessmentSubmissionHandler — ported (partially) from AssessmentSubmissionHandler.swift.
 *
 * Orchestrates the post-sign-up account creation + assessment submission so that
 * a fresh account exists in `public.users` and its onboarding answers land in
 * `programs.program_assessment_responses` (visible in Studio).
 *
 * SCOPE: this is the "create a user + record the assessment responses" slice only.
 * The iOS handler additionally runs Amplitude/RevenueCat sign-in and the normative
 * feedback → break creation → `program.start` machinery. The analytics is
 * intentionally skipped for now; the program-setup machinery (handleClear30 /
 * handleLife / getNormativeFeedback / program.start) remains the TODO tracked in
 * [org.clear30.views.newuser.AllNewUserViewModel].
 *
 * Must run AFTER OTP verification (both RPCs read `auth.uid()` server-side).
 */
object AssessmentSubmissionHandler {

    /**
     * Create the user, then submit the collected assessment responses.
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
        // and the Today feed is empty. So we always submit one (defaulting to the
        // clear30 30-day program with empty responses, which still returns one
        // lesson per day — including the day-0 intro video).
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

        // 2. Submit the assessment responses. The backend filters the payload to
        //    the assessment's known question IDs, so unrelated prompts are dropped.
        //    `clear30` / `life` are the only valid assessment ids (each maps to a
        //    program); default to clear30 when nothing was chosen.
        val assessmentID = when {
            info == null -> "clear30"
            info.choseClear30 -> "clear30"
            else -> "life"
        }
        val responses = info?.responses ?: emptyList()
        val (responseID, submitError) = SupabaseController.submitAssessment(assessmentID, responses)
        if (submitError != null) return submitError.message

        // 3. Place the user on the Clear30 (vs Life) track by registering the break.
        //    iOS builds this via handleBreaks → program.start; the live onboarding
        //    asks no start-date, so Day 0 = today with no start-soon bridge. Content
        //    itself is pulled lazily by the Today/Support tabs (anchored to the break
        //    start); here we create + push the break so the badge ("Clear30 Day N"),
        //    calendar window and Previous Breaks reflect it and it survives reinstall.
        //    Life ("Moderation") users get no break — currentBreak == null == Life.
        if (assessmentID == "clear30" && program.breaks.none { it.type == ProgramBreakType.CLEAR30 }) {
            val (startSoon, main) = ProgramTimelineHandler.handleBreaks(ProgramBreakType.CLEAR30, responses)
            program.breaks.add(main)
            startSoon?.let { program.breaks.add(it) }
            program.startDate = now().justDay
            Clear30Store.save(program)
            SupabaseController.syncProgramState(program)
        }

        android.util.Log.i(
            "AssessmentSubmission",
            "Created user $userID, submitted '$assessmentID' assessment → response id $responseID",
        )
        return null
    }
}

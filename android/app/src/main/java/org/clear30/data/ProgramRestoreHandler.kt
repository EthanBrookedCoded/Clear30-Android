package org.clear30.data

import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.AssessmentQuestionType
import org.clear30.data.model.AssessmentQuestions
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.SlipPlan
import org.clear30.data.model.SupabaseMessageWithStage
import org.clear30.data.model.UserInfo
import org.clear30.data.model.setCacheObject
import org.clear30.data.model.toSlipPlan
import org.clear30.data.supabase.SupabaseAssessmentResponse
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.SupabaseUserData
import org.clear30.data.supabase.fetchAssessmentResponse
import org.clear30.data.supabase.fetchMessagesByIDs
import org.clear30.data.supabase.fetchUserData
import org.clear30.util.adding
import org.clear30.util.now

/**
 * ProgramRestoreHandler — ported from ProgramRestoreHandler.swift.
 *
 * Rebuilds the local Program/UserInfo from the user's `public.users` row on
 * sign-in (the only PULL in the sync model; every other touchpoint pushes).
 * The stored `content_info` holds resolved calendar dates + message ids, so no
 * scheduling math re-runs here — bodies are hydrated by id and re-keyed.
 */
object ProgramRestoreHandler {

    private const val CLIENT_NAME = "_CLIENTNAME_"

    /** Restore everything; returns an error message, or null on success. */
    suspend fun restoreUserData(userID: String, userInfo: UserInfo, program: Program): String? {
        val userData = SupabaseController.fetchUserData(userID)
            ?: return "No user data found"

        val messageIDs = userData.content_info.orEmpty().values
            .flatMap { it.message_ids }
            .distinct()
        val messages = SupabaseController.fetchMessagesByIDs(messageIDs.map { it.toLong() })
        if (messageIDs.isNotEmpty() && messages.isEmpty()) {
            return "Could not fetch program content"
        }

        restoreToModels(userData, messages, userInfo, program)

        Clear30Store.save(userInfo)
        Clear30Store.save(program)
        return null
    }

    private suspend fun restoreToModels(
        userData: SupabaseUserData,
        messages: List<SupabaseMessageWithStage>,
        userInfo: UserInfo,
        program: Program,
    ) {
        // 1. Name / emoji (only when not already set locally)
        if (userInfo.name.isEmpty()) userInfo.name = userData.name
        if (userInfo.emoji.isNullOrEmpty()) userInfo.emoji = userData.emoji

        // 2. Your why
        userInfo.userWhy = userData.your_why

        // 2b. If-then slip plans (trigger_responses → SlipPlan cache — iOS
        //     ProgramRestoreHandler.swift:103-115).
        userData.trigger_responses?.takeIf { it.isNotEmpty() }?.let { responses ->
            userInfo.setCacheObject(SlipPlan.CACHE_KEY, responses.map { it.toSlipPlan() })
        }

        // 3. Start date — DERIVED, not read from users.start_date (iOS rule):
        //    min(earliest content date, earliest check-in date, earliest break start)
        //    falling back to created_at, then today.
        val earliestContentDate = userData.content_info.orEmpty().keys
            .mapNotNull { runCatching { PlainDate.parse(it) }.getOrNull() }
            .minOrNull()?.dateObject
        val earliestCheckInDate = userData.day_info.orEmpty().keys.minOrNull()?.dateObject
        val earliestBreakDate = userData.program_breaks.orEmpty().minOfOrNull { it.start_date }
        program.startDate = listOfNotNull(earliestContentDate, earliestCheckInDate, earliestBreakDate)
            .minOrNull() ?: userData.created_at ?: now()

        // 4. Last smoked
        userData.last_smoked?.let { program.lastSmoked = it }

        // 5. Check-in log
        userData.day_info?.let { program.dayInfo = it.toMutableMap() }

        // 6. Custom check-ins
        userData.custom_check_ins?.let { list ->
            program.customCheckIns = list.map { it.toCustomCheckIn() }.toMutableList()
        }

        // 7. Breaks — unknown types (there is no "life" break) drop out via
        //    toProgramBreak() == null; assessment responses hydrate by id.
        userData.program_breaks?.let { breaksData ->
            program.breaks = breaksData.mapNotNull { it.toProgramBreak() }.toMutableList()
            for (programBreak in program.breaks) {
                programBreak.assessmentResponseID?.let { id ->
                    SupabaseController.fetchAssessmentResponse(id)?.let {
                        programBreak.assessmentResponses = it.toAssessmentResponses()
                    }
                }
                programBreak.postAssessmentResponseID?.let { id ->
                    SupabaseController.fetchAssessmentResponse(id)?.let {
                        programBreak.postAssessmentResponses = it.toAssessmentResponses()
                    }
                }
            }
        }

        // 8. Timeline — re-key by date; unlockOn is recomputed as day@10:00 +
        //    array-index seconds (preserves within-day display order), and
        //    _CLIENTNAME_ is substituted into the copy.
        userData.content_info?.let { contentData ->
            val messageMap = messages.associateBy { it.id }
            val rebuilt = mutableMapOf<PlainDate, ContentInfo>()
            for ((dateString, info) in contentData) {
                val plain = runCatching { PlainDate.parse(dateString) }.getOrNull() ?: continue
                val reconstructed = info.message_ids.mapIndexedNotNull { index, id ->
                    val message = messageMap[id] ?: return@mapIndexedNotNull null
                    message.copy(
                        title = message.title.replace(CLIENT_NAME, userInfo.name),
                        subtitle = message.subtitle.replace(CLIENT_NAME, userInfo.name),
                        body = message.body.replace(CLIENT_NAME, userInfo.name),
                    ).toProgramMessage(
                        unlockOn = plain.dateObject.adding(seconds = 10L * 3600 + index),
                    )
                }
                val stage = info.message_ids.firstOrNull()?.let { messageMap[it]?.toStage() }
                rebuilt[plain] = ContentInfo(messages = reconstructed, stage = stage, progress = info.progress)
            }

            // 9. Force progress to 1.0 for every day before today (iOS restore rule).
            val yesterday = PlainDate.from(now().adding(days = -1))
            for ((date, contentInfo) in rebuilt) {
                if (date <= yesterday) rebuilt[date] = contentInfo.copy(progress = 1.0)
            }
            program.contentInfo = rebuilt
        }
    }

    // MARK: - Assessment response reconstruction (SupabaseAssessmentResponse.toAssessmentResponses)

    private fun SupabaseAssessmentResponse.toAssessmentResponses(): List<ProgramAssessmentResponse> {
        val out = mutableListOf<ProgramAssessmentResponse>()
        for ((strippedPrompt, storedValues) in responses) {
            var question = questionForPrompt(strippedPrompt)
            val responseIndices = mutableListOf<Int>()

            val isInputQuestion = strippedPrompt == AssessmentQuestionID.NAME.raw ||
                strippedPrompt == AssessmentQuestionID.COMMENTS.raw
            if (isInputQuestion) {
                // Input questions carry their text as the options themselves.
                question = question.copy(options = storedValues)
                if (storedValues.isNotEmpty()) responseIndices.add(0)
            } else {
                var options = question.options
                for (storedValue in storedValues) {
                    val existing = options.indexOf(storedValue)
                    if (existing >= 0) {
                        responseIndices.add(existing)
                    } else {
                        options = options + storedValue
                        responseIndices.add(options.size - 1)
                    }
                }
                question = question.copy(options = options)
            }

            if (responseIndices.isNotEmpty()) {
                out.add(ProgramAssessmentResponse(question = question, responses = responseIndices))
            }
        }
        return out
    }

    /** Map a stored strippedPrompt back to the static question definition. */
    private fun questionForPrompt(strippedPrompt: String): ProgramAssessmentQuestion =
        when (strippedPrompt) {
            AssessmentQuestionID.REFERRAL.raw -> AssessmentQuestions.referral
            AssessmentQuestionID.NAME.raw -> AssessmentQuestions.name
            AssessmentQuestionID.BREAK_REASON.raw -> AssessmentQuestions.breakReason
            AssessmentQuestionID.CONSUMPTION_METHOD.raw -> AssessmentQuestions.consumptionMethod
            AssessmentQuestionID.DAYS_USING.raw -> AssessmentQuestions.daysUsing
            AssessmentQuestionID.USAGE_DURATION.raw -> AssessmentQuestions.usageDuration
            AssessmentQuestionID.BIOLOGICAL_SEX.raw -> AssessmentQuestions.biologicalSex
            AssessmentQuestionID.MONEY_SPENT.raw -> AssessmentQuestions.moneySpent
            AssessmentQuestionID.HELP_HARM.raw -> AssessmentQuestions.helpHarm
            AssessmentQuestionID.AGE.raw -> AssessmentQuestions.age
            AssessmentQuestionID.GUARDIAN_REPORT.raw -> AssessmentQuestions.guardianReports
            AssessmentQuestionID.LAST_SMOKED.raw -> AssessmentQuestions.lastSmoked
            AssessmentQuestionID.SMOKE_TIME.raw -> AssessmentQuestions.timeOfDay
            AssessmentQuestionID.PREVIOUS_BREAK.raw -> AssessmentQuestions.previousBreak
            AssessmentQuestionID.SYMPTOMS.raw -> AssessmentQuestions.symptoms
            AssessmentQuestionID.TRIGGER.raw -> AssessmentQuestions.triggers
            AssessmentQuestionID.THEN_WHAT.raw -> AssessmentQuestions.afterClear30
            AssessmentQuestionID.COMMITMENT.raw -> AssessmentQuestions.commitment
            AssessmentQuestionID.LO_USE_STATE.raw -> AssessmentQuestions.modAbs
            AssessmentQuestionID.WHAT_BRINGS_YOU_HERE.raw -> AssessmentQuestions.whatBringsYouHere
            // Questions without a ported static definition (post-assessment set,
            // legacy start-date, new-break) reconstruct minimally like iOS's
            // unknown-prompt fallback.
            else -> ProgramAssessmentQuestion(
                questionNumber = 0,
                type = AssessmentQuestionType.MultipleChoice,
                strippedPrompt = strippedPrompt,
                prompt1 = "",
                prompt2 = strippedPrompt,
                options = emptyList(),
            )
        }
}

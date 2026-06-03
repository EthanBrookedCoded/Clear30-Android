package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.justDay
import org.clear30.util.now

/** Program detail-sheet copy (Swift `Program.DetailSheetInfo`). */
data class ProgramDetailSheetInfo(val title: String, val description: String) {
    val id: String get() = title + description
}

/**
 * ProgramBreak — ported from ProgramBreak.swift (`@Model`). A single Clear30
 * break with its assessment + post-assessment responses. `endDate` is the first
 * day *out* of the break (override, else start + type.raw + 1).
 */
@Serializable
class ProgramBreak(
    var name: String,
    var type: ProgramBreakType,
    var startDate: Instant,
    var endDateOverride: Instant? = null,
    var assessmentResponseID: Int? = null,
    var assessmentResponses: List<ProgramAssessmentResponse> = emptyList(),
    var normativeFeedback: ProgramNormativeFeedback? = null,
    var postAssessmentResponseID: Int? = null,
    var postAssessmentResponses: List<ProgramAssessmentResponse> = emptyList(),
    var postAssessmentCompleted: Boolean = false,
    var moneySavedAdjustment: Int = 0,
    var messages: List<ProgramMessage> = emptyList(), // deprecated
) {
    val endDate: Instant
        get() = endDateOverride ?: startDate.justDay.adding(days = type.raw + 1)

    val isStartSoon: Boolean get() = type.isStartSoon

    val currentBreakDay: Int get() = getBreakDay(now())
    fun getBreakDay(date: Instant): Int = startDate.daysTo(date)

    fun overrideEndDate(lastDayOfBreak: Instant) {
        endDateOverride = lastDayOfBreak.justDay.adding(days = 1)
    }

    fun getAssessmentResponse(strippedPrompt: String): ProgramAssessmentResponse? =
        assessmentResponses.firstOrNull { it.question.strippedPrompt == strippedPrompt }

    val breakDescription: String?
        get() = when (type) {
            ProgramBreakType.CLEAR30 -> {
                val thenWhat = getAssessmentResponse(AssessmentQuestionID.THEN_WHAT.raw) ?: return null
                if (thenWhat.responses.firstOrNull() == 0) "Your first 30 days of quitting weed."
                else "Your 30 day break from weed."
            }
            ProgramBreakType.CLEAR30_START_SOON -> null
        }

    val detailSheetInfo: ProgramDetailSheetInfo?
        get() = when (type) {
            ProgramBreakType.CLEAR30 -> ProgramDetailSheetInfo(
                title = "Clear30 is your guided 30 day break from weed.",
                description = """
                    Whether you're taking a month off or quitting for good, you'll get daily content and tools to help you through it.

                    Clear30 isn't about perfection, if you slip up once or twice, that's part of the process.

                    After 30 days, the Life program continues supporting you whether you're staying sober or moderating.
                """.trimIndent(),
            )
            ProgramBreakType.CLEAR30_START_SOON -> null
        }

    companion object {
        /** Mirrors the iOS init that normalised startDate to justDay. */
        fun create(
            name: String,
            type: ProgramBreakType,
            startDate: Instant,
            assessmentResponses: List<ProgramAssessmentResponse> = emptyList(),
            normativeFeedback: ProgramNormativeFeedback? = null,
            postAssessmentResponses: List<ProgramAssessmentResponse> = emptyList(),
        ) = ProgramBreak(
            name = name, type = type, startDate = startDate.justDay,
            assessmentResponses = assessmentResponses, normativeFeedback = normativeFeedback,
            postAssessmentResponses = postAssessmentResponses,
        )
    }
}

// extension [ProgramBreak]
val List<ProgramBreak>.sorted: List<ProgramBreak> get() = sortedBy { it.startDate }
val List<ProgramBreak>.previous: List<ProgramBreak> get() = sorted.filter { it.endDate < now() }

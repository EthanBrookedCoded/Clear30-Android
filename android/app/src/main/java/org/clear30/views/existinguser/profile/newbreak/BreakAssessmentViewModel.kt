package org.clear30.views.existinguser.profile.newbreak

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.AssessmentQuestions
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.ProgramBreakType
import org.clear30.data.model.UserInfo
import org.clear30.views.newuser.assessment.AssessmentSlide

/**
 * BreakAssessmentViewModel — port of iOS BreakAssessmentViewModel.swift for the
 * Profile "New Break" flow. Slides = [newBreakType] + newClear30Questions, with
 * per-question affirmation insertion; the terminal "setting up your break"
 * screen is rendered by the host once [currentIndex] passes the last slide.
 */
class BreakAssessmentViewModel(
    val userInfo: UserInfo,
    val program: Program,
) {
    var currentIndex by mutableIntStateOf(0)
    var assessmentDone by mutableStateOf(false)

    val slides = mutableStateListOf<AssessmentSlide>()
    val responses = mutableStateMapOf<String, ProgramAssessmentResponse>()
    var breakType: ProgramBreakType? = null

    init {
        val questions = (listOf(AssessmentQuestions.newBreakType) + AssessmentQuestions.newClear30Questions)
            .map { q ->
                q.copy(
                    prompt1 = q.prompt1.replace(CLIENT_NAME, userInfo.name),
                    prompt2 = q.prompt2.replace(CLIENT_NAME, userInfo.name),
                )
            }
        slides.addAll(questions.map { AssessmentSlide.Question(it) })
    }

    /** Record + react to a question answer, then advance (iOS handleSlideCompletion). */
    fun handleQuestionCompletion(question: org.clear30.data.model.ProgramAssessmentQuestion, index: Int) {
        val response = responses[question.strippedPrompt]

        // Break-type capture.
        if (question.strippedPrompt == AssessmentQuestionID.NEW_BREAK_TYPE.raw) {
            response?.responses?.firstOrNull()
                ?.let { question.options.getOrNull(it) }
                ?.let { ProgramBreakType.from(it) }
                ?.let { breakType = it }
        }

        // Auto-add the question's affirmation for the chosen option (iOS inserts
        // at index+1, replacing a previously inserted one on re-answer).
        if (question.autoAddAffirmation != false) {
            val affirmations = question.affirmations ?: emptyList()
            val idx = response?.responses?.firstOrNull()
            val insertAt = index + 1
            // On re-answer, drop the affirmation inserted for the previous answer.
            val existing = slides.getOrNull(insertAt) as? AssessmentSlide.Information
            if (existing != null && affirmations.any { it.id == existing.data.id }) {
                slides.removeAt(insertAt)
            }
            if (idx != null && idx < affirmations.size) {
                slides.add(insertAt, AssessmentSlide.Information(affirmations[idx]))
            }
        }

        Logger.logEvent(
            userInfo.loggingID,
            LogEventType.completedAssessmentQuestion,
            mapOf(
                LogEventExtraDataType.ASSESSMENT_QUESTION to question.strippedPrompt,
                LogEventExtraDataType.FROM to "new_break",
            ),
        )

        currentIndex = index + 1
    }

    fun handleInfoCompletion(data: org.clear30.data.model.AssessmentInfoData, index: Int) {
        data.id?.let {
            Logger.logEvent(
                userInfo.loggingID,
                LogEventType.completedAssessmentSlide,
                mapOf(LogEventExtraDataType.TYPE to it.raw, LogEventExtraDataType.FROM to "new_break"),
            )
        }
        currentIndex = index + 1
    }

    companion object {
        private const val CLIENT_NAME = "_CLIENTNAME_"
    }
}

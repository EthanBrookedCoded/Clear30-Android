package org.clear30.views.existinguser.postassessment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.ProgramTimelineHandler
import org.clear30.data.model.AssessmentInfoDataID
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.AssessmentQuestions
import org.clear30.data.model.AssessmentType
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.ExperimentKey
import org.clear30.data.model.PostAssessmentQuestions
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.ProgramBreak
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.logCoachReferralEvent
import org.clear30.data.supabase.submitAssessment
import org.clear30.data.supabase.syncProgramState
import org.clear30.views.newuser.assessment.AssessmentSlide

/**
 * PostAssessmentViewModel — port of PostAssessmentViewModel.swift. Builds the
 * dynamic slide list (intro → mental → goal-per-break-reason → days-used →
 * experiment-gated feedback slide → breakdown → life-context → modAbs
 * [→ moderation] → loading) and, on completion, submits the "life" assessment,
 * resumes the core program, stamps the break's post_assessment_* fields, and
 * pushes the program state.
 */
class PostAssessmentViewModel(
    val userInfo: UserInfo,
    val program: Program,
    val currentBreak: ProgramBreak,
    private val experimentController: ExperimentController,
    val completion: () -> Unit,
) {
    var currentIndex by mutableIntStateOf(0)
    var assessmentDone by mutableStateOf(false)
    /** Set by the coach-referral slide — opens the booking sheet after the flow. */
    var showCoachReferralAfter by mutableStateOf(false)

    val slides = mutableStateListOf<AssessmentSlide>()
    val responses = mutableStateMapOf<String, ProgramAssessmentResponse>()

    init {
        val goalQuestions = currentBreak
            .getMultiAssessmentResponses(AssessmentQuestionID.BREAK_REASON.raw)
            .map { reason ->
                PostAssessmentQuestions.goalTemplate.copy(
                    strippedPrompt = "${reason.replace(" ", "_")}-Met",
                    prompt2 = PostAssessmentQuestions.goalTemplate.prompt2 + "__${reason}__?",
                )
            }
        slides.add(AssessmentSlide.Information(PostAssessmentQuestions.introSlide(currentBreak)))
        slides.add(AssessmentSlide.Question(PostAssessmentQuestions.mental))
        goalQuestions.forEach { slides.add(AssessmentSlide.Question(it)) }
        slides.add(AssessmentSlide.Question(PostAssessmentQuestions.daysUsed))
    }

    fun handleSlideCompletion(slide: AssessmentSlide, index: Int) {
        when (slide) {
            is AssessmentSlide.Question -> {
                handleQuestionCompletion(slide.question, index)
                Logger.logEvent(
                    userInfo.loggingID,
                    LogEventType.completedAssessmentQuestion,
                    mapOf(
                        LogEventExtraDataType.ASSESSMENT_QUESTION to slide.question.strippedPrompt,
                        LogEventExtraDataType.FROM to "post_assessment",
                    ),
                )
            }
            is AssessmentSlide.Information -> {
                when (slide.data.id) {
                    AssessmentInfoDataID.postAssessmentInterview,
                    AssessmentInfoDataID.postAssessmentTestimonial,
                    AssessmentInfoDataID.postAssessmentCoachReferral,
                    -> addBreakdown(index)
                    else -> {}
                }
                slide.data.id?.let {
                    Logger.logEvent(
                        userInfo.loggingID,
                        LogEventType.completedAssessmentSlide,
                        mapOf(
                            LogEventExtraDataType.TYPE to it.raw,
                            LogEventExtraDataType.FROM to "post_assessment",
                        ),
                    )
                }
            }
        }
        currentIndex = index + 1
    }

    private fun handleQuestionCompletion(question: ProgramAssessmentQuestion, index: Int) {
        val response = responses[question.strippedPrompt] ?: return

        when (question.strippedPrompt) {
            AssessmentQuestionID.DAYS_USING.raw -> {
                val responseInt = response.question.options
                    .getOrNull(response.responses.firstOrNull() ?: -1)?.toIntOrNull() ?: return
                val slidesToAdd = mutableListOf<AssessmentSlide>()

                // ONE of coach-referral / testimonial / comments, by experiment
                // priority (iOS PostAssessmentViewModel:130-160). The interview
                // branch is hardcoded OUT (§17-Q22): prod has no
                // post-assessment-interview row, so the fallback(true) was
                // showing it to everyone.
                when {
                    experimentController.showFeature(ExperimentKey.postAssessmentCoachReferral) ->
                        slidesToAdd.add(AssessmentSlide.Information(PostAssessmentQuestions.coachReferralSlide))
                    experimentController.showFeature(ExperimentKey.postAssessmentTestimonial) ->
                        slidesToAdd.add(AssessmentSlide.Information(PostAssessmentQuestions.testimonialSlide))
                    else ->
                        slidesToAdd.add(AssessmentSlide.Question(PostAssessmentQuestions.comments))
                }

                if (responseInt > 8) {
                    slidesToAdd.add(0, AssessmentSlide.Question(PostAssessmentQuestions.usedLess))
                }

                removePendingSlides(index)
                slides.addAll(slidesToAdd)
            }

            AssessmentQuestionID.COMMENTS.raw -> addBreakdown(index)

            AssessmentQuestionID.LO_USE_STATE.raw -> {
                removePendingSlides(index)
                if (response.responses.firstOrNull() == 1) {
                    slides.add(AssessmentSlide.Question(PostAssessmentQuestions.moderation))
                }
                slides.add(AssessmentSlide.Information(PostAssessmentQuestions.loadingSlide))
            }
        }
    }

    private fun removePendingSlides(index: Int) {
        while (slides.size > index + 1) slides.removeAt(slides.lastIndex)
    }

    private fun addBreakdown(index: Int) {
        removePendingSlides(index)
        // The breakdown reads some of these answers before submission (iOS).
        currentBreak.postAssessmentResponses = responses.values.toList()
        slides.add(AssessmentSlide.Information(PostAssessmentQuestions.breakdownSlide))
        slides.add(AssessmentSlide.Information(PostAssessmentQuestions.lifeContextSlide))
        slides.add(AssessmentSlide.Question(AssessmentQuestions.modAbs))
    }

    suspend fun logCoachReferralEvent(eventType: String) {
        SupabaseController.logCoachReferralEvent(userInfo.userID, eventType)
    }

    /** iOS `handleAssessmentDone` — returns an error message or null. */
    suspend fun handleAssessmentDone(): String? {
        assessmentDone = true
        val responsesList = responses.values.toList()

        val (responseID, submitError) = SupabaseController.submitAssessment(AssessmentType.Life.string, responsesList)
        if (submitError != null) {
            assessmentDone = false
            return "Could not submit assessment. ${submitError.message}"
        }

        ProgramTimelineHandler.resumeCore(program, userInfo.name)?.let {
            assessmentDone = false
            return it
        }

        currentBreak.postAssessmentResponseID = responseID?.toInt()
        currentBreak.postAssessmentResponses = responsesList
        currentBreak.postAssessmentCompleted = true
        program.coreModeration = responsesList
            .firstOrNull { it.question.strippedPrompt == AssessmentQuestionID.LO_USE_STATE.raw }
            ?.responses?.firstOrNull() == 1
        runCatching { Clear30Store.save(program) }
        // resumeCore's internal finish already synced once, but the break's
        // post_assessment_* fields were stamped AFTER — push again (iOS
        // pushProgramData at the end of handleAssessmentDone).
        SupabaseController.syncProgramState(program)
        return null
    }
}

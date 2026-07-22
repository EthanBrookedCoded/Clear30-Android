package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RemoteAssessmentQuestion — ported from ProgramAssessment.swift:325. A
 * backend-defined multiple-choice question (`programs.remote_assessment_questions`)
 * injected into the onboarding assessment right after the slide whose id matches
 * [afterQuestionId] (question `strippedPrompt` or info-slide id raw).
 */
@Serializable
data class RemoteAssessmentQuestion(
    val id: Int,
    @SerialName("after_question_id") val afterQuestionId: String,
    @SerialName("stripped_prompt") val strippedPrompt: String,
    val prompt1: String,
    val prompt2: String,
    val options: List<String>,
    @SerialName("displayed_options") val displayedOptions: List<String>? = null,
    @SerialName("image_names") val imageNames: List<String>? = null,
    val badges: List<String>? = null,
    val subtexts: List<String>? = null,
    val min: Int = 1,
    val max: Int = 1,
    val enabled: Boolean = true,
    @SerialName("randomize_options") val randomizeOptions: Boolean = false,
) {
    fun toProgramAssessmentQuestion() = ProgramAssessmentQuestion(
        questionNumber = 0, // not used for remote questions
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = strippedPrompt,
        prompt1 = prompt1,
        prompt2 = prompt2,
        options = options,
        // Keep displayedOptions in sync with options (iOS validation).
        displayedOptions = (displayedOptions ?: options).take(options.size),
        imageNames = imageNames,
        badges = badges,
        subtexts = subtexts,
        affirmations = emptyList(),
        autoAddAffirmation = true,
        min = min,
        max = max,
        shuffled = randomizeOptions,
    )
}

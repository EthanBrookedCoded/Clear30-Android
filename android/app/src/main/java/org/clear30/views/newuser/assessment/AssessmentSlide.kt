package org.clear30.views.newuser.assessment

import org.clear30.data.model.AssessmentInfoData
import org.clear30.data.model.ProgramAssessmentQuestion

/** A single assessment slide — ported from the Swift `AssessmentSlide` enum. */
sealed interface AssessmentSlide {
    data class Information(val data: AssessmentInfoData) : AssessmentSlide
    data class Question(val question: ProgramAssessmentQuestion) : AssessmentSlide
}

/**
 * Slide + its completion handler (Swift `AssessmentSlideWithCompletion`).
 * The completion runs when the user leaves the slide, receiving the view model
 * and whether they chose the primary option — matching the iOS closure signature.
 */
data class AssessmentSlideWithCompletion(
    val slide: AssessmentSlide,
    val completion: ((AssessmentViewModel, Boolean) -> Unit)? = null,
)

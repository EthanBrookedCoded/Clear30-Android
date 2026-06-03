package org.clear30.views.newuser.assessment

import org.clear30.data.model.AssessmentInfoData
import org.clear30.data.model.ProgramAssessmentQuestion

/** A single assessment slide — ported from the Swift `AssessmentSlide` enum. */
sealed interface AssessmentSlide {
    data class Information(val data: AssessmentInfoData) : AssessmentSlide
    data class Question(val question: ProgramAssessmentQuestion) : AssessmentSlide
}

/** Slide + its completion handler (Swift `AssessmentSlideWithCompletion`). */
data class AssessmentSlideWithCompletion(
    val slide: AssessmentSlide,
    val completion: (() -> Unit)? = null,
)

package org.clear30.views.newuser.assessment

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.AssessmentInfo
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.ExperimentKey
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.UserInfo
import org.clear30.util.now
import org.clear30.views.theme.Haptics

/**
 * AssessmentViewModel — ported from AssessmentViewModel.swift.
 *
 * Drives the slide sequence, collects responses, logs question/slide events, and
 * saves the assembled [AssessmentInfo] onto [OnboardingSetup]. The slide-content
 * builders (`AssessmentSlides2`/`AssessmentSlides3` — the entire branching
 * question script) are a large dedicated port; [addInitialSlides]/[addNextSlides]
 * are stubs for now, so a freshly-built flow completes through to saveAssessment.
 */
class AssessmentViewModel(
    private val userInfo: UserInfo,
    private val onboardingSetup: OnboardingSetup,
    val experimentController: ExperimentController,
    val scope: CoroutineScope,
    private val completion: () -> Unit,
) {
    var currentIndex by mutableIntStateOf(0)
    val slidesWithCompletions = mutableStateListOf<AssessmentSlideWithCompletion>()
    val responses = mutableMapOf<String, ProgramAssessmentResponse>()
    var hasLeftFirstSlide = false

    var name: String = ""
    var choseClear30: Boolean = true
    var lastSmokedDate: Instant = now()

    val useNewOnboarding: Boolean
        get() = experimentController.showFeature(ExperimentKey.newOnboarding, fallback = true)

    /** Approximate progress-bar length (AssessmentViewModel.progressBarEstimate). */
    val progressBarEstimate: Int
        get() {
            // Same numbers as prod iOS (short flow: bio-sex/symptoms never show,
            // so their +1s are dropped rather than experiment-read, §17-Q22).
            var base = if (useNewOnboarding) 24 else 25
            if (experimentController.showFeature(ExperimentKey.assessmentUsageDuration, false)) base += 1
            base += 2 // commitment question + affirmation
            return base
        }

    init { addInitialSlides() }

    private fun addInitialSlides() {
        AssessmentSlides3.addInitialSlides(this)
    }

    fun handleSlideCompletion(
        slide: AssessmentSlideWithCompletion,
        questionOverride: ProgramAssessmentQuestion?,
        index: Int,
        chosePrimaryOption: Boolean,
    ) {
        hasLeftFirstSlide = true

        AssessmentSlides3.addNextSlides(slide, index, this, chosePrimaryOption)

        // Completion logging
        when (val s = slide.slide) {
            is AssessmentSlide.Question -> Logger.logEvent(
                userInfo.loggingID, LogEventType.completedAssessmentQuestion,
                mapOf(LogEventExtraDataType.ASSESSMENT_QUESTION to s.question.strippedPrompt),
            )
            is AssessmentSlide.Information -> s.data.id?.let {
                Logger.logEvent(userInfo.loggingID, LogEventType.completedAssessmentSlide, mapOf(LogEventExtraDataType.TYPE to it.raw))
            }
        }

        // Advance, or finish. A light haptic on each slide transition gives the
        // onboarding "swipe" between slides a tactile tick (iOS lightImpact on
        // every page change).
        if (index + 1 < slidesWithCompletions.size) {
            Haptics.lightImpact()
            currentIndex = index + 1
        } else {
            saveAssessment()
        }
    }

    private fun saveAssessment() {
        onboardingSetup.assessmentInfo = AssessmentInfo(
            responses = responses.values.toList(),
            lastSmoked = lastSmokedDate,
            choseClear30 = choseClear30,
        )
        userInfo.name = name
        scope.launch {
            Clear30Store.save(onboardingSetup)
            Clear30Store.save(userInfo)
            completion()
        }
    }
}

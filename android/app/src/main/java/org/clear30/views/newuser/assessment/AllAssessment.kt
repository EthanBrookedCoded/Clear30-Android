package org.clear30.views.newuser.assessment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.UserInfo
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * AllAssessment — ported from AllAssessment.swift. Pages through the slide
 * sequence, rendering each question via [AssessmentQuestionView] and recording
 * responses on the [AssessmentViewModel].
 *
 * The slide script (AssessmentSlides3) populates the sequence, so the real
 * questions/affirmations render here; the empty branch is just a safety net.
 */
@Composable
fun AllAssessment(
    userInfo: UserInfo,
    onboardingSetup: OnboardingSetup,
    experimentController: ExperimentController,
    scope: CoroutineScope,
    onComplete: () -> Unit,
    onBackFromFirst: (() -> Unit)? = null,
) {
    val vm = remember {
        AssessmentViewModel(userInfo, onboardingSetup, experimentController, scope, onComplete)
    }

    if (vm.slidesWithCompletions.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(Dimens.horizontalPadding),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Heading2("Assessment")
            SmallText("Loading your assessment…", Modifier.padding(vertical = 8.dp))
            DefaultButton("Continue", gradient = Clear30Gradients.clear30, onClick = onComplete)
        }
        return
    }

    val index = vm.currentIndex
    val swc = vm.slidesWithCompletions[index]
    Column(Modifier.fillMaxSize()) {
        AssessmentHeader(
            index = index,
            estimate = vm.progressBarEstimate,
            onBack = { if (index > 0) vm.currentIndex = index - 1 },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
        when (val slide = swc.slide) {
            is AssessmentSlide.Question -> AssessmentQuestionView(slide.question) { result ->
                // Mirrors the iOS pattern: free-text / date / number answers are
                // stuffed into question.options[0] with responses=[0] so they
                // serialise the same way as a chosen-option answer; the actual
                // value is preserved as a stringified entry in options.
                val prompt = slide.question.strippedPrompt
                vm.responses[prompt] = when (result) {
                    is AssessmentQuestionResult.MultipleChoice ->
                        ProgramAssessmentResponse(slide.question, result.responses)
                    is AssessmentQuestionResult.Input ->
                        ProgramAssessmentResponse(slide.question.copy(options = listOf(result.value)), listOf(0))
                    is AssessmentQuestionResult.DateAnswer -> {
                        // Lift the picked Instant into vm.lastSmokedDate — this is
                        // what feeds program.lastSmoked and the home "time clear"
                        // timer. Without this, the date was being thrown away.
                        result.date?.let { vm.lastSmokedDate = it }
                        val opt = result.date?.toString().orEmpty()
                        ProgramAssessmentResponse(
                            slide.question.copy(options = listOf(opt)),
                            if (result.date != null) listOf(0) else emptyList(),
                        )
                    }
                    is AssessmentQuestionResult.Number ->
                        // Carry the integer as a stringified option — keeps
                        // responses semantically a list of option indices and
                        // preserves the actual number for analytics / paywall traits.
                        ProgramAssessmentResponse(
                            slide.question.copy(options = listOf(result.value.toString())),
                            listOf(0),
                        )
                }
                vm.handleSlideCompletion(swc, slide.question, index, chosePrimaryOption = true)
            }
            is AssessmentSlide.Information -> {
                // Full info-slide renderer (AssessmentInfoSlide.kt) dispatches
                // on the named id (socialProof / credibility / greeting /
                // recommendation etc.) to a richer presentation than the iOS
                // generic info slide.
                AssessmentInfoSlide(
                    data = slide.data,
                    onPrimary = { vm.handleSlideCompletion(swc, null, index, chosePrimaryOption = true) },
                    onSecondary = slide.data.secondaryButtonText?.let {
                        { vm.handleSlideCompletion(swc, null, index, chosePrimaryOption = false) }
                    },
                )
            }
        }
        }
    }
}

/**
 * Assessment header — back button + progress bar, both hidden on the first slide.
 * The iOS PagingView header equivalent, built from [IconButton] + [InfiniteProgressBar].
 */
@Composable
private fun AssessmentHeader(index: Int, estimate: Int, onBack: () -> Unit) {
    val shown = index > 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.horizontalPadding)
            .padding(top = Dimens.headingTopPadding, bottom = Dimens.headingTopPadding * 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        org.clear30.views.components.IconButton(
            "chevron.backward",
            modifier = Modifier.alpha(if (shown) 1f else 0f),
        ) { if (shown) onBack() }
        Spacer(Modifier.width(Dimens.cardSpacing))
        Box(Modifier.weight(1f).alpha(if (shown) 1f else 0f)) {
            org.clear30.views.components.InfiniteProgressBar(
                currentProgress = index,
                maxEstimate = estimate,
                height = 8.dp,
            )
        }
    }
}

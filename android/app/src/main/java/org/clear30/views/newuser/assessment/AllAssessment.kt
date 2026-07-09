package org.clear30.views.newuser.assessment

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.OnboardingSetup
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.UserInfo
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
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

    // iOS `makeCurrentBackgroundGradient`: every info slide whose
    // overrideBackgroundGradient is not false renders on the full green gradient
    // with white content + a hidden progress bar.
    fun isGradient(i: Int): Boolean {
        val data = (vm.slidesWithCompletions[i].slide as? AssessmentSlide.Information)?.data
        return data != null && data.overrideBackgroundGradient != false
    }
    val makeGradient = isGradient(index)

    Box(Modifier.fillMaxSize()) {
        // Full-screen background that fades between the white app background and
        // the green gradient as we move between question and info slides, so the
        // colour change isn't an abrupt snap mid-transition.
        Crossfade(targetState = makeGradient, animationSpec = Anim.default(), label = "assessmentBg") { green ->
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (green) Modifier.background(Clear30Gradients.clear30) else Modifier),
            )
        }

        // Inset the content for the status bar (top) and navigation bar (bottom)
        // while the gradient bleeds full-screen behind. Without this the primary
        // button fell under the nav bar ("off the screen a little").
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            CompositionLocalProvider(
                LocalContentColor provides if (makeGradient) Color.White else Clear30Colors.text,
            ) {
                AssessmentHeader(
                    index = index,
                    estimate = vm.progressBarEstimate,
                    hideProgress = makeGradient,
                    onBack = {
                        if (index > 0) {
                            org.clear30.views.theme.Haptics.lightImpact()
                            vm.currentIndex = index - 1
                        }
                    },
                )
            }
            // iOS applies .padding(.vertical, headingTopPadding) to each slide.
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.headingTopPadding)) {
                // Push the outgoing slide off to the left (and the new one in from
                // the right) when advancing; reverse it when stepping back. The
                // fade keeps the colour cross-over (white ↔ green slides) gentle.
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        val forward = targetState >= initialState
                        val dir = if (forward) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        // Springy slide + a gentle scale-pop so slides feel smooth
                        // and playful (a soft bounce, not a flat tween).
                        val slideSpec = spring<IntOffset>(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                        val scaleSpec = spring<Float>(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMediumLow,
                        )
                        val fadeSpec = tween<Float>(SLIDE_MILLIS, easing = FastOutSlowInEasing)
                        (slideIntoContainer(dir, slideSpec) + fadeIn(fadeSpec) + scaleIn(initialScale = 0.92f, animationSpec = scaleSpec))
                            .togetherWith(slideOutOfContainer(dir, slideSpec) + fadeOut(fadeSpec) + scaleOut(targetScale = 0.92f, animationSpec = scaleSpec))
                    },
                    label = "assessmentSlide",
                ) { i ->
                    val swc = vm.slidesWithCompletions[i]
                    val slideGradient = isGradient(i)
                    CompositionLocalProvider(
                        LocalContentColor provides if (slideGradient) Color.White else Clear30Colors.text,
                    ) {
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
                                vm.handleSlideCompletion(swc, slide.question, i, chosePrimaryOption = true)
                            }
                            is AssessmentSlide.Information -> {
                                // Faithful AssessmentInfoSlide2 port — base layout + id dispatch
                                // to the rich custom views (social proof / credibility / pain
                                // point / dream outcome / affirmation cards).
                                AssessmentInfoSlide(
                                    data = slide.data,
                                    vm = vm,
                                    onGradient = slideGradient,
                                    onPrimary = { vm.handleSlideCompletion(swc, null, i, chosePrimaryOption = true) },
                                    onSecondary = slide.data.secondaryButtonText?.let {
                                        { vm.handleSlideCompletion(swc, null, i, chosePrimaryOption = false) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SLIDE_MILLIS = 320

/**
 * Assessment header — a centered progress bar with the back chevron pinned to the
 * top-left beside it (both hidden on the first slide). iOS PagingView equivalent.
 */
@Composable
private fun AssessmentHeader(index: Int, estimate: Int, hideProgress: Boolean, onBack: () -> Unit) {
    val shown = index > 0
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.horizontalPadding)
            .padding(top = Dimens.headingTopPadding, bottom = Dimens.headingTopPadding * 2),
    ) {
        // Progress bar — centered on screen (iOS hides it on green info slides).
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.6f)
                .alpha(if (shown && !hideProgress) 1f else 0f),
        ) {
            org.clear30.views.components.InfiniteProgressBar(
                currentProgress = index,
                maxEstimate = estimate,
                height = 10.dp,
            )
        }
        // Back chevron — top-left, beside the bar; inherits the slide's content
        // color (white on the gradient).
        org.clear30.views.components.IconButton(
            "chevron.backward",
            height = 28.dp,
            padding = 0.dp,
            tint = androidx.compose.material3.LocalContentColor.current,
            modifier = Modifier.align(Alignment.CenterStart).alpha(if (shown) 1f else 0f),
        ) { if (shown) onBack() }
    }
}

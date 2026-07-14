package org.clear30.views.existinguser.postassessment

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.clear30.data.AlertHandler
import org.clear30.data.model.AssessmentInfoDataID
import org.clear30.data.model.ExperimentController
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.ProgramBreak
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.InfiniteProgressBar
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.sfSymbol
import org.clear30.views.newuser.assessment.AssessmentInfoSlide
import org.clear30.views.newuser.assessment.AssessmentQuestionResult
import org.clear30.views.newuser.assessment.AssessmentQuestionView
import org.clear30.views.newuser.assessment.AssessmentSlide
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * PostAssessment — port of PostAssessment.swift: the end-of-Clear30 flow that
 * celebrates the break, collects outcome answers, and lands the user in the
 * Better Life Program. Presented full-screen and non-dismissable (iOS
 * `.interactiveDismissDisabled`); the coach-referral booking sheet follows the
 * flow when the user expressed interest.
 */
@Composable
fun PostAssessment(
    userInfo: UserInfo,
    program: Program,
    currentBreak: ProgramBreak,
    experimentController: ExperimentController,
    completion: () -> Unit,
) {
    var showBookingSheet by remember { mutableStateOf(false) }
    val vm = remember {
        PostAssessmentViewModel(userInfo, program, currentBreak, experimentController) {
            completion()
        }
    }

    Dialog(
        onDismissRequest = { /* interactive dismiss disabled (iOS) */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
            val index = vm.currentIndex

            if (index >= vm.slides.size) {
                PostAssessmentLoadingView(vm) {
                    // iOS onDisappear: open the booking sheet when interested.
                    if (vm.showCoachReferralAfter) showBookingSheet = true else vm.completion()
                }
                return@Box
            }

            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Box(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = Dimens.horizontalPadding)
                        .padding(top = Dimens.headingTopPadding, bottom = Dimens.headingTopPadding),
                ) {
                    Box(Modifier.align(Alignment.Center).fillMaxWidth(0.6f).alpha(if (index > 0) 1f else 0f)) {
                        InfiniteProgressBar(currentProgress = index, maxEstimate = vm.slides.size + 3, height = 10.dp)
                    }
                    IconButton(
                        "chevron.backward",
                        height = 28.dp,
                        padding = 0.dp,
                        modifier = Modifier.align(Alignment.CenterStart).alpha(if (index > 0) 1f else 0f),
                    ) {
                        if (index > 0) {
                            Haptics.lightImpact()
                            vm.currentIndex = index - 1
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.headingTopPadding / 2)) {
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = {
                            val forward = targetState >= initialState
                            val dir = if (forward) {
                                AnimatedContentTransitionScope.SlideDirection.Left
                            } else {
                                AnimatedContentTransitionScope.SlideDirection.Right
                            }
                            val slideSpec = spring<IntOffset>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                            val scaleSpec = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
                            val fadeSpec = tween<Float>(320, easing = FastOutSlowInEasing)
                            (slideIntoContainer(dir, slideSpec) + fadeIn(fadeSpec) + scaleIn(initialScale = 0.92f, animationSpec = scaleSpec))
                                .togetherWith(slideOutOfContainer(dir, slideSpec) + fadeOut(fadeSpec) + scaleOut(targetScale = 0.92f, animationSpec = scaleSpec))
                        },
                        label = "postAssessmentSlide",
                    ) { i ->
                        PostAssessmentSlide(vm, i)
                    }
                }
            }
        }
    }

    if (showBookingSheet) {
        CoachReferralBookingSheet(
            userInfo = userInfo,
            onDismiss = {
                showBookingSheet = false
                vm.completion()
            },
        )
    }
}

@Composable
private fun PostAssessmentSlide(vm: PostAssessmentViewModel, index: Int) {
    when (val slide = vm.slides[index]) {
        is AssessmentSlide.Question -> AssessmentQuestionView(slide.question) { result ->
            val prompt = slide.question.strippedPrompt
            vm.responses[prompt] = when (result) {
                is AssessmentQuestionResult.MultipleChoice ->
                    ProgramAssessmentResponse(slide.question, result.responses)
                is AssessmentQuestionResult.Input ->
                    ProgramAssessmentResponse(slide.question.copy(options = listOf(result.value)), listOf(0))
                is AssessmentQuestionResult.DateAnswer ->
                    ProgramAssessmentResponse(
                        slide.question.copy(options = listOf(result.date?.let { PlainDate.from(it).dateString }.orEmpty())),
                        if (result.date != null) listOf(0) else emptyList(),
                    )
                is AssessmentQuestionResult.Number ->
                    ProgramAssessmentResponse(slide.question.copy(options = listOf(result.value.toString())), listOf(0))
            }
            Haptics.lightImpact()
            vm.handleSlideCompletion(slide, index)
        }

        is AssessmentSlide.Information -> when (slide.data.id) {
            AssessmentInfoDataID.postAssessmentWelcome -> PostAssessmentIntro(
                vm = vm,
                buttonText = slide.data.primaryButtonText,
                onNext = { vm.handleSlideCompletion(slide, index) },
            )
            AssessmentInfoDataID.postAssessmentBreakdown -> PostAssessmentBreakdown(
                vm = vm,
                buttonText = slide.data.primaryButtonText,
                onNext = { vm.handleSlideCompletion(slide, index) },
            )
            AssessmentInfoDataID.postAssessmentLifeContext -> PostAssessmentLifeContext(
                data = slide.data,
                onNext = { vm.handleSlideCompletion(slide, index) },
            )
            AssessmentInfoDataID.postAssessmentCoachReferral -> PostAssessmentCoachReferral(
                vm = vm,
                onNext = { vm.handleSlideCompletion(slide, index) },
            )
            AssessmentInfoDataID.postAssessmentTestimonial -> PostAssessmentTestimonial(
                title = slide.data.title,
                onNext = { vm.handleSlideCompletion(slide, index) },
            )
            AssessmentInfoDataID.postAssessmentInterview -> PostAssessmentInterview(
                title = slide.data.title,
                onNext = { vm.handleSlideCompletion(slide, index) },
            )
            else -> AssessmentInfoSlide(
                data = slide.data,
                vm = null,
                onGradient = false,
                onPrimary = { vm.handleSlideCompletion(slide, index) },
            )
        }
    }
}

/**
 * Terminal loading slide — runs [PostAssessmentViewModel.handleAssessmentDone]
 * once: spinner → "Welcome to the Better Life Program" + Done. On error: alert
 * + step back to the last question to retry.
 */
@Composable
private fun PostAssessmentLoadingView(vm: PostAssessmentViewModel, onDone: () -> Unit) {
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val error = vm.handleAssessmentDone()
        if (error != null) {
            AlertHandler.error(message = error)
            vm.currentIndex = (vm.slides.size - 1).coerceAtLeast(0)
        } else {
            done = true
        }
    }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!done) {
            CircularProgressIndicator()
            Heading3("Getting your new program ready")
            SmallText("This only takes a moment.", color = Clear30Colors.text.copy(alpha = 0.5f))
        } else {
            androidx.compose.material3.Icon(
                sfSymbol("checkmark.circle.fill"),
                contentDescription = null,
                tint = Clear30Colors.green,
                modifier = Modifier.padding(bottom = Dimens.cardSpacing / 2),
            )
            Heading3("Welcome to the Better Life Program 🍃")
            StretchedButton("Done", gradient = Clear30Gradients.clear30) { onDone() }
        }
    }
}

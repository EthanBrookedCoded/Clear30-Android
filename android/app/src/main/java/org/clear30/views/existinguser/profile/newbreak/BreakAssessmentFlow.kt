package org.clear30.views.existinguser.profile.newbreak

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
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.clear30.data.AlertHandler
import org.clear30.data.AssessmentSubmissionHandler
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30FullScreenCover
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
import org.clear30.views.theme.Anim
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * BreakAssessmentFlow — the Profile "New Break" flow (iOS BreakAssessment.swift):
 * the full break assessment ([newBreakType] + newClear30Questions with
 * affirmations), then a "Setting up your break" terminal screen that runs
 * `AssessmentSubmissionHandler.handleNewBreak` (submit under "clear30", capture
 * response ID + normative feedback, splice the new content in). Replaces the
 * old name-only NewBreakSheet.
 */
@Composable
fun BreakAssessmentFlow(
    userInfo: UserInfo,
    program: Program,
    onDismiss: () -> Unit,
    onCreated: () -> Unit,
) {
    val vm = remember { BreakAssessmentViewModel(userInfo, program) }

    Clear30FullScreenCover(onDismiss = { if (!vm.assessmentDone) onDismiss() }) {
        Box(Modifier.fillMaxSize()) {
            val index = vm.currentIndex

            if (index >= vm.slides.size) {
                NewBreakLoadingView(vm, onCreated)
                return@Box
            }

            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                // Header: progress bar + back chevron (back on the first slide dismisses).
                Box(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = Dimens.horizontalPadding)
                        .padding(top = Dimens.headingTopPadding, bottom = Dimens.headingTopPadding * 2),
                ) {
                    Box(Modifier.align(Alignment.Center).fillMaxWidth(0.6f).alpha(if (index > 0) 1f else 0f)) {
                        InfiniteProgressBar(
                            currentProgress = index,
                            maxEstimate = vm.slides.size + 1,
                            height = 10.dp,
                        )
                    }
                    IconButton(
                        "chevron.backward",
                        height = 28.dp,
                        padding = 0.dp,
                        tint = LocalContentColor.current,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Haptics.lightImpact()
                        if (index > 0) vm.currentIndex = index - 1 else onDismiss()
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = Dimens.headingTopPadding)) {
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
                        label = "breakAssessmentSlide",
                    ) { i ->
                        when (val slide = vm.slides[i]) {
                            is AssessmentSlide.Question -> AssessmentQuestionView(slide.question) { result ->
                                val prompt = slide.question.strippedPrompt
                                vm.responses[prompt] = when (result) {
                                    is AssessmentQuestionResult.MultipleChoice ->
                                        ProgramAssessmentResponse(slide.question, result.responses)
                                    is AssessmentQuestionResult.Input ->
                                        ProgramAssessmentResponse(slide.question.copy(options = listOf(result.value)), listOf(0))
                                    is AssessmentQuestionResult.DateAnswer -> {
                                        // Serialized as the yyyy-MM-dd assessment
                                        // date string (iOS date.assessmentDateString)
                                        // — handleBreaks/handleNewBreak parse it back.
                                        val opt = result.date
                                            ?.let { org.clear30.data.model.PlainDate.from(it).dateString }
                                            .orEmpty()
                                        ProgramAssessmentResponse(
                                            slide.question.copy(options = listOf(opt)),
                                            if (result.date != null) listOf(0) else emptyList(),
                                        )
                                    }
                                    is AssessmentQuestionResult.Number ->
                                        ProgramAssessmentResponse(slide.question.copy(options = listOf(result.value.toString())), listOf(0))
                                }
                                Haptics.lightImpact()
                                vm.handleQuestionCompletion(slide.question, i)
                            }
                            is AssessmentSlide.Information -> AssessmentInfoSlide(
                                data = slide.data,
                                vm = null,
                                onGradient = false,
                                onPrimary = {
                                    Haptics.lightImpact()
                                    vm.handleInfoCompletion(slide.data, i)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Terminal "Setting up your break" screen (iOS AccountSetupView usage in
 * BreakAssessment): runs handleNewBreak once, spinner → checkmark + Done. On
 * error: alert + step back to the last question so the user can retry.
 */
@Composable
private fun NewBreakLoadingView(vm: BreakAssessmentViewModel, onCreated: () -> Unit) {
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val breakType = vm.breakType
        if (breakType == null) {
            AlertHandler.error(message = "No break selected.")
            vm.currentIndex = 0
            return@LaunchedEffect
        }
        vm.assessmentDone = true
        val error = AssessmentSubmissionHandler.handleNewBreak(
            userInfo = vm.userInfo,
            program = vm.program,
            breakType = breakType,
            responses = vm.responses.values.toList(),
        )
        if (error != null) {
            AlertHandler.error(message = error)
            vm.assessmentDone = false
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
            Heading3("Setting up your break")
            SmallText("This only takes a moment.", color = Clear30Colors.text.copy(alpha = 0.5f))
        } else {
            androidx.compose.material3.Icon(
                sfSymbol("checkmark.circle.fill"),
                contentDescription = null,
                tint = Clear30Colors.green,
                modifier = Modifier.padding(bottom = Dimens.cardSpacing / 2),
            )
            Heading3("Your break is ready!")
            StretchedButton("Done", gradient = Clear30Gradients.clear30) { onCreated() }
        }
    }
}

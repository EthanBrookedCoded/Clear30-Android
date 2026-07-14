package org.clear30.views.existinguser.midpilot

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.clear30.data.model.AssessmentInfoData
import org.clear30.data.model.AssessmentInfoDataID
import org.clear30.data.model.AssessmentQuestionType
import org.clear30.data.model.ProgramAssessmentQuestion
import org.clear30.data.model.UserInfo
import org.clear30.views.components.IconButton
import org.clear30.views.components.InfiniteProgressBar
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.newuser.assessment.AssessmentInfoSlide
import org.clear30.views.newuser.assessment.AssessmentInput
import org.clear30.views.newuser.assessment.AssessmentMultipleChoice
import org.clear30.views.newuser.assessment.AssessmentSlider
import org.clear30.views.newuser.assessment.AssessmentSpectrum
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

private const val TOTAL_PAGES = 8

/**
 * MidPilotAssessment — port of MidPilotAssessment.swift: the school-pilot
 * "Quick Check-In 📋", 8 slides presented as a non-dismissable full-screen
 * dialog once a school user has 10+ checked-in days
 * (TodayFeedView.swift:290-300). Submission failure offers "Skip for Now".
 */
@Composable
fun MidPilotAssessment(userInfo: UserInfo, completion: () -> Unit) {
    val vm = remember { MidPilotAssessmentViewModel(userInfo, completion) }
    val scope = rememberCoroutineScope()

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

            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                // Header: back chevron (not on the first page) + progress.
                Box(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = Dimens.horizontalPadding)
                        .padding(top = Dimens.headingTopPadding, bottom = Dimens.headingTopPadding * 2),
                ) {
                    Box(Modifier.align(Alignment.Center).fillMaxWidth(0.6f).alpha(if (index > 0) 1f else 0f)) {
                        InfiniteProgressBar(currentProgress = index, maxEstimate = TOTAL_PAGES, height = 10.dp)
                    }
                    IconButton(
                        "chevron.backward",
                        height = 28.dp,
                        padding = 0.dp,
                        modifier = Modifier.align(Alignment.CenterStart).alpha(if (index > 0) 1f else 0f),
                    ) {
                        if (index > 0 && !vm.isSubmitting) {
                            Haptics.lightImpact()
                            vm.currentIndex = index - 1
                        }
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
                        label = "midPilotSlide",
                    ) { i ->
                        MidPilotPage(vm, i, onSubmit = { scope.launch { vm.submitAssessment() } })
                    }
                }
            }

            if (vm.isSubmitting) {
                Box(
                    Modifier.fillMaxSize().background(Clear30Colors.background),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun MidPilotPage(vm: MidPilotAssessmentViewModel, index: Int, onSubmit: () -> Unit) {
    fun progress() {
        Haptics.lightImpact()
        vm.currentIndex = index + 1
    }

    when (index) {
        0 -> AssessmentInfoSlide(
            data = AssessmentInfoData(
                id = AssessmentInfoDataID.midPilotAssessment,
                title = "Quick Check-In 📋",
                subtitle = "How are things going?",
                body = "Your school partnered with Clear30 to support you. This quick assessment helps us understand your experience and improve the program.\n\nIt only takes about a minute.",
                systemImageName = "chart.bar.doc.horizontal.fill",
                primaryButtonText = "Let's Go",
                primaryButtonIcon = "arrow.right",
                overrideBackgroundGradient = false,
            ),
            vm = null,
            onGradient = false,
            onPrimary = { progress() },
        )

        1 -> AssessmentMultipleChoice(
            question = midPilotQuestion(
                prompt2 = "Have You Experienced Any of These?",
                options = vm.withdrawalOptions,
                max = vm.withdrawalOptions.size,
            ),
        ) { selected ->
            vm.withdrawalSymptoms = selected
            progress()
        }

        2 -> MidPilotWellbeingSliders { sleep, anxiety, focus ->
            vm.wellbeingSleep = sleep
            vm.wellbeingAnxiety = anxiety
            vm.wellbeingFocus = focus
            progress()
        }

        3 -> AssessmentSpectrum(
            question = midPilotQuestion(
                prompt2 = "How Helpful Has Clear30 Been?",
                options = vm.helpfulnessEmojis,
                max = 1,
            ),
            left = "1",
            middle = "",
            right = "10",
            showDots = true,
        ) { value ->
            vm.helpfulness = value + 1
            progress()
        }

        4 -> AssessmentInput(
            question = midPilotQuestion(prompt2 = "💡 Any Unexpected Benefits?", options = emptyList(), max = 1),
            placeholder = "Share your experience...",
            multiLine = true,
            optional = true,
        ) { text ->
            vm.unexpectedValue = text
            progress()
        }

        5 -> AssessmentSlider(
            question = midPilotQuestion(
                prompt2 = "💪 How Committed Are You?",
                options = (1..10).map { "$it" },
                max = 10,
            ),
            valueLabel = "",
        ) { value ->
            vm.commitment = value
            progress()
        }

        6 -> AssessmentMultipleChoice(
            question = midPilotQuestion(
                prompt1 = "We want to make sure you have all the support you need",
                prompt2 = "Since Starting Clear30...",
                options = vm.campusWellnessOptions,
                max = vm.campusWellnessOptions.size,
            ),
            exclusiveOptionIndices = listOf(vm.campusWellnessOptions.size - 1),
        ) { selected ->
            vm.campusWellness = selected
            progress()
        }

        7 -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AssessmentSlider(
                    question = midPilotQuestion(
                        prompt2 = "🗣️ How Likely Would You Be to Recommend Clear30?",
                        options = (1..10).map { "$it" },
                        max = 10,
                    ),
                    valueLabel = "",
                ) { value ->
                    vm.recommendation = value
                    onSubmit()
                }
            }
            if (vm.submissionError) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SmallText("Something went wrong.", color = Clear30Colors.red1)
                    TinyText(
                        "Skip for Now",
                        Modifier.padding(top = Dimens.cardSpacing / 2).pressScale { vm.completion() },
                    )
                }
            }
        }
    }
}

/** Ad-hoc question wrapper for the shared renderers (which take a question model). */
private fun midPilotQuestion(
    prompt1: String = "",
    prompt2: String,
    options: List<String>,
    max: Int,
): ProgramAssessmentQuestion = ProgramAssessmentQuestion(
    questionNumber = 0,
    type = AssessmentQuestionType.MultipleChoice,
    strippedPrompt = prompt2,
    prompt1 = prompt1,
    prompt2 = prompt2,
    options = options,
    displayedOptions = options.takeIf { it.isNotEmpty() },
    min = 1,
    max = max,
)

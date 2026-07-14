package org.clear30.views.existinguser.postassessment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.clear30.R
import org.clear30.data.model.AssessmentInfoData
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.PostAssessmentQuestions
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.logCoachReferralEvent
import org.clear30.views.components.ConfettiOverlay
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.InlineVideoPlayer
import org.clear30.views.components.SmallText
import org.clear30.views.components.SmallTextMarkdown
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.TinyText
import org.clear30.views.components.cardStyle
import org.clear30.views.components.pressScale
import org.clear30.views.existinguser.profile.ProfileSnakeCalendar
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Haptics

/**
 * Post-assessment slide composables — the Android counterparts of the iOS
 * `customView`s in PostAssessmentAbstracted.swift. The 3D-coin achievement
 * slide is intentionally NOT ported (Thatcher: skip, no substitute).
 */

// MARK: - Popup card (feed / profile entry point)

/**
 * The "<Break> breakdown 📦" card shown on the Today feed and Profile while the
 * post-assessment is pending (iOS `PopupCard(postAssessmentCardText)` —
 * PopUps.swift:31-33, Profile.swift:181-184).
 */
@Composable
fun PostAssessmentPopupCard(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.pressScale(onClick = onClick)) {
        Clear30Card(modifier = Modifier.fillMaxWidth(), gradient = Clear30Gradients.clear30) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallText(text)
                androidx.compose.material3.Icon(
                    org.clear30.views.components.sfSymbol("chevron.right"),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// MARK: - Intro (celebration)

@Composable
internal fun PostAssessmentIntro(vm: PostAssessmentViewModel, buttonText: String, onNext: () -> Unit) {
    var confetti by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(250)
        Haptics.successHeavy()
        confetti++
    }

    val numDaysSober = vm.program.numDaysSober(vm.currentBreak)
    val numDaysCheckedIn = vm.program.numDaysCheckedIn(vm.currentBreak)
    val delta = vm.program.getDeltaSmokingFrequency(vm.currentBreak) ?: 0
    val name = vm.userInfo.name

    // iOS PostAssessmentIntro.text — branch on the outcome.
    val (title, bodyLines) = when {
        delta == 100 && numDaysCheckedIn == 30 -> "$name, you did it!" to listOf(
            "You accepted the challenge and showed up.",
            "Every. Single. Day.",
            "We couldn't be prouder of you!",
        )
        delta > 50 -> "$name, you did it!" to listOf(
            "🫣 $delta% less smoking.",
            "🥹 $numDaysSober days without weed at all!",
            "🥶 You’ve truly transformed your habits, $name. Huge congratulations!",
        )
        delta in 1..50 -> "\"The unexamined life is not worth living.\" - Socrates" to listOf(
            "$name, you’ve spent the last 30 days checking in with yourself and becoming more aware of your behavior.",
            "This journey toward a better you is something to celebrate.",
            "Congrats on completing Clear30 💚",
        )
        else -> "You did it, $name!" to listOf(
            "For 30 days, you focused on being mindful and intentional with your habits.",
            "That kind of dedication deserves to be celebrated.",
            "Congratulations on completing Clear30 💚",
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Heading3(title, Modifier.padding(bottom = Dimens.cardSpacing))
                SmallText(
                    bodyLines.joinToString("\n\n") +
                        "\n\nAnswer a few quick questions to see your personalized Clear30 Breakdown!",
                    Modifier.padding(bottom = Dimens.cardSpacing * 2),
                )
                ProfileSnakeCalendar(height = 275.dp, program = vm.program, programBreak = vm.currentBreak)
                Spacer(Modifier.height(Dimens.cardSpacing))
            }
            StretchedButton(
                buttonText,
                gradient = Clear30Gradients.clear30,
                modifier = Modifier.padding(vertical = Dimens.cardSpacing),
            ) { onNext() }
        }

        if (confetti > 0) key(confetti) { ConfettiOverlay(Modifier.fillMaxSize(), count = 100) }
    }
}

// MARK: - Breakdown (stats + goals)

@Composable
internal fun PostAssessmentBreakdown(vm: PostAssessmentViewModel, buttonText: String, onNext: () -> Unit) {
    val b = vm.currentBreak
    val program = vm.program
    val numDaysSober = program.numDaysSober(b)
    val numDaysCheckedIn = program.numDaysCheckedIn(b)
    val delta = program.getDeltaSmokingFrequency(b)
    val moneySaved = program.getTotalMoneySavedOverBreak(b)
    val goals = b.getMultiAssessmentResponses(AssessmentQuestionID.BREAK_REASON.raw)

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Heading3("${b.name} breakdown 📦")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                BreakdownStat("Days clear", "$numDaysSober", Modifier.weight(1f))
                BreakdownStat("Check-ins", "$numDaysCheckedIn", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                if (delta != null) BreakdownStat("Less smoking", "$delta%", Modifier.weight(1f))
                if (moneySaved != null) BreakdownStat("Saved", "$$moneySaved", Modifier.weight(1f))
                if (delta == null && moneySaved == null) Spacer(Modifier.weight(1f))
            }

            if (goals.isNotEmpty()) {
                SmallText("Your goals", color = Clear30Colors.text.copy(alpha = 0.5f))
                goals.forEach { goal ->
                    Box(
                        Modifier.fillMaxWidth().cardStyle(
                            color = Clear30Colors.button,
                            shadowColor = androidx.compose.ui.graphics.Color.Transparent,
                            outlineGradient = Clear30Gradients.clear30,
                            outlineOpacity = 0.5f,
                        ),
                    ) { SmallText(goal) }
                }
            }
            Spacer(Modifier.height(Dimens.cardSpacing))
        }
        StretchedButton(
            buttonText,
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.padding(vertical = Dimens.cardSpacing),
        ) { onNext() }
    }
}

@Composable
private fun BreakdownStat(title: String, value: String, modifier: Modifier = Modifier) {
    Clear30Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Heading3(value)
            TinyText(title, color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

// MARK: - Life-context carousel

@Composable
internal fun PostAssessmentLifeContext(data: AssessmentInfoData, onNext: () -> Unit) {
    val slides = PostAssessmentQuestions.lifeContextCarousel
    val pagerState = rememberPagerState(pageCount = { slides.size })

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding)) {
        Heading3(data.title, Modifier.padding(bottom = Dimens.cardSpacing / 2))
        SmallText(
            data.subtitle,
            Modifier.padding(bottom = Dimens.cardSpacing),
            color = Clear30Colors.text.copy(alpha = 0.75f),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = Dimens.cardSpacing,
        ) { page ->
            val info = slides[page]
            Clear30Card(modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    Image(
                        painter = painterResource(carouselDrawable(info.image)),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    SmallText(info.title, textAlign = TextAlign.Center)
                    TinyText(info.body, color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
        }

        // Page dots
        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 3, Alignment.CenterHorizontally),
        ) {
            repeat(slides.size) { i ->
                Box(
                    Modifier.size(7.dp).clip(CircleShape).background(
                        Clear30Colors.text.copy(alpha = if (i == pagerState.currentPage) 0.75f else 0.25f),
                    ),
                )
            }
        }

        StretchedButton(
            "Next",
            gradient = Clear30Gradients.clear30,
            modifier = Modifier.padding(vertical = Dimens.cardSpacing),
        ) { onNext() }
    }
}

private fun carouselDrawable(name: String): Int = when (name) {
    "post_assessment_tracking" -> R.drawable.post_assessment_tracking
    "post_assessment_messages" -> R.drawable.post_assessment_messages
    "post_assessment_community" -> R.drawable.post_assessment_community
    "post_assessment_break" -> R.drawable.post_assessment_break
    else -> R.drawable.feature_accountability
}

// MARK: - Coach referral (Phil)

@Composable
internal fun PostAssessmentCoachReferral(vm: PostAssessmentViewModel, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    var choice by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) { vm.logCoachReferralEvent("shown") }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.CenterVertically),
    ) {
        Heading3("Want a coach in your corner?")

        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            val c = choice
            if (c != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    if (c) {
                        Heading3("Awesome!")
                        SmallText(
                            "We'll show you how to book a free call with Phil after this.",
                            color = Clear30Colors.text.copy(alpha = 0.5f),
                        )
                    } else {
                        Heading3("All good!")
                        SmallText(
                            "We've still got plenty in store for you. Phil's always here if you change your mind.",
                            color = Clear30Colors.text.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                Column {
                    PhilHeader(Modifier.fillMaxWidth().padding(bottom = Dimens.cardSpacing * 2))
                    SmallTextMarkdown(
                        "We'll keep supporting you every step of the way. But if you're looking for more personal, 1-on-1 guidance, meet __Phil__, a cannabis mindset coach who's been helping people just like you since 2022.",
                        Modifier.padding(bottom = Dimens.cardSpacing / 2),
                        color = Clear30Colors.text.copy(alpha = 0.75f),
                    )
                    TinyText("Weekly sessions + support between calls", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
        }

        if (choice == null) {
            StretchedButton("I'm interested 👍", gradient = Clear30Gradients.clear30) {
                choice = true
                vm.showCoachReferralAfter = true
                scope.launch { vm.logCoachReferralEvent("cta_tapped") }
            }
            TinyText(
                "I'm good for now",
                Modifier.fillMaxWidth().pressScale {
                    choice = false
                    vm.showCoachReferralAfter = false
                    scope.launch { vm.logCoachReferralEvent("skipped") }
                }.padding(Dimens.cardSpacing / 2),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
        } else {
            StretchedButton("Next", gradient = Clear30Gradients.clear30) { onNext() }
        }
    }
}

@Composable
private fun PhilHeader(modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Clear30Gradients.meditation))
            Image(
                painterResource(R.drawable.phil),
                contentDescription = "Phil Stile",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(65.dp).clip(CircleShape),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
            Heading3("Phil Stile")
            TinyText("Cannabis Mindset Coach", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

/** The post-flow booking sheet (iOS `CoachReferralBookingSheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachReferralBookingSheet(userInfo: UserInfo, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    fun log(eventType: String) {
        scope.launch { SupabaseController.logCoachReferralEvent(userInfo.userID, eventType) }
    }

    LaunchedEffect(Unit) { log("sheet_shown") }

    ModalBottomSheet(
        onDismissRequest = {
            log("sheet_dismissed")
            onDismiss()
        },
        containerColor = Clear30Colors.background,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(bottom = Dimens.cardSpacing * 2),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PhilHeader()
            SmallText(
                "Book a free 20-minute call to see if 1-on-1 coaching is right for you. No commitment, no pressure.",
                color = Clear30Colors.text.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
            StretchedButton("Book a free call", gradient = Clear30Gradients.clear30) {
                log("book_tapped")
                uriHandler.openUri("https://clearheadedhighs.xyz/phil")
            }
            TinyText(
                "Maybe later",
                Modifier.pressScale {
                    log("sheet_dismissed")
                    onDismiss()
                }.padding(Dimens.cardSpacing / 2),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
        }
    }
}

// MARK: - Testimonial ask

@Composable
internal fun PostAssessmentTestimonial(title: String, onNext: () -> Unit) {
    var choice by remember { mutableStateOf<Boolean?>(null) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = choice
        if (c != null) {
            if (c) {
                Heading3("Awesome ❤️")
                SmallText(
                    // iOS guides into the (unported) VideoTestimonialView after the
                    // flow — Android points at the Support-tab entry instead.
                    "If you ever want to film a testimonial, there's a button on the 'Support' tab.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            } else {
                Heading3("All good!")
                SmallText(
                    "If you ever want to film a testimonial, there's a button on the 'Support' tab.",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
            StretchedButton("Next", gradient = Clear30Gradients.clear30) { onNext() }
        } else {
            Heading3(title, textAlign = TextAlign.Center)
            InlineVideoPlayer(
                uri = "https://m.clear30.org/videos/testimonial.mov",
                modifier = Modifier.fillMaxWidth(),
            )
            StretchedButton("I'm in ✋", gradient = Clear30Gradients.clear30) { choice = true }
            TinyText(
                "Maybe later",
                Modifier.pressScale { choice = false }.padding(Dimens.cardSpacing / 2),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
        }
    }
}

// MARK: - Interview ask ($25 Calendly)

@Composable
internal fun PostAssessmentInterview(title: String, onNext: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing, Alignment.CenterVertically),
    ) {
        Heading3(title)
        SmallTextMarkdown(
            "We’re on a mission to make effective cannabis support accessible to everyone, and __your__ feedback will help us achieve this.",
        )
        Column(Modifier.fillMaxWidth()) {
            StretchedButton("Share Your Experience", gradient = Clear30Gradients.clear30) {
                uriHandler.openUri("https://calendly.com/asher-clear30/30min")
            }
            TinyText(
                "And Earn $25!",
                Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 3),
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
        }
        StretchedButton("Next") { onNext() }
    }
}

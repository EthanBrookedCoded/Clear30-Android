package org.clear30.data.model

/**
 * Post-assessment question/slide templates — ported from
 * PostAssessmentAbstracted.swift. The iOS `customView`-bearing info slides are
 * data-only here; the PostAssessment host dispatches on their
 * [AssessmentInfoDataID] to the matching composable (intro / breakdown /
 * coach-referral / testimonial / interview / life-context).
 */
object PostAssessmentQuestions {

    private val spectrumType = AssessmentQuestionType.Spectrum(left = "No", middle = "Somewhat", right = "Yes")

    val mental = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = spectrumType,
        strippedPrompt = AssessmentQuestionID.MENTAL_HEALTH.raw,
        prompt1 = "Throughout your Clear30,",
        prompt2 = "Did your overall __mental health__ improve?",
        options = listOf("No", "Somewhat", "Yes"),
        displayedOptions = listOf("😖", "🙂", "🤩"),
        min = 1, max = 1,
    )

    /** The VM copies this per break-reason: strippedPrompt "<reason>-Met", prompt2 += "__<reason>__?". */
    val goalTemplate = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = spectrumType,
        strippedPrompt = AssessmentQuestionID.BREAK_REASON.raw,
        prompt1 = "Throughout your Clear30,",
        prompt2 = "Did you reach your goal of ",
        options = listOf("No", "Somewhat", "Yes"),
        displayedOptions = listOf("😖", "🙂", "🤩"),
        min = 1, max = 1,
    )

    val daysUsed = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = AssessmentQuestionType.Number(dayQuestionType = AssessmentDaysType.DaysUsing),
        strippedPrompt = AssessmentQuestionID.DAYS_USING.raw,
        prompt1 = "During your Clear30",
        prompt2 = "How many days did you use cannabis?",
        options = (0..31).map { "$it" },
        min = 1, max = 31,
    )

    val usedLess = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = spectrumType,
        strippedPrompt = AssessmentQuestionID.USED_LESS.raw,
        prompt1 = "On the days you used cannabis,",
        prompt2 = "Did you usually use less weed or lower THC than before?",
        options = listOf("No", "Somewhat", "Yes"),
        displayedOptions = listOf("💨", "😶‍🌫️", "😁"),
        min = 1, max = 1,
    )

    val comments = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = AssessmentQuestionType.Input(multiLine = true, placeholder = "Comments", optional = true),
        strippedPrompt = AssessmentQuestionID.COMMENTS.raw,
        prompt1 = "💬 Help us help more people.",
        prompt2 = "We’re on a mission to make effective cannabis support accessible to everyone, and __your__ feedback will help us achieve this.",
        options = emptyList(),
        min = 1, max = 1,
    )

    val moderation = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.MODERATION_TECH.raw,
        prompt1 = "Regarding your choice of moderation,",
        prompt2 = "Will any of the following be different compared to before you started?",
        options = listOf(
            "I plan to use less frequently",
            "I plan to use lower potency THC",
            "I plan to use later in the day/evening",
            "I plan to take yearly breaks",
            "I plan to only use with friends",
        ),
        displayedOptions = listOf(
            "📉 Less frequent use",
            "🍃 Lower potency THC",
            "🌙 Later day/evening use",
            "📅 Yearly breaks",
            "👥 Social use only",
        ),
        min = 1, max = 1,
    )

    // ─────────── Info slides (custom views dispatched by id in the host) ───────────

    // iOS uses "See ${currentBreak.name} breakdown"; shortened per Thatcher
    // (2026-07-22) — the break name made the button wordy.
    fun introSlide(currentBreak: ProgramBreak) = AssessmentInfoData(
        id = AssessmentInfoDataID.postAssessmentWelcome,
        title = "", subtitle = "", body = "",
        primaryButtonText = "See breakdown",
    )

    val interviewSlide = AssessmentInfoData(
        id = AssessmentInfoDataID.postAssessmentInterview,
        title = "💬 Help us help more people.",
        subtitle = "", body = "",
    )

    val testimonialSlide = AssessmentInfoData(
        id = AssessmentInfoDataID.postAssessmentTestimonial,
        title = "Share your Clear30 experience 😸",
        subtitle = "", body = "",
    )

    val breakdownSlide = AssessmentInfoData(
        id = AssessmentInfoDataID.postAssessmentBreakdown,
        title = "", subtitle = "", body = "",
        primaryButtonText = "Start the Better Life Program",
    )

    val lifeContextSlide = AssessmentInfoData(
        id = AssessmentInfoDataID.postAssessmentLifeContext,
        title = "🌟 Keep Growing After Clear30",
        subtitle = "Your first 30 days were just the start - now build lifelong habits for lasting growth with the\n🍃 Better Life Program 🍃",
        body = "",
    )

    val coachReferralSlide = AssessmentInfoData(
        id = AssessmentInfoDataID.postAssessmentCoachReferral,
        title = "Want a coach in your corner?",
        subtitle = "", body = "",
    )

    val loadingSlide = AssessmentInfoData(
        id = AssessmentInfoDataID.postAssessmentLoading,
        title = "", subtitle = "", body = "",
    )

    /** Life-context carousel content (image drawable name, title, body). */
    data class CarouselInfo(val image: String, val title: String, val body: String)

    val lifeContextCarousel = listOf(
        CarouselInfo(
            "post_assessment_tracking",
            "📅 Clearly See Your Progress",
            "Watch your habits evolve over time, fueling long-term clarity and motivation.",
        ),
        CarouselInfo(
            "post_assessment_messages",
            "✨ Daily Ideas Worth Remembering",
            "Discover curated insights - from ancient wisdom to cutting-edge behavioral science - to improve every aspect of your life.",
        ),
        CarouselInfo(
            "post_assessment_community",
            "📝 Reflect, Share, Grow Together",
            "Track your thoughts, see your growth, and share insights with others on the same journey.",
        ),
        CarouselInfo(
            "post_assessment_break",
            "🔄 Start a Structured Break Anytime",
            "Easily start a Clear30 break whenever life calls for a reset.",
        ),
        CarouselInfo(
            "feature_accountability",
            "🌱 Your Accountability Buddy Stays With You",
            "Your personal accountability buddy isn't going anywhere. They're still here whenever you need real, human support.",
        ),
    )
}

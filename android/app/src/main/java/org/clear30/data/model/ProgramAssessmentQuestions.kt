package org.clear30.data.model

/**
 * Static assessment question definitions — ported from
 * `ProgramAssessmentQuestions.swift` (iOS `extension Program`). Exposed via the
 * [AssessmentQuestions] object since Kotlin can't extend Program's companion from
 * another file; the slide builders (AssessmentSlides3) reference these.
 *
 * `AssessmentInfoData.customView` (a SwiftUI AnyView) is dropped per the model
 * port — affirmations render via their [AssessmentInfoDataID] in AssessmentInfoSlide.
 * `BreakReasonType`'s break-setup-only members (customCheckIn / percentage /
 * extraInfo / asNoun) are deferred to the feedback batch; only the question-facing
 * members are ported here.
 */
object AssessmentQuestions {

    val referral = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.REFERRAL.raw,
        prompt1 = "",
        prompt2 = "Where did you hear about us?",
        options = listOf(
            "Instagram", "TikTok", "YouTube", "Reddit", "X", "App Store",
            "Facebook", "LinkedIn", "Website", "Friend / Family", "Other",
        ),
        imageNames = listOf(
            "Referral Instagram", "Referral TikTok", "Referral YouTube", "Referral Reddit",
            "Referral X", "Referral App Store", "Referral Facebook", "Referral LinkedIn",
            "Referral Globe", "Referral Person", "Referral Other",
        ),
    )

    val whatBringsYouHere = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.WHAT_BRINGS_YOU_HERE.raw,
        prompt1 = "Before we dive in,",
        prompt2 = "What brings you here?",
        options = listOf("Quit", "Break", "Moderate", "Don't know"),
        displayedOptions = listOf("❌ Quitting Weed", "⏰ Taking a break", "📊 Moderation", "🤔 Don't know yet"),
        affirmations = listOf(
            AssessmentInfoData(
                id = AssessmentInfoDataID.planPath,
                badge = "Quit",
                title = "Let's learn about your weed habits so we can help you quit.",
                subtitle = "",
                body = "• These questions help you reflect.\n• Your answers help us build your plan.",
            ),
            AssessmentInfoData(
                id = AssessmentInfoDataID.planPath,
                badge = "Break",
                title = "Let's dig into your weed habits so we can help you reset.",
                subtitle = "",
                body = "• These questions help you reflect.\n• Your answers help us build your plan.",
            ),
            AssessmentInfoData(
                id = AssessmentInfoDataID.planPath,
                badge = "Balance",
                title = "Let's learn about your weed habits so we can help find your balance.",
                subtitle = "",
                body = "• These questions help you reflect.\n• Your answers help us build your plan.",
            ),
            AssessmentInfoData(
                id = AssessmentInfoDataID.planPath,
                badge = "Explore",
                title = "Let's figure out where you are with weed and where you want to be.",
                subtitle = "",
                body = "• These questions help you reflect.\n• Your answers help us find the best path forward.",
            ),
        ),
        autoAddAffirmation = true,
        min = 1, max = 1,
    )

    val name = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = AssessmentQuestionType.Input(multiLine = false, placeholder = "Name", optional = false),
        strippedPrompt = AssessmentQuestionID.NAME.raw,
        prompt1 = "First things first,",
        prompt2 = "What should we call you?",
        options = emptyList(),
    )

    val breakReason = ProgramAssessmentQuestion(
        questionNumber = 2,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.BREAK_REASON.raw,
        prompt1 = "So tell us _CLIENTNAME_,",
        prompt2 = "What do you want to **achieve** with Clear30?",
        options = BreakReasonType.allRawValues,
        displayedOptions = BreakReasonType.allDisplayTexts,
        badges = BreakReasonType.allBadges,
        affirmations = BreakReasonType.allAffirmations,
        autoAddAffirmation = false,
        min = 1, max = 3,
        shuffled = true,
    )

    val consumptionMethod = ProgramAssessmentQuestion(
        questionNumber = 3,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.CONSUMPTION_METHOD.raw,
        prompt1 = "Got it!",
        prompt2 = "How do you **usually use cannabis**?",
        options = listOf("Flower", "Pen", "Dabs", "Edibles", "Other"),
        displayedOptions = listOf("🍃 Bud (eg joint)", "🖊️ Pen / Vape", "😶‍🌫️ Dabs", "🍪 Edibles", "🤨 Other"),
        badges = listOf("🍃 Bud", "🖊️ Pen", "😶‍🌫️ Dabs", "🍪 Edible"),
        min = 1, max = 1,
    )

    val daysUsing = ProgramAssessmentQuestion(
        questionNumber = 4,
        type = AssessmentQuestionType.Slider(valueLabel = "day"),
        strippedPrompt = AssessmentQuestionID.DAYS_USING.raw,
        prompt1 = "",
        prompt2 = "How many **days a week** do you typically use cannabis?",
        options = listOf("1", "2", "3", "4", "5", "6", "7"),
        badges = listOf(
            "💨 1 day a week", "💨 2 days a week", "💨 3 days a week", "💨 4 days a week",
            "💨 5 days a week", "💨 6 days a week", "💨 7 days a week",
        ),
        min = 1, max = 7,
    )

    val moneySpent = ProgramAssessmentQuestion(
        questionNumber = 5,
        type = AssessmentQuestionType.SliderWithCustom(valueLabel = "week"),
        strippedPrompt = AssessmentQuestionID.MONEY_SPENT.raw,
        prompt1 = "",
        prompt2 = "On average, how much money do you **spend a week** on cannabis?",
        options = listOf("5", "10", "15", "20", "25", "30", "35", "40", "45", "50"),
        displayedOptions = listOf("$5", "$10", "$15", "$20", "$25", "$30", "$35", "$40", "$45", "$50"),
        min = 1, max = 10,
    )

    val helpHarm = ProgramAssessmentQuestion(
        questionNumber = 6,
        type = AssessmentQuestionType.Spectrum(left = "Harming", middle = "", right = "Helping"),
        strippedPrompt = AssessmentQuestionID.HELP_HARM.raw,
        prompt1 = "",
        prompt2 = "How has cannabis been **affecting your life**?",
        options = listOf(
            "Mostly harming more than helping",
            "Somewhat harming more than helping",
            "Equally Helping and Harming (but in different ways)",
            "Somewhat helping more than harming",
            "Mostly helping more than harming",
        ),
        displayedOptions = listOf("😖", "🙁", "😐", "🙂", "😁"),
        badges = listOf(
            "😖 Mostly harming", "🙁 Somewhat harming", "😐 Helping and harming",
            "🙂 Somewhat helping", "😁 Mostly helping",
        ),
        min = 1, max = 1,
    )

    val age = ProgramAssessmentQuestion(
        questionNumber = 7,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.AGE.raw,
        prompt1 = "This helps us provide advice that's just right for you.",
        prompt2 = "How old are you?",
        options = listOf("18-20", "21-25", "26-30", "31-40", "41-50", "51-64", "65+"),
        displayedOptions = listOf("18 - 20", "21 - 25", "26 - 30", "31 - 40", "41 - 50", "51 - 64", "65+"),
        // Terms-of-Use footer shown on the age screen (iOS subtext via MiniTextWithLinks).
        subtext = "By continuing you agree to our [Terms of Use](https://www.clear30.org/terms-and-conditions) and [Privacy Policy](https://clear30.org/privacy-policy/).",
        min = 1, max = 1,
    )

    val guardianReports = ProgramAssessmentQuestion(
        questionNumber = 7,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.GUARDIAN_REPORT.raw,
        prompt1 = "",
        prompt2 = "Do you want us to send weekly summaries to the caregiver that invited you?",
        options = listOf("Yes", "No"),
        displayedOptions = listOf("Yes", "No"),
        min = 1, max = 1,
    )

    val clear30Start = ProgramAssessmentQuestion(
        questionNumber = 8,
        type = AssessmentQuestionType.DatePicker(cancelOption = "I don't want to do a Clear30 break"),
        strippedPrompt = AssessmentQuestionID.START_DATE.raw,
        prompt1 = "Makes sense - jumping straight into a break can be too much too soon.",
        prompt2 = "Do you want to pick a **later date** to start your Clear30?",
        options = emptyList(),
        min = 0, max = 14,
    )

    val lastSmoked = ProgramAssessmentQuestion(
        questionNumber = 8,
        type = AssessmentQuestionType.Number(dayQuestionType = AssessmentDaysType.LastSmoked),
        strippedPrompt = AssessmentQuestionID.LAST_SMOKED.raw,
        prompt1 = "",
        prompt2 = "When did you **last use cannabis**?",
        options = emptyList(),
        min = 0, max = 11,
    )

    val usageDuration = ProgramAssessmentQuestion(
        questionNumber = 9,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.USAGE_DURATION.raw,
        prompt1 = "",
        prompt2 = "How long have you been using cannabis?",
        options = listOf(
            "Less than 6 months", "6 months - 1 year", "1-2 years",
            "2-5 years", "5-10 years", "10+ years",
        ),
        displayedOptions = listOf(
            "🌱 Less than 6 months", "🌿 6 months - 1 year", "🍃 1-2 years",
            "🌲 2-5 years", "🌳 5-10 years", "🏔️ 10+ years",
        ),
        min = 1, max = 1,
    )

    val biologicalSex = ProgramAssessmentQuestion(
        questionNumber = 10,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.BIOLOGICAL_SEX.raw,
        prompt1 = "This helps us tailor support.",
        prompt2 = "What is your biological sex at birth?",
        options = listOf("Male", "Female", "Other", "Prefer not to say"),
        min = 1, max = 1,
    )

    val timeOfDay = ProgramAssessmentQuestion(
        questionNumber = 6,
        type = AssessmentQuestionType.Spectrum(left = "☀️", middle = "", right = "🌙", showDots = false),
        strippedPrompt = AssessmentQuestionID.SMOKE_TIME.raw,
        prompt1 = "We use this to provide you with targeted support notifications.",
        prompt2 = "What **time of day** do you typically use?",
        options = listOf(
            "1:00 AM", "2:00 AM", "3:00 AM", "4:00 AM", "5:00 AM", "6:00 AM",
            "7:00 AM", "8:00 AM", "9:00 AM", "10:00 AM", "11:00 AM", "12:00 PM",
            "1:00 PM", "2:00 PM", "3:00 PM", "4:00 PM", "5:00 PM", "6:00 PM",
            "7:00 PM", "8:00 PM", "9:00 PM", "10:00 PM", "11:00 PM", "12:00 AM",
        ),
        min = 1, max = 1,
    )

    val previousBreak = ProgramAssessmentQuestion(
        questionNumber = 9,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.PREVIOUS_BREAK.raw,
        prompt1 = "",
        prompt2 = "Have you tried **changing your cannabis habits** in the past? How did it go?",
        options = listOf("Maintained", "Mostly maintained", "Did not maintain", "No break"),
        displayedOptions = listOf(
            "✅ Yes, and it wasn't difficult",
            "🤔 Yes, but it was somewhat difficult",
            "😓 Yes, and it was very challenging",
            "🤫 No, this is my first time",
        ),
        badges = listOf("✅ Past Success", "🤔 Previous Effort", "😓 Past Difficulty", "🥇 First Break"),
        affirmations = listOf(
            AssessmentInfoData(
                id = AssessmentInfoDataID.previousBreakAffirmation,
                title = "Nice—you've successfully changed your habits before!",
                subtitle = "",
                body = "Clear30 builds on what's already working for you, adding structure and personalized support so you can see (and feel!) your progress clearly.",
                systemImageName = "trophy.fill", systemImageRotation = 3f,
            ),
            AssessmentInfoData(
                id = AssessmentInfoDataID.previousBreakAffirmation,
                title = "It's great you've made changes, even if it wasn't easy.",
                subtitle = "",
                body = "We'll make things smoother with personalized support and daily guidance designed specifically to help you through any ups and downs.",
                systemImageName = "mountain.2.fill", systemImageRotation = 3f,
            ),
            AssessmentInfoData(
                id = AssessmentInfoDataID.previousBreakAffirmation,
                title = "Big respect for tackling something tough.",
                subtitle = "",
                body = "We built Clear30 specifically to support you through challenging changes—with structured daily check-ins, helpful tools, and extra encouragement exactly when you need it.",
                systemImageName = "calendar.badge.clock", systemImageRotation = 3f,
            ),
            AssessmentInfoData(
                id = AssessmentInfoDataID.previousBreakAffirmation,
                title = "Awesome you're taking this step!",
                subtitle = "",
                body = "We'll guide you clearly and simply, providing personalized tools and friendly support every day, so you'll know exactly what to expect and how to handle it.",
                systemImageName = "trophy.fill", systemImageRotation = 3f,
            ),
        ),
        min = 1, max = 1,
    )

    val symptoms = ProgramAssessmentQuestion(
        questionNumber = 9,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.SYMPTOMS.raw,
        prompt1 = "Select any symptoms you're dealing with or anticipating.",
        prompt2 = "What do you need support with during your break?",
        options = listOf(
            "Anger/Irritability", "Anxiety", "Appetite Problems", "Boredom", "Cravings",
            "Depression", "Insomnia", "Lasting Brain Fog", "Loneliness", "Nausea",
            "Social Pressure", "Stomach Problems",
        ),
        displayedOptions = listOf(
            "😡 Anger/Irritability", "😰 Anxiety", "🍗 Appetite Problems", "🫤 Boredom",
            "🫣 Cravings", "😓 Depression", "🌙 Insomnia", "😶‍🌫️ Lasting Brain Fog",
            "😔 Loneliness", "🤢 Nausea", "🎉 Social Pressure", "🍽 Stomach Problems",
        ),
        min = 1, max = 3,
    )

    val triggers = ProgramAssessmentQuestion(
        questionNumber = 10,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.TRIGGER.raw,
        prompt1 = "To help us create a plan for when temptations arise,",
        prompt2 = "Why might you use cannabis when you didn't plan to?",
        options = listOf(
            "My friends", "Relieve pain", "To fall asleep", "Reduce boredom", "My partner",
            "Enhance creativity", "Stress relief / relax", "Reduce/stop withdrawal symptoms",
            "Escape from negative thoughts / emotions", "Out of habit",
            "It is available around me generally",
            "Wanting to enhance things chores, work, or movies or music",
        ),
        displayedOptions = listOf(
            "👥 My friends", "🤕 Relieve pain", "😴 To fall asleep", "😐 Reduce boredom",
            "👫 My partner", "🎨 Enhance creativity", "😌 Stress relief/relax",
            "😰 Reduce/stop withdrawal symptoms", "🧠 Escape from negative thoughts/emotions",
            "🔁 Out of habit", "🏠 It is available around me generally",
            "✨ Wanting to enhance things (chores, work, movies, or music)",
        ),
        badges = listOf(
            "👥 Friends", "🤕 Pain", "😴 Sleep", "😐 Boredom", "👫 Partner", "🎨 Creativity",
            "😌 Stress relief", "😰 Symptoms", "🧠 Escaping negativity", "🔁 Escaping habit",
            "🏠 Availability", "✨ Enhancing life",
        ),
        affirmations = listOf(
            triggerAffirmation("👥 Smoking because your friends are?", "We'll help you handle those moments in ways that feel right to you—without weed.", "person.3.sequence.fill"),
            triggerAffirmation("🩹 Smoking to ease physical pain?", "We'll help you find relief that doesn't rely on weed.", "bolt.shield.fill"),
            triggerAffirmation("😴 Using weed to fall asleep?", "Let's help you build routines that make sleep easier without cannabis.", "moon.zzz.fill"),
            triggerAffirmation("📱 Smoking just to pass the time?", "We'll help you find more satisfying ways to beat boredom.", "hourglass.bottomhalf.filled"),
            triggerAffirmation("❤️ Smoking because your partner does?", "We'll help you build comfort and confidence choosing what's right for you—regardless of their choices.", "person.2.wave.2.fill"),
            triggerAffirmation("🎨 Smoking to get those creative juices flowing?", "We'll help you find fresh ways to spark ideas—without needing weed.", "paintbrush.pointed.fill"),
            triggerAffirmation("😌 Smoking to relieve stress or unwind?", "We'll help you find healthier ways to relax, without leaning on weed.", "wind.snow.circle.fill"),
            triggerAffirmation("🌡 Smoking to avoid withdrawal?", "We'll help you get through the discomfort and out the other side—so you won't need weed to feel okay.", "chart.line.downtrend.xyaxis"),
            triggerAffirmation("🌧 Smoking to escape tough feelings?", "We'll help you handle those emotions in ways that leave you feeling better.", "cloud.heavyrain.fill"),
            triggerAffirmation("🔄 Smoking without thinking about it?", "We'll help you replace autopilot habits with ones that feel better.", "repeat.circle.fill"),
            triggerAffirmation("🌱 Smoking because it's around you?", "We'll help you feel comfortable saying no—even when weed's right in front of you.", "tray.full.fill"),
            triggerAffirmation("🎧 Smoking to make things more enjoyable?", "We'll help you find ways to elevate everyday activities without weed.", "sparkles.tv.fill"),
        ),
        autoAddAffirmation = false,
        min = 1, max = 3,
        shuffled = true,
    )

    val afterClear30 = ProgramAssessmentQuestion(
        questionNumber = 11,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.THEN_WHAT.raw,
        prompt1 = "",
        prompt2 = "Which best describes your **goals after Clear30**",
        options = listOf(
            "I want to stop cannabis/weed use entirely",
            "I want to use less or use differently",
            "I want to use the same",
            "I don't know right now",
        ),
        displayedOptions = listOf(
            "❌ Stop cannabis entirely", "📉 Use less or differently",
            "➡️ Continue with current use", "❓ Not sure yet",
        ),
        badges = listOf("❌ Stop entirely", "📉 Use differently", "➡️ Continue with current use"),
        min = 1, max = 1,
    )

    val commitment = ProgramAssessmentQuestion(
        questionNumber = 12,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.COMMITMENT.raw,
        prompt1 = "So,",
        prompt2 = "How committed are you right now?",
        options = listOf("Extremely", "Very", "Somewhat", "Little", "Not"),
        displayedOptions = listOf(
            "🔥 Extremely Committed", "💪 Very committed", "🤔 Somewhat committed",
            "🌱 A Little Committed", "🤳 Just trying it out",
        ),
        badges = listOf(
            "🔥 Extremely Committed", "💪 Very committed", "🤔 Somewhat committed",
            "🌱 A Little Committed", "🤳 Trying it out",
        ),
        affirmations = listOf(
            commitmentAffirmation("You're all-in, and we're all about it.", "People this committed cut their cannabis use by an average of 90% with Clear30. You've set yourself up perfectly—let's maximize your momentum.", "star.fill"),
            commitmentAffirmation("Love that you're committed!", "When committed people like you use Clear30, they cut their cannabis use by an average of 90%. You're already setting yourself up for big results.", "hand.thumbsup.fill"),
            commitmentAffirmation("It's totally fine if you're only somewhat committed right now.", "Clear30 isn't about being fully \"all-in\" from the start—we help you build smart habits that work regardless of how motivated you're feeling.", "chart.line.uptrend.xyaxis"),
            commitmentAffirmation("Even a little commitment is enough to start real change.", "Clear30 makes progress easy with small, clear steps that add up faster than you'd expect.", "tree.circle.fill"),
            commitmentAffirmation("Just trying it out is great—you're here and exploring.", "Clear30 can help you test the waters, see what works for you, and take it from there.", "sparkles"),
        ),
        min = 1, max = 1,
    )

    val modAbs = ProgramAssessmentQuestion(
        questionNumber = 13,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.LO_USE_STATE.raw,
        prompt1 = "",
        prompt2 = "Which best describes your goals regarding cannabis moving forward?",
        options = listOf("I want to stop entirely", "I want to moderate my use"),
        displayedOptions = listOf("🤩 Weed Free", "📊 Moderation"),
        affirmations = listOf(
            AssessmentInfoData(
                id = AssessmentInfoDataID.modAbsAffirmation,
                title = "Great, we'll focus on helping you stay weed free!",
                subtitle = "",
                body = "If you ever want to switch to moderation or start a Clear30, you can do that anytime.",
                systemImageName = "checkmark.circle.fill",
                primaryButtonText = "Done", primaryButtonIcon = "checkmark",
            ),
            AssessmentInfoData(
                id = AssessmentInfoDataID.modAbsAffirmation,
                title = "Great choice!",
                subtitle = "We'll help you find the sweet spot that works best for you.",
                body = "If you decide to go fully weed-free or start a Clear30 later, you can switch anytime.",
                systemImageName = "calendar.badge.clock",
                primaryButtonText = "Done", primaryButtonIcon = "checkmark",
            ),
        ),
        min = 1, max = 1,
    )

    // ─────────── New Break assessment (iOS BreakAssessmentAbstracted.swift) ───────────

    val newBreakType = ProgramAssessmentQuestion(
        questionNumber = 1,
        type = AssessmentQuestionType.MultipleChoice,
        strippedPrompt = AssessmentQuestionID.NEW_BREAK_TYPE.raw,
        prompt1 = "Want to start a new break?",
        prompt2 = "Choose a break below." +
            // iOS counts ALL cases (incl. start-soon), so the suffix never shows.
            if (ProgramBreakType.entries.size == 1) "\n(More breaks coming soon)" else "",
        options = ProgramBreakType.entries.filterNot { it.isStartSoon }.sortedBy { it.raw }.map { it.id },
        displayedOptions = ProgramBreakType.entries.filterNot { it.isStartSoon }.sortedBy { it.raw }.map { it.typeName },
        min = 1, max = 1,
    )

    val newBreakStart = ProgramAssessmentQuestion(
        questionNumber = 2,
        type = AssessmentQuestionType.DatePicker(cancelOption = null),
        strippedPrompt = AssessmentQuestionID.START_DATE.raw,
        prompt1 = "",
        prompt2 = "When do you want to start your break?",
        options = emptyList(),
        min = 0, max = 14,
    )

    /** The ordered new-break question set (iOS `newClear30Questions`). */
    val newClear30Questions: List<ProgramAssessmentQuestion> = listOf(
        breakReason, consumptionMethod, daysUsing, moneySpent, helpHarm,
        newBreakStart, previousBreak, triggers, afterClear30, commitment,
    )

    private fun triggerAffirmation(title: String, body: String, symbol: String) = AssessmentInfoData(
        id = AssessmentInfoDataID.triggersAffirmation,
        title = title, subtitle = "", body = body,
        systemImageName = symbol, systemImageRotation = -3f,
        primaryButtonText = "Finish Up",
    )

    private fun commitmentAffirmation(title: String, body: String, symbol: String) = AssessmentInfoData(
        id = AssessmentInfoDataID.commitmentAffirmation,
        title = title, subtitle = "", body = body,
        systemImageName = symbol,
        primaryButtonText = "Done", primaryButtonIcon = "checkmark",
    )
}

/** First selected option's display/option text, or null (Swift `getSingleOption`). */
fun ProgramAssessmentResponse.getSingleOption(): String? =
    responses.firstOrNull()?.let { question.options.getOrNull(it) }

/**
 * BreakReasonType — ported from ProgramAssessmentQuestions.swift. Question-facing
 * members only (displayText / badge / affirmation); break-setup members
 * (customCheckIn / percentage / extraInfo / asNoun) are deferred to a later batch.
 */
enum class BreakReasonType(val rawValue: String) {
    GAIN_MENTAL_CLARITY("Gain Mental Clarity"),
    REDUCE_ANXIETY("Reduce Anxiety"),
    REDUCE_DEPRESSION("Reduce Depression"),
    REDUCE_BEING_STUCK_IN_OWN_HEAD("Reduce Being Stuck in Own Head"),
    IMPROVE_SLEEP_QUALITY("Improve Sleep Quality"),
    IMPROVE_SELF_CONTROL("Improve Self-Control and Intention"),
    REDUCE_DEPENDENCY("Reduce Dependency on Cannabis"),
    EXPLORE_LIFE_WITHOUT("Explore Life Without Cannabis"),
    LOWER_TOLERANCE("Lower Tolerance"),
    IMPROVE_OVERALL_HEALTH("Improve Overall Health"),
    IMPROVE_LUNG_HEALTH("Improve Lung Health"),
    INCREASE_PRODUCTIVITY("Increase Productivity"),
    INCREASE_MOTIVATION("Increase Motivation"),
    SAVE_MONEY("Save Money"),
    IMPROVE_RELATIONSHIPS("Improve Current Relationships"),
    ENHANCE_SOCIAL_CONNECTIONS("Enhance Social Connections"),
    REDUCE_LONELINESS("Reduce Loneliness"),
    PASS_DRUG_TEST("Pass Work-Required Drug Test"),
    MEET_LEGAL_OBLIGATIONS("Meet Legal Obligations"),
    ENTER_NEW_PHASE("Enter a New Phase of My Life"),
    HEALTHY_PREGNANCY("Have a Healthy Pregnancy"),
    OTHER("Other");

    val displayText: String
        get() = when (this) {
            GAIN_MENTAL_CLARITY -> "💡 Gain Mental Clarity"
            REDUCE_ANXIETY -> "😤 Reduce Anxiety"
            REDUCE_DEPRESSION -> "☺️ Reduce Depression"
            REDUCE_BEING_STUCK_IN_OWN_HEAD -> "😇 Reduce Being Stuck in my Own Head"
            IMPROVE_SLEEP_QUALITY -> "😴 Improve Sleep Quality"
            IMPROVE_SELF_CONTROL -> "🎯 Improve Sense of Self-Control and Intention"
            REDUCE_DEPENDENCY -> "🛑 Reduce Dependency on Cannabis"
            EXPLORE_LIFE_WITHOUT -> "🌍 Explore Life Without Cannabis"
            LOWER_TOLERANCE -> "⬇️ Lower Tolerance"
            IMPROVE_OVERALL_HEALTH -> "💚 Improve Overall Health"
            IMPROVE_LUNG_HEALTH -> "🫁 Improve Lungs"
            INCREASE_PRODUCTIVITY -> "💼 Increase Productivity"
            INCREASE_MOTIVATION -> "🚀 Increase Motivation"
            SAVE_MONEY -> "💰 Save Money"
            IMPROVE_RELATIONSHIPS -> "👥 Improve Current Relationships"
            ENHANCE_SOCIAL_CONNECTIONS -> "🥳 Reduce Loneliness / Enhance Social Connections"
            REDUCE_LONELINESS -> "🥳 Reduce Loneliness"
            PASS_DRUG_TEST -> "🧪 Pass a Work-Required Drug Test"
            MEET_LEGAL_OBLIGATIONS -> "⚖️ Meet Legal Obligations (e.g., Probation)"
            ENTER_NEW_PHASE -> "🌱 Enter a New Phase of My Life"
            HEALTHY_PREGNANCY -> "🤰 Have a Healthy Pregnancy"
            OTHER -> "❓ Other"
        }

    val badge: String
        get() = when (this) {
            GAIN_MENTAL_CLARITY -> "💡 Mental Clarity"
            REDUCE_ANXIETY -> "😤 Reduce Anxiety"
            REDUCE_DEPRESSION -> "☺️ Reduce Depression"
            REDUCE_BEING_STUCK_IN_OWN_HEAD -> "😇 Getting Out of Your Head"
            IMPROVE_SLEEP_QUALITY -> "😴 Improve Sleep"
            IMPROVE_SELF_CONTROL -> "🎯 Improve Self-Control"
            REDUCE_DEPENDENCY -> "🛑 Reduce Dependency"
            EXPLORE_LIFE_WITHOUT -> "🌍 Explore Life Without Cannabis"
            LOWER_TOLERANCE -> "⬇️ Lower Tolerance"
            IMPROVE_OVERALL_HEALTH -> "💚 Improve Health"
            IMPROVE_LUNG_HEALTH -> "🫁 Improve Lungs"
            INCREASE_PRODUCTIVITY -> "💼 Increase Productivity"
            INCREASE_MOTIVATION -> "🚀 Increase Motivation"
            SAVE_MONEY -> "💰 Save Money"
            IMPROVE_RELATIONSHIPS -> "👥 Improve Relationships"
            ENHANCE_SOCIAL_CONNECTIONS -> "🥳 Enhance Social Connections"
            REDUCE_LONELINESS -> "🥳 Reduce Loneliness"
            PASS_DRUG_TEST -> "🧪 Pass a Drug Test"
            MEET_LEGAL_OBLIGATIONS -> "⚖️ Legal Obligations"
            ENTER_NEW_PHASE -> "🌱 New Phase"
            HEALTHY_PREGNANCY -> "🤰 Healthy Pregnancy"
            OTHER -> ""
        }

    /** Short emoji + noun for the dream-outcome projection (iOS `asNoun`). */
    val asNoun: String
        get() = when (this) {
            GAIN_MENTAL_CLARITY -> "💡 Mental Clarity"
            REDUCE_ANXIETY -> "😤 Reduced Anxiety"
            REDUCE_DEPRESSION -> "☺️ Reduced Depression"
            REDUCE_BEING_STUCK_IN_OWN_HEAD -> "😇 Mental Freedom"
            IMPROVE_SLEEP_QUALITY -> "😴 Better Sleep"
            IMPROVE_SELF_CONTROL -> "🎯 Better Self-Control"
            REDUCE_DEPENDENCY -> "🛑 Less Dependency"
            EXPLORE_LIFE_WITHOUT -> "🌍 Life Without Cannabis"
            LOWER_TOLERANCE -> "⬇️ Lower Tolerance"
            IMPROVE_OVERALL_HEALTH -> "💚 Better Health"
            IMPROVE_LUNG_HEALTH -> "🫁 Healthier Lungs"
            INCREASE_PRODUCTIVITY -> "💼 Higher Productivity"
            INCREASE_MOTIVATION -> "🚀 More Motivation"
            SAVE_MONEY -> "💰 Money Saved"
            IMPROVE_RELATIONSHIPS -> "👥 Better Relationships"
            ENHANCE_SOCIAL_CONNECTIONS -> "🥳 Better Social Connections"
            REDUCE_LONELINESS -> "🥳 Less Loneliness"
            PASS_DRUG_TEST -> "🧪 Passed Drug Test"
            MEET_LEGAL_OBLIGATIONS -> "⚖️ Met Legal Obligations"
            ENTER_NEW_PHASE -> "🌱 New Life Phase"
            HEALTHY_PREGNANCY -> "🤰 Healthy Pregnancy"
            OTHER -> "❓ Other Benefits"
        }

    /** Encouraging one-liner shown under each goal on the goals-affirmation cards (iOS `extraInfo`). */
    val extraInfo: String
        get() = when (this) {
            GAIN_MENTAL_CLARITY -> "We'll help clear your head, so you can finish your thoughts—and sentences—in peace."
            REDUCE_ANXIETY -> "We'll focus on tools to make life feel a bit lighter."
            REDUCE_DEPRESSION -> "We can help you find your spark again."
            REDUCE_BEING_STUCK_IN_OWN_HEAD -> "We'll help you step out of your head and into the present."
            IMPROVE_SLEEP_QUALITY -> "We'll help you get real beauty sleep."
            IMPROVE_SELF_CONTROL -> "Taking a weed break strengthens self-control and helps you align your actions with your goals."
            REDUCE_DEPENDENCY -> "Let's focus on putting you back in charge—because you're a better boss anyway."
            EXPLORE_LIFE_WITHOUT -> "Cutting out weed can force you to leave your comfort zone and make some real memories!"
            LOWER_TOLERANCE -> "Taking a break helps reset your system, so you can enjoy the effects again without needing more."
            IMPROVE_OVERALL_HEALTH -> "We'll help you build habits your body and mind will thank you for."
            IMPROVE_LUNG_HEALTH -> "We'll help you breathe easier and feel healthier again."
            INCREASE_PRODUCTIVITY -> "We'll help you lock in."
            INCREASE_MOTIVATION -> "We'll help you turn your weed break into motivation your future self will brag about."
            SAVE_MONEY -> "We can focus on helping you save cash to spend it on things you really value."
            IMPROVE_RELATIONSHIPS -> "We'll help you use your weed break to reconnect and level up your relationships."
            ENHANCE_SOCIAL_CONNECTIONS -> "Taking a break from weed can create space for new friendships and deeper connections."
            REDUCE_LONELINESS -> "Taking a break from weed can create space for new friendships and deeper connections."
            PASS_DRUG_TEST -> "We'll help you clear weed out of your system, so you're confident and ready."
            MEET_LEGAL_OBLIGATIONS -> "We'll help you stick to your weed break and clear your obligations smoothly."
            ENTER_NEW_PHASE -> "This break is your fresh start. We'll help you build the foundation for whatever comes next."
            HEALTHY_PREGNANCY -> "Taking a break from cannabis is a great step for your health and your baby's. We'll support you through this journey."
            OTHER -> "Everyone has their own unique motivation. Whatever yours is, we're here to support you through it."
        }

    /** Optional "% of Clear30 users saw…" stat (iOS `percentage`); null when not applicable. */
    val percentage: BreakReasonPercentage?
        get() = when (this) {
            GAIN_MENTAL_CLARITY -> BreakReasonPercentage(81.08, "81.08% of Clear30 users saw improved mental clarity.")
            REDUCE_ANXIETY -> BreakReasonPercentage(79.17, "79.17% of Clear30 users saw reduced anxiety.")
            REDUCE_DEPRESSION -> BreakReasonPercentage(77.27, "77.27% of Clear30 users saw reduced depression.")
            REDUCE_BEING_STUCK_IN_OWN_HEAD -> BreakReasonPercentage(85.71, "85.71% of Clear30 users saw improved mental freedom.")
            IMPROVE_SLEEP_QUALITY -> BreakReasonPercentage(73.68, "73.68% of Clear30 users saw improved sleep quality.")
            IMPROVE_SELF_CONTROL -> BreakReasonPercentage(87.5, "87.5% of Clear30 users saw improved self-control.")
            REDUCE_DEPENDENCY -> BreakReasonPercentage(78.12, "78.12% of Clear30 users saw reduced dependency on cannabis.")
            EXPLORE_LIFE_WITHOUT -> BreakReasonPercentage(86.67, "86.67% of Clear30 users saw improved life without cannabis.")
            LOWER_TOLERANCE -> BreakReasonPercentage(90.0, "90% of Clear30 users reduced their tolerance.")
            IMPROVE_OVERALL_HEALTH -> BreakReasonPercentage(80.0, "80% of Clear30 users saw improved overall health.")
            IMPROVE_LUNG_HEALTH -> BreakReasonPercentage(88.89, "88.89% of Clear30 users saw improved lung health.")
            INCREASE_PRODUCTIVITY -> BreakReasonPercentage(100.0, "100% of Clear30 users saw improved productivity.")
            INCREASE_MOTIVATION -> BreakReasonPercentage(88.89, "88.89% of Clear30 users saw improved motivation.")
            SAVE_MONEY -> BreakReasonPercentage(66.67, "66.67% of Clear30 users saved money.")
            IMPROVE_RELATIONSHIPS -> BreakReasonPercentage(71.43, "71.43% of Clear30 users saw improved relationships.")
            ENHANCE_SOCIAL_CONNECTIONS -> BreakReasonPercentage(80.0, "80% of Clear30 users saw improved social connections.")
            REDUCE_LONELINESS -> BreakReasonPercentage(80.0, "80% of Clear30 users saw reduced loneliness.")
            PASS_DRUG_TEST -> BreakReasonPercentage(75.0, "75% of Clear30 users passed their drug test.")
            MEET_LEGAL_OBLIGATIONS -> BreakReasonPercentage(0.0, "0% of Clear30 users met their legal obligations.")
            ENTER_NEW_PHASE -> BreakReasonPercentage(85.0, "85% of Clear30 users felt ready to enter a new phase of life.")
            HEALTHY_PREGNANCY -> BreakReasonPercentage(90.0, "90% of Clear30 users achieved their pregnancy health goals.")
            OTHER -> BreakReasonPercentage(100.0, "100% of Clear30 users saw improved outcomes.")
        }

    val affirmation: AssessmentInfoData
        get() = breakReasonAffirmation(this)

    companion object {
        /** The display order used by the break-reason question (mirrors iOS `options`). */
        val options: List<BreakReasonType> = listOf(
            GAIN_MENTAL_CLARITY, REDUCE_ANXIETY, REDUCE_DEPRESSION, REDUCE_BEING_STUCK_IN_OWN_HEAD,
            IMPROVE_SLEEP_QUALITY, IMPROVE_SELF_CONTROL, REDUCE_DEPENDENCY, EXPLORE_LIFE_WITHOUT,
            LOWER_TOLERANCE, IMPROVE_OVERALL_HEALTH, IMPROVE_LUNG_HEALTH, INCREASE_PRODUCTIVITY,
            INCREASE_MOTIVATION, SAVE_MONEY, IMPROVE_RELATIONSHIPS, REDUCE_LONELINESS,
            PASS_DRUG_TEST, MEET_LEGAL_OBLIGATIONS, ENTER_NEW_PHASE, HEALTHY_PREGNANCY, OTHER,
        )

        val allRawValues: List<String> get() = options.map { it.rawValue }
        val allDisplayTexts: List<String> get() = options.map { it.displayText }
        val allBadges: List<String> get() = options.map { it.badge }
        val allAffirmations: List<AssessmentInfoData> get() = options.map { it.affirmation }

        fun from(text: String): BreakReasonType? = entries.firstOrNull { it.rawValue == text }
    }
}

/** iOS `(percentage: Double, text: String)` tuple for a break-reason outcome stat. */
data class BreakReasonPercentage(val percentage: Double, val text: String)

private fun reasonAffirmation(title: String, body: String, symbol: String) = AssessmentInfoData(
    id = AssessmentInfoDataID.breakReasonAffirmation,
    title = title, subtitle = "", body = body,
    systemImageName = symbol, systemImageRotation = -3f,
)

private fun breakReasonAffirmation(reason: BreakReasonType): AssessmentInfoData = when (reason) {
    BreakReasonType.GAIN_MENTAL_CLARITY -> reasonAffirmation(
        "💡 Ready for fewer \"Wait, what was I just saying?\" moments?",
        "We'll help clear your head, so you can finish your thoughts—and sentences—in peace.\n\n**96.3%** of users report improved mental clarity.",
        "lightbulb.circle.fill",
    )
    BreakReasonType.REDUCE_ANXIETY -> reasonAffirmation(
        "😤 Let's take some of that pressure off!",
        "We'll focus on tools to make life feel a bit lighter.\n\n**96%** of Clear30 users felt better mentally after their break.",
        "heart.circle.fill",
    )
    BreakReasonType.REDUCE_DEPRESSION -> reasonAffirmation(
        "☺️ Feeling down more often than you'd like?",
        "We can help you find your spark again.\n\n**96%** of Clear30 users said their break improved their overall mental health.",
        "sun.max.fill",
    )
    BreakReasonType.REDUCE_BEING_STUCK_IN_OWN_HEAD -> reasonAffirmation(
        "😇 Ready to stop replaying that awkward thing you said in 2018?",
        "We'll help you step out of your head and into the present.\n\n**96%** of Clear30 users felt their mental health improved after their break.",
        "arrow.trianglehead.2.clockwise.rotate.90.circle.fill",
    )
    BreakReasonType.IMPROVE_SLEEP_QUALITY -> reasonAffirmation(
        "😴 Ready to break up with weed as your bedtime buddy?",
        "We'll help you get real beauty sleep.",
        "moon.zzz.fill",
    )
    BreakReasonType.IMPROVE_SELF_CONTROL -> reasonAffirmation(
        "🎯 You're choosing intention.",
        "Taking a weed break strengthens self-control and helps you align your actions with your goals.",
        "hand.raised.fill",
    )
    BreakReasonType.REDUCE_DEPENDENCY -> reasonAffirmation(
        "🛑 Tired of weed calling the shots?",
        "Let's focus on putting you back in charge—because you're a better boss anyway.\n\n**92.6%** of users said Clear30 helped them improve their relationship with weed.",
        "hand.raised.fill",
    )
    BreakReasonType.EXPLORE_LIFE_WITHOUT -> reasonAffirmation(
        "🌍 Exploring life without cannabis",
        "Cutting out weed can force you to leave your comfort zone and make some real memories!\n\n**96%** of users experienced self growth.",
        "map.fill",
    )
    BreakReasonType.LOWER_TOLERANCE -> reasonAffirmation(
        "⬇️ Looking to reset your tolerance?",
        "Taking a break helps reset your system, so you can enjoy the effects again without needing more.",
        "chart.line.downtrend.xyaxis",
    )
    BreakReasonType.IMPROVE_OVERALL_HEALTH -> reasonAffirmation(
        "💚 Ready to feel healthier without weed?",
        "We'll help you build habits your body and mind will thank you for.",
        "heart.text.square.fill",
    )
    BreakReasonType.IMPROVE_LUNG_HEALTH -> reasonAffirmation(
        "🫁 Lungs feeling the weed?",
        "We'll help you breathe easier and feel healthier again.",
        "lungs.fill",
    )
    BreakReasonType.INCREASE_PRODUCTIVITY -> reasonAffirmation(
        "💼 Got big goals, and weed isn't helping you get closer to them?",
        "We'll help you lock in.",
        "chart.bar.fill",
    )
    BreakReasonType.INCREASE_MOTIVATION -> reasonAffirmation(
        "🚀 Ready to impress yourself with how productive you can be?",
        "We'll help you turn your weed break into motivation your future self will brag about.",
        "flame.fill",
    )
    BreakReasonType.SAVE_MONEY -> reasonAffirmation(
        "💰 Let's get your money up!",
        "We can focus on helping you save cash to spend it on things you really value.",
        "dollarsign.circle.fill",
    )
    BreakReasonType.IMPROVE_RELATIONSHIPS -> reasonAffirmation(
        "👥 Let's make your friends wonder why you're suddenly so present!",
        "We'll help you use your weed break to reconnect and level up your relationships.",
        "person.2.fill",
    )
    BreakReasonType.ENHANCE_SOCIAL_CONNECTIONS -> reasonAffirmation(
        "🥳 Let's focus on your social life!",
        "Taking a break from weed can create space for new friendships and deeper connections.",
        "person.3.fill",
    )
    BreakReasonType.REDUCE_LONELINESS -> reasonAffirmation(
        "🥳 Let's focus on your social life!",
        "Taking a break from weed can create space for new friendships and deeper connections.",
        "person.3.fill",
    )
    BreakReasonType.PASS_DRUG_TEST -> reasonAffirmation(
        "🧪 Let's focus on passing your drug test!",
        "We'll help you clear weed out of your system, so you're confident and ready.",
        "checkmark.seal.fill",
    )
    BreakReasonType.MEET_LEGAL_OBLIGATIONS -> reasonAffirmation(
        "⚖️ Let's keep you on the right side of the law!",
        "We'll help you stick to your weed break and clear your obligations smoothly.",
        "building.columns.fill",
    )
    BreakReasonType.ENTER_NEW_PHASE -> reasonAffirmation(
        "🌱 Ready to step into a new chapter?",
        "This break is your fresh start. We'll help you build the foundation for whatever comes next.",
        "leaf.fill",
    )
    BreakReasonType.HEALTHY_PREGNANCY -> reasonAffirmation(
        "🤰 Preparing for a healthy pregnancy!",
        "Taking a break from cannabis is a great step for your health and your baby's. We'll support you through this journey.",
        "heart.fill",
    )
    BreakReasonType.OTHER -> reasonAffirmation(
        "❓ What's your reason for taking a break?",
        "Everyone has their own unique motivation. Whatever yours is, we're here to support you through it.",
        "questionmark.circle.fill",
    )
}

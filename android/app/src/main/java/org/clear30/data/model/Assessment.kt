package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Assessment-question engine data layer — ported from ProgramAssessment.swift.
 *
 * `AssessmentQuestionType` (a Swift enum-with-associated-values + custom Codable
 * using a `type` discriminator) maps to a kotlinx sealed class — kotlinx uses
 * the same `type` discriminator by default, and @SerialName + matching property
 * names reproduce the iOS JSON shape. `AssessmentInfoData.customView` (a SwiftUI
 * `AnyView`) is dropped; the equivalent custom slides are built in the views
 * segment.
 */

@Serializable
sealed class AssessmentQuestionType {
    @Serializable @SerialName("multipleChoice")
    data object MultipleChoice : AssessmentQuestionType()

    @Serializable @SerialName("sectionedMultipleChoice")
    data class SectionedMultipleChoice(val sectionTitles: List<MultipleChoiceSection>) : AssessmentQuestionType()

    @Serializable @SerialName("imageChoice")
    data class ImageChoiceType(val imageChoices: List<ImageChoice>) : AssessmentQuestionType()

    @Serializable @SerialName("slider")
    data class Slider(val valueLabel: String) : AssessmentQuestionType()

    @Serializable @SerialName("sliderWithCustom")
    data class SliderWithCustom(val valueLabel: String) : AssessmentQuestionType()

    @Serializable @SerialName("spectrum")
    data class Spectrum(val left: String, val middle: String, val right: String, val showDots: Boolean = true) : AssessmentQuestionType()

    @Serializable @SerialName("multiSpectrum")
    data class MultiSpectrum(
        val left: String, val leftEmoji: String,
        val middle: String, val middleEmoji: String,
        val right: String, val rightEmoji: String,
    ) : AssessmentQuestionType()

    @Serializable @SerialName("number")
    data class Number(val dayQuestionType: AssessmentDaysType) : AssessmentQuestionType()

    @Serializable @SerialName("input")
    data class Input(val multiLine: Boolean, val placeholder: String, val optional: Boolean) : AssessmentQuestionType()

    @Serializable @SerialName("datePicker")
    data class DatePicker(val cancelOption: String? = null) : AssessmentQuestionType()

    /**
     * Fallback for unknown server-side type discriminators. Routed to via the
     * polymorphic default deserializer registered on [org.clear30.data.LocalStore.json] /
     * [org.clear30.data.supabase.SupabaseController.decoder], so that adding a new
     * question type on the backend (e.g. an A/B test) cannot break the entire
     * assessment payload on older clients.
     */
    @Serializable @SerialName("__unknown__")
    data object Unknown : AssessmentQuestionType()
}

@Serializable
sealed class AssessmentDaysType {
    @Serializable @SerialName("lastSmoked") data object LastSmoked : AssessmentDaysType()
    @Serializable @SerialName("lastSmokedAndStartSoon") data class LastSmokedAndStartSoon(val startSoonMax: Int) : AssessmentDaysType()
    @Serializable @SerialName("startSoon") data object StartSoon : AssessmentDaysType()
    @Serializable @SerialName("daysUsing") data object DaysUsing : AssessmentDaysType()
}

@Serializable
data class MultipleChoiceSection(
    val title: String,
    val range: SectionRange,
) {
    /** Swift `ClosedRange<Int>` -> explicit bounds (server-defined). */
    @Serializable
    data class SectionRange(val lowerBound: Int, val upperBound: Int) {
        val intRange: IntRange get() = lowerBound..upperBound
    }
}

@Serializable
data class ImageChoice(
    val imageName: String,
    val isSystemImage: Boolean,
)

/** Stable IDs for affirmation/info slides — value class for forward-compat. */
@JvmInline
@Serializable
value class AssessmentInfoDataID(val raw: String) {
    companion object {
        val features = AssessmentInfoDataID("features")
        val expectations = AssessmentInfoDataID("expectations")
        val socialProof = AssessmentInfoDataID("social_proof")
        val credibility = AssessmentInfoDataID("credibility")
        val checkIn = AssessmentInfoDataID("check_in")
        val nameContext = AssessmentInfoDataID("name_context")
        val halftime = AssessmentInfoDataID("halftime")
        val greeting = AssessmentInfoDataID("greeting")
        val breakReasonAffirmation = AssessmentInfoDataID("break_reason_affirmation")
        val goalsAffirmation = AssessmentInfoDataID("goals_affirmation")
        val currentUseSummary = AssessmentInfoDataID("current_use_summary")
        val clear30Recommendation = AssessmentInfoDataID("clear30_recommendation")
        val whereYouAre = AssessmentInfoDataID("where_you_are")
        val whereYouGoing = AssessmentInfoDataID("where_you_going")
        val previousBreakAffirmation = AssessmentInfoDataID("previous_break_affirmation")
        val triggersAffirmation = AssessmentInfoDataID("triggers_affirmation")
        val commitmentAffirmation = AssessmentInfoDataID("commitment_affirmation")
        val modAbsAffirmation = AssessmentInfoDataID("mod_abs_affirmation")
        val welcomeTyping = AssessmentInfoDataID("welcome_typing")
        val clear30Context = AssessmentInfoDataID("clear30_context")
        val lifeContext = AssessmentInfoDataID("life_context")
        // iOS uses `goals_affirmation` for BOTH the WBYH plan-path slides and
        // the break-reason goals slide (the custom view is attached per-slide
        // there, id-collision-free). Android's id→view dispatch discriminates
        // on `affirmationCards` instead — the raw must match iOS so analytics
        // `type` values and remote `after_question_id` matching line up.
        val planPath = AssessmentInfoDataID("goals_affirmation")
        // New-break / mid-pilot / post-assessment flows (Wave 6).
        val newBreakLoading = AssessmentInfoDataID("new_break_loading")
        val midPilotAssessment = AssessmentInfoDataID("mid_pilot_assessment")
        val postAssessmentWelcome = AssessmentInfoDataID("post_assessment_welcome")
        val postAssessmentInterview = AssessmentInfoDataID("post_assessment_interview")
        val postAssessmentTestimonial = AssessmentInfoDataID("post_assessment_testimonial")
        val postAssessmentBreakdown = AssessmentInfoDataID("post_assessment_breakdown")
        val postAssessmentLifeContext = AssessmentInfoDataID("post_assessment_life_context")
        val postAssessmentCoachReferral = AssessmentInfoDataID("post_assessment_coach_referral")
        val postAssessmentLoading = AssessmentInfoDataID("post_assessment_loading")
        // (additional ids added as referenced by onboarding slides)
    }
}

/**
 * AssessmentInfoData — affirmation/info slide config (customView omitted; that
 * was a SwiftUI AnyView, rebuilt as composables in the views segment).
 */
@Serializable
data class AssessmentInfoData(
    val id: AssessmentInfoDataID? = null,
    val title: String,
    val subtitle: String,
    val body: String,
    val imageName: String? = null,
    val systemImageName: String? = null,
    val systemImageSize: Float = 125f,
    val systemImageRotation: Float = 0f,
    val primaryButtonText: String = "Next",
    val primaryButtonIcon: String = "arrow.right",
    val secondaryButtonText: String? = null,
    val overrideBackgroundGradient: Boolean? = null,
    /** Normative percentile for the pain-point comparison chart (currentUseSummary). */
    val painPointPercentile: Int? = null,
    /** Chosen break-reason outcome nouns for the dream-outcome card (clear30Recommendation). */
    val dreamOutcomeNouns: List<String>? = null,
    /** Monthly savings (weekly spend × 4) for the dream-outcome card. */
    val dreamOutcomeSavings: Int? = null,
    /** Emoji/title/subtitle cards for the goals-affirmation slide (iOS AffirmationCardsView). */
    val affirmationCards: List<AffirmationCard>? = null,
    /** Small label above the bottom card on the goals-affirmation slide (e.g. "Where You're Headed"). */
    val affirmationBottomLabel: String? = null,
    /** Body text inside the bottom card on the goals-affirmation slide (the long-term goal). */
    val affirmationBottomText: String? = null,
    /** Pill label shown atop the plan-path card (planPath slide), e.g. "Break". */
    val badge: String? = null,
)

/** One emoji + title + subtitle card on the goals-affirmation slide (iOS AffirmationCardsView.CardData). */
@Serializable
data class AffirmationCard(
    val emoji: String,
    val title: String,
    val subtitle: String,
)

/** ProgramAssessmentQuestion — ported 1:1 (ProgramAssessment.swift). */
@Serializable
data class ProgramAssessmentQuestion(
    val questionNumber: Int,
    val type: AssessmentQuestionType,
    val strippedPrompt: String,
    val prompt1: String,
    val prompt2: String,
    val options: List<String>,
    val displayedOptions: List<String>? = null,
    val imageNames: List<String>? = null,
    val badges: List<String>? = null,
    val subtexts: List<String>? = null,
    /** Question-level footer (iOS `subtext`), e.g. the Terms-of-Use line on the age
     *  question; supports `[label](url)` markdown links. */
    val subtext: String = "",
    val affirmations: List<AssessmentInfoData>? = emptyList(),
    val autoAddAffirmation: Boolean? = true,
    val min: Int = 1,
    val max: Int = 1,
    val shuffled: Boolean? = false,
)

/** ProgramAssessmentResponse — a question + the chosen option indices. */
@Serializable
data class ProgramAssessmentResponse(
    val question: ProgramAssessmentQuestion,
    val responses: List<Int>,
)

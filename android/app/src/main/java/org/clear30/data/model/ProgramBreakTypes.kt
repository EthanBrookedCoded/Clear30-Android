package org.clear30.data.model

import kotlinx.serialization.Serializable

/**
 * ProgramBreakType — ported from ProgramBreak.swift. The iOS Int rawValue (30 /
 * 7) doubles as the break's day count (e.g. `endDate = start + type.raw + 1`),
 * so [raw] is preserved exactly.
 */
@Serializable
enum class ProgramBreakType(val raw: Int) {
    CLEAR30(30),
    CLEAR30_START_SOON(7);   // override-driven; raw unused for start-soon

    val typeName: String get() = if (this == CLEAR30) "Clear30" else "Preparation"

    val id: String get() = if (this == CLEAR30) "clear30" else "clear30-start-soon"

    val assessmentType: AssessmentType get() = AssessmentType.Clear30

    val isStartSoon: Boolean get() = this == CLEAR30_START_SOON

    companion object {
        fun from(id: String): ProgramBreakType? = when (id) {
            "clear30" -> CLEAR30
            "clear30-start-soon" -> CLEAR30_START_SOON
            else -> null
        }
    }
}

/** AssessmentType — ported from the enum with a `custom(assessmentID)` case. */
@Serializable
sealed class AssessmentType {
    @Serializable data object Life : AssessmentType()
    @Serializable data object LifeShort : AssessmentType()
    @Serializable data object LifeOnboarding : AssessmentType()
    @Serializable data object Clear30 : AssessmentType()
    @Serializable data class Custom(val assessmentID: String) : AssessmentType()

    val string: String
        get() = when (this) {
            is Life -> "life"
            is LifeShort -> "life-short"
            is Clear30 -> "clear30"
            is LifeOnboarding -> "life-onboarding"
            is Custom -> assessmentID
        }
}

/**
 * AssessmentQuestionID — ported from ProgramAssessment.swift. [raw] is the iOS
 * rawValue, used to match question prompts (`getAssessmentResponse(strippedPrompt:)`).
 */
enum class AssessmentQuestionID(val raw: String) {
    // General
    REFERRAL("Referral"),
    AGE("LO-Age"),
    NAME("Name-Exclude"),
    BREAK_REASON("Break-Reason"),
    CONSUMPTION_METHOD("Consumption-Method"),
    DAYS_USING("Days-Using"),
    MONEY_SPENT("Money-Spent"),
    HELP_HARM("Help-Harm"),
    SMOKE_TIME("Smoke-Time"),
    START_DATE("Start-Date"),
    PREVIOUS_BREAK("Previous-Break"),
    SYMPTOMS("Symptoms"),
    TRIGGER("Trigger"),
    THEN_WHAT("Then-What"),
    COMMITMENT("Commitment"),
    LAST_SMOKED("Last-Smoked"),
    GUARDIAN_REPORT("Guardian-Report"),
    USAGE_DURATION("Usage-Duration"),
    BIOLOGICAL_SEX("Biological-Sex"),
    WHAT_BRINGS_YOU_HERE("What-Brings-You-Here"),

    // Life
    LO_USE_STATE("LO-Use-State"),

    // Life post assessment
    MENTAL_HEALTH("Mental-Health"),
    USED_LESS("Used-Less"),
    COMMENTS("Comments"),
    MODERATION_TECH("Moderation-Tech"),

    // New Break
    NEW_BREAK_TYPE("New-Break-Type"),
}

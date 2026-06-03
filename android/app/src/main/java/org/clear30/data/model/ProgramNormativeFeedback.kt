package org.clear30.data.model

import kotlinx.serialization.Serializable

/**
 * Normative feedback models — ported 1:1 from ProgramNormativeFeedback.swift.
 * Pure data (the rendering lives in the Feedback views), so the whole file ports
 * cleanly.
 */
@Serializable
data class ProgramNormativeFeedback(
    var scoreCards: List<ProgramNormativeFeedbackScore>,
    var amountCards: List<ProgramNormativeFeedbackNumber>,
    val sections: List<ProgramNormativeFeedbackTextSection>,
)

@Serializable
data class ProgramNormativeFeedbackScore(
    var title: String,
    var score: Double,
    var insight: String,
    var color1: String,
    var color2: String,
)

@Serializable
data class ProgramNormativeFeedbackNumber(
    var amount: String,
    var title: String,
    var subtitle: String,
    var color1: String,
    var color2: String,
)

@Serializable
data class ProgramNormativeFeedbackTextSection(
    var sectionTitle: String,
    var content: List<ProgramNormativeTextSectionContent>,
)

@Serializable
data class ProgramNormativeTextSectionContent(
    var title: String,
    var subtitle: String,
)

data class ProgramNormativeTextSectionContentOrdered(
    var title: String,
    var description: String,
    var priority: Int,
)

@Serializable
data class NormativeData(
    var frequency: String,
    var percentage: Int,
    var percentile_more_than: Int,
    var notes: String,
) {
    companion object {
        val weeklyUsageMapping: Map<Int, String> = mapOf(
            1 to "1–2 times per week",
            2 to "1–2 times per week",
            3 to "3–4 times per week",
            4 to "3–4 times per week",
            5 to "5–6 times per week",
            6 to "5–6 times per week",
            7 to "Daily (every day)",
        )

        val defaultData: List<NormativeData> = listOf(
            NormativeData("Never (no use in past year)", 62, 0, "Majority have not used in the past year"),
            NormativeData("Rarely (few times/year)", 13, 62, "Very occasional users"),
            NormativeData("About once per month", 2, 75, "Light monthly users"),
            NormativeData("2–3 times per month", 5, 77, "Still light use, less than weekly"),
            NormativeData("1–2 times per week", 5, 82, "Weekly users"),
            NormativeData("3–4 times per week", 4, 87, "Regular users, near-daily"),
            NormativeData("5–6 times per week", 3, 91, "Almost daily users"),
            NormativeData("Daily (every day)", 7, 94, "Daily or near-daily users"),
        )
    }
}

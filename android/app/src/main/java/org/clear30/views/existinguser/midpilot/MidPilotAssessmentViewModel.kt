package org.clear30.views.existinguser.midpilot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.clear30.data.Clear30Store
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.MidPilotResponses
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.submitMidPilotAssessment

/**
 * MidPilotAssessmentViewModel — port of MidPilotAssessmentViewModel.swift.
 * Holds the 8-slide responses and submits to `schools.submit_mid_pilot_assessment`
 * with the selected option STRINGS (not indices).
 */
class MidPilotAssessmentViewModel(
    val userInfo: UserInfo,
    val completion: () -> Unit,
) {
    var currentIndex by mutableIntStateOf(0)
    var isSubmitting by mutableStateOf(false)
    var submissionError by mutableStateOf(false)

    // Responses
    var withdrawalSymptoms: List<Int> = emptyList()
    var wellbeingSleep = 5
    var wellbeingAnxiety = 5
    var wellbeingFocus = 5
    var helpfulness = 5
    var unexpectedValue = ""
    var commitment = 5
    var campusWellness: List<Int> = emptyList()
    var recommendation = 5

    val campusWellnessOptions = listOf(
        "🏥 Visited my campus counseling or wellness center",
        "🔍 Looked into campus wellness resources",
        "💬 Talked to a friend, RA, or advisor about my use",
        "🌱 Not yet, but I'm more open to it than before",
        "🚫 I'm not interested in other support right now",
    )

    val withdrawalOptions = listOf(
        "😤 Irritability",
        "😴 Trouble sleeping",
        "🍽️ Loss of appetite",
        "😰 Anxiety",
        "🧲 Cravings",
        "🦶 Restlessness",
        "🌙 Vivid dreams",
        "✅ None of the above",
    )

    val helpfulnessEmojis = listOf("😖", "🙁", "😕", "😐", "🤔", "🙂", "😊", "😄", "🤩", "🌟")

    suspend fun submitAssessment() {
        if (isSubmitting) return
        isSubmitting = true
        submissionError = false

        val schoolId = userInfo.schoolId
        if (schoolId == null) {
            isSubmitting = false
            submissionError = true
            return
        }
        if (campusWellness.isEmpty() || campusWellness.any { it !in campusWellnessOptions.indices }) {
            isSubmitting = false
            submissionError = true
            return
        }

        val error = SupabaseController.submitMidPilotAssessment(
            schoolID = schoolId,
            responses = MidPilotResponses(
                withdrawal_symptoms = withdrawalSymptoms.map { withdrawalOptions[it] },
                wellbeing_sleep = wellbeingSleep,
                wellbeing_anxiety = wellbeingAnxiety,
                wellbeing_focus = wellbeingFocus,
                helpfulness = helpfulness,
                unexpected_value = unexpectedValue,
                commitment = commitment,
                campus_wellness = campusWellness.map { campusWellnessOptions[it] },
                recommendation = recommendation,
            ),
        )
        if (error == null) {
            userInfo.midPilotAssessmentCompleted = true
            runCatching { Clear30Store.save(userInfo) }
            completion()
        } else {
            isSubmitting = false
            submissionError = true
        }
    }
}

package org.clear30.data.model

import androidx.compose.ui.graphics.Brush
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.views.theme.Clear30Gradients

/** Group accountability levels — ported from OnboardingSetup.swift. */
@Serializable
enum class WalkthroughAccountabilityLevel {
    @SerialName("low") LOW,
    @SerialName("high") HIGH,
}

/** Group brightness/saturation for hue-based gradients (Clear30Group.swift). */
const val GROUP_BRIGHTNESS = 0.9
const val GROUP_SATURATION = 0.8

/**
 * OnboardingSetup — ported from OnboardingSetup.swift (`@Model`). The transient
 * bundle of choices made during onboarding, persisted via [org.clear30.data.LocalStore].
 */
@Serializable
class OnboardingSetup(
    var customCheckIn: CustomCheckIn? = null,
    var accountabilityLevel: WalkthroughAccountabilityLevel? = null,
    var groupToCreate: Clear30GroupOnboarding? = null,
    var groupToJoin: String? = null,
    var assessmentInfo: AssessmentInfo? = null,
    var guardianInfo: GuardianInfo? = null,
    var isUniversityCPP: Boolean = false,
) {
    companion object {
        const val STORE_KEY = "onboarding_setup"
    }
}

@Serializable
data class Clear30GroupOnboarding(
    var id: String,
    var userName: String,
    var hue: Double,
) {
    val groupName: String get() = "$userName's Group"

    val shareMessages: List<String>
        get() = listOf(
            "Join my Clear30 group! 🍃",
            "https://clear30.org/join-a-group/?group_id=$id&name=$userName",
        )

    val gradient: Brush get() = Clear30Gradients.hue(hue, GROUP_SATURATION, GROUP_BRIGHTNESS, hueOffset = 0.1)
}

@Serializable
data class AssessmentInfo(
    var responses: List<ProgramAssessmentResponse>,
    var lastSmoked: Instant,
    var choseClear30: Boolean,
)

@Serializable
data class GuardianInfo(
    val code: String,
    val free: Boolean,
    @SerialName("allow_guardian_reports") val allowGuardianReports: Boolean,
    @SerialName("assessment_id") val assessmentID: String? = null,
    var adolescent: Boolean? = null,
    @SerialName("notify_guardian") var notifyGuardian: Boolean? = false,
)

package org.clear30.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import org.clear30.data.LocalStore

/**
 * Experiment (feature-flag / A-B) engine — ported from ExperimentController.swift.
 *
 * `ExperimentKey` and `ExperimentOption` were enums-with-`custom(String)` that
 * Codable-encoded as single strings; here they're a value class / sealed class
 * with STRING serializers, so the `experiments` map still encodes with string
 * keys. `AnyCodable` payloads become kotlinx `JsonElement`.
 */
@Serializable(with = ExperimentKeySerializer::class)
@JvmInline
value class ExperimentKey(val rawValue: String) {
    companion object {
        // Assessment
        val adolescentAllowed = ExperimentKey("adolescent-allowed")
        val assessmentUsageDuration = ExperimentKey("assessment-usage-duration")
        val assessmentBiologicalSex = ExperimentKey("assessment-biological-sex")
        val welcomeTypingSlide = ExperimentKey("welcome-typing-slide")
        val whatBringsYouHereExperiment = ExperimentKey("what-brings-you-here")
        val singlePathDreamOutcome = ExperimentKey("single-path-dream-outcome")
        val removeSmokeTime = ExperimentKey("remove-smoke-time")

        // Sign up + Sales slide
        val onboardingNotifications = ExperimentKey("onboarding-notifications")
        val onboardingReviews = ExperimentKey("onboarding-reviews")
        val onboardingReferral = ExperimentKey("onboarding-referral")
        val onboardingSymptoms = ExperimentKey("onboarding-symptoms")
        val heliumPaywall = ExperimentKey("helium-paywall")
        val newOnboarding = ExperimentKey("new-onboarding")
        val newIntroScreen = ExperimentKey("new-intro-screen")
        val newSignUpScreen = ExperimentKey("new-sign-up-screen")
        val normativeFeedbackIntro = ExperimentKey("normative-feedback-intro")

        // In App
        val voiceClaire = ExperimentKey("voice-claire")
        val feedbackMethod = ExperimentKey("feedback-method")
        val postAssessmentInterview = ExperimentKey("post-assessment-interview")
        val postAssessmentTestimonial = ExperimentKey("post-assessment-testimonial")
        val postAssessmentCoachReferral = ExperimentKey("post-assessment-coach-referral")
        val videoTestimonialWelcome = ExperimentKey("video-testimonial-welcome")
        val threeDayEncouragementCancelSub = ExperimentKey("three-day-encouragement-cancel-sub")
        val settingsCancelSub = ExperimentKey("settings-cancel-sub")
        val supplementCards = ExperimentKey("supplement-cards")
        val shopifyApplePay = ExperimentKey("shopify-apple-pay")
        val messageTestimonialSubmission = ExperimentKey("message-testimonial-submission")
        val newTutorial = ExperimentKey("new-tutorial")
        val newTabIntro = ExperimentKey("new-tab-intro")
        val newSupportTab = ExperimentKey("new-support-tab")

        /** Any unrecognised string becomes a custom key (Swift `.custom`). */
        fun from(string: String) = ExperimentKey(string)
    }
}

object ExperimentKeySerializer : KSerializer<ExperimentKey> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ExperimentKey", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ExperimentKey) = encoder.encodeString(value.rawValue)
    override fun deserialize(decoder: Decoder): ExperimentKey = ExperimentKey.from(decoder.decodeString())
}

@Serializable(with = ExperimentOptionSerializer::class)
sealed class ExperimentOption {
    data object Show : ExperimentOption()
    data object Hide : ExperimentOption()
    data object Off : ExperimentOption()
    data class Custom(val value: String) : ExperimentOption()

    val rawValue: String
        get() = when (this) {
            is Show -> "show"; is Hide -> "hide"; is Off -> "off"; is Custom -> value
        }

    companion object {
        fun from(string: String, forLogging: Boolean = false): ExperimentOption = when (string) {
            "show" -> Show
            "hide" -> Hide
            "off" -> Off
            else -> if (string.startsWith("show-") && !forLogging) Show else Custom(string)
        }
    }
}

object ExperimentOptionSerializer : KSerializer<ExperimentOption> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ExperimentOption", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ExperimentOption) = encoder.encodeString(value.rawValue)
    override fun deserialize(decoder: Decoder): ExperimentOption = ExperimentOption.from(decoder.decodeString())
}

@Serializable
data class ExperimentConfig(
    val option: ExperimentOption,
    val payload: Map<String, JsonElement?> = emptyMap(),
)

/**
 * ExperimentController — ported from the `@Model`. Single persisted instance
 * (via [LocalStore]) holding the active experiments map.
 */
@Serializable
class ExperimentController(
    var experiments: MutableMap<ExperimentKey, ExperimentConfig> = mutableMapOf(),
) {
    fun showFeature(experimentKey: ExperimentKey, fallback: Boolean = true): Boolean =
        experiments[experimentKey]?.let { it.option == ExperimentOption.Show } ?: fallback

    fun getFeature(experimentKey: ExperimentKey): ExperimentConfig? = experiments[experimentKey]

    companion object {
        const val STORE_KEY = "experiment_controller"
    }
}

/** Decode an experiment payload into a typed object (Swift `payload.decode(to:)`). */
inline fun <reified T> Map<String, JsonElement?>.decodeTo(): T? = runCatching {
    val obj = kotlinx.serialization.json.JsonObject(filterValues { it != null }.mapValues { it.value!! })
    LocalStore.json.decodeFromJsonElement<T>(obj)
}.getOrNull()

package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RemoteFeedbackConfig — ported from GenericSubmitFeedback.swift. The payload of
 * the `feedback-method` experiment: it drives the Support-tab feedback card (the
 * "Feedback Monster" art comes from here, referenced by asset name) and the
 * feedback sheet (free-form text vs an external link), plus an optional
 * `completion` variant shown after the user has submitted.
 */
@Serializable
data class RemoteFeedbackConfig(
    @SerialName("card_title") val cardTitle: String,
    @SerialName("card_image") val cardImage: String,
    @SerialName("card_image_width") val cardImageWidth: Double? = null,
    @SerialName("card_bottom_image") val cardBottomImage: Boolean? = null,
    @SerialName("card_image_is_symbol") val cardImageIsSymbol: Boolean = false,

    val type: String,
    val image: String? = null,
    val emoji: String = "",
    val title: String = "",
    val subtitle: String = "",

    @SerialName("link_button_text") val linkButtonText: String? = null,
    val link: String? = null,

    val completion: RemoteFeedbackConfig? = null,
) {
    /** The device-local "already submitted" cache key (iOS `feedback_<type>`). */
    val cacheKey: String get() = "feedback_$type"
}

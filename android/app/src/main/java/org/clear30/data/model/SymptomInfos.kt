package org.clear30.data.model

import androidx.compose.ui.graphics.Brush
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.colorFromHex

/**
 * Symptom info models — ported from SymptomInfos.swift.
 *
 * Swift used a `SymptomInfosDecoded` with dynamic coding keys to decode an
 * arbitrary `[String: SymptomInfo]` object; kotlinx deserializes a
 * `Map<String, SymptomInfo>` directly, so that helper is unnecessary.
 */
@Serializable
class SymptomInfos(
    var symptomInfos: MutableMap<String, SymptomInfo> = mutableMapOf(),
    var smsFrequency: Int = 0,
    var symptomInfosJSON: String? = null,
    var symptomMessagesJSON: String? = null,
) {
    companion object {
        const val STORE_KEY = "symptom_infos"
    }
}

@Serializable
data class SymptomInfo(
    val color1: String,
    val color2: String,
    val tips: List<SymptomTip>,
    val reddits: Map<String, String>,
    val prompts: Map<String, String>,
    var selected: Boolean? = null,
    var messages: List<SymptomInfoMessage>? = null,
) {
    fun getGradient(): Brush = Clear30Gradients.linear(
        listOf(colorFromHex(color1), colorFromHex(color2)),
        Clear30Gradients.bottomLeading, Clear30Gradients.topTrailing,
    )
}

@Serializable
data class SymptomTip(
    val title: String,
    val sections: List<SymptomTipSection>,
    val color1: String,
    val color2: String,
)

@Serializable
data class SymptomTipSection(
    val heading: String,
    val content: String,
    val example: String? = null,
)

@Serializable
data class SymptomInfoMessage(
    val message: String,
    var sent: Instant? = null,
)

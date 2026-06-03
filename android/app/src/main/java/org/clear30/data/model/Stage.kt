package org.clear30.data.model

import androidx.compose.ui.graphics.Brush
import kotlinx.serialization.Serializable
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.colorFromHex

/**
 * Stage — ported from Stage.swift. The iOS init(from:) fell back to empty
 * strings on decode failure; with `ignoreUnknownKeys` + defaults here, partial
 * payloads decode the same way.
 */
@Serializable
data class Stage(
    val title: String = "",
    val subtitle: String = "",
    val body: String = "",
    val color1: String = "",
    val color2: String = "",
) {
    val gradient: Brush
        get() = Clear30Gradients.linear(
            listOf(colorFromHex(color1), colorFromHex(color2)),
            Clear30Gradients.bottomLeading, Clear30Gradients.topTrailing,
        )
}

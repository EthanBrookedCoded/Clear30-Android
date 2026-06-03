package org.clear30.data.model

import kotlinx.serialization.Serializable

/**
 * ContentInfo — ported from ProgramContent.swift. The per-day bundle of unlocked
 * messages + stage + scroll progress stored in `Program.contentInfo`.
 */
@Serializable
data class ContentInfo(
    var messages: List<ProgramMessage> = emptyList(),
    var stage: Stage? = null,
    var progress: Double? = null,
) {
    fun updateProgress(newProgress: Double) {
        progress = maxOf(progress ?: 0.0, newProgress)
    }
}

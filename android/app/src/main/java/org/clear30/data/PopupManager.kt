package org.clear30.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import org.clear30.util.now

/**
 * PopupManager — ported from `PopupManager` (Data/General/PopupManager.swift).
 *
 * On iOS this is a `@MainActor` ObservableObject that other screens read and
 * write to surface a single modal popup at a time; on Android we expose the same
 * idea as a process-scoped singleton with a [StateFlow] the root composable
 * collects. Callers enqueue popups via [request], the [AppRoot] renders the
 * head of the queue, and [dismissCurrent] advances.
 *
 * The popup *content* (cards, tutorial overlays, achievement reveals) is
 * modeled here as a discriminated [Payload] that views can pattern-match. As
 * more popup types are ported (achievement detail, tutorial highlight, generic
 * alert sheet) new [Payload] variants land — call sites enqueue, the renderer
 * grows a `when` arm.
 *
 * Pop-up coalescing: identical payloads queued within [COALESCE_WINDOW_MS] are
 * dropped — matches the iOS behavior where a duplicate `request(.achievement)`
 * fired during the dismissal animation doesn't stack a second sheet.
 */
object PopupManager {
    /** Discriminated popup content. New cases land as their views are ported. */
    sealed interface Payload {
        /** Standard one-button alert (info / error). [AlertHandler] for the master alert. */
        data class Alert(
            val title: String,
            val message: String,
            val confirmLabel: String = "OK",
            val onConfirm: () -> Unit = {},
        ) : Payload

        /** Confetti-style celebration overlay (no buttons; auto-dismisses). */
        data class Confetti(val message: String, val emoji: String = "🎉") : Payload

        /** Achievement reveal card. Resolved by the AchievementsSection renderer. */
        data class Achievement(val achievementKey: String) : Payload

        /** Tutorial highlight step (a single ring + caption). */
        data class TutorialStep(val screen: String, val captionKey: String) : Payload
    }

    /** Queue head exposed to the renderer. */
    private val _current = MutableStateFlow<Payload?>(null)
    val current: StateFlow<Payload?> = _current.asStateFlow()

    private val queue: ArrayDeque<Payload> = ArrayDeque()
    private val recent: ArrayDeque<Pair<Payload, Instant>> = ArrayDeque()
    private const val COALESCE_WINDOW_MS = 800L

    /**
     * Enqueue [payload]. If nothing is currently shown the renderer picks it up
     * on the next collect; otherwise it joins the FIFO queue. Duplicates queued
     * within [COALESCE_WINDOW_MS] are dropped silently.
     */
    @Synchronized
    fun request(payload: Payload) {
        val cutoff = now().toEpochMilliseconds() - COALESCE_WINDOW_MS
        // Drop old entries from the dedupe window so it doesn't grow unbounded.
        while (recent.isNotEmpty() && recent.first().second.toEpochMilliseconds() < cutoff) recent.removeFirst()
        if (recent.any { it.first == payload }) return
        recent.addLast(payload to now())

        if (_current.value == null) _current.value = payload
        else queue.addLast(payload)
    }

    /** Dismiss the head and surface the next queued popup, if any. */
    @Synchronized
    fun dismissCurrent() {
        _current.value = queue.removeFirstOrNull()
    }

    /** Drop everything (sign-out, deep-link reset). */
    @Synchronized
    fun clear() {
        queue.clear()
        recent.clear()
        _current.value = null
    }
}

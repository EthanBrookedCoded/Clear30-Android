package org.clear30.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * LoadingCoordinator — ported from `LoadingCoordinator` (Data/General/
 * LoadingCoordinator.swift).
 *
 * Tracks "is the app currently fetching something long enough that a spinner is
 * worth showing." Multiple concurrent tasks can be in flight (program sync,
 * paywall load, achievements refresh) — they each [begin] / [end] and the
 * coordinator exposes a single boolean for the renderer.
 *
 * Uses an AtomicInteger counter so concurrent begin/end calls from different
 * dispatchers behave predictably; the public [isLoading] only flips when the
 * count transitions from / to zero.
 */
object LoadingCoordinator {
    private val counter = AtomicInteger(0)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Begin a tracked operation. Pair with [end]. */
    fun begin() {
        val next = counter.incrementAndGet()
        if (next == 1) _isLoading.value = true
    }

    /** End a previously [begin]-tracked operation. */
    fun end() {
        val next = counter.updateAndGet { if (it <= 0) 0 else it - 1 }
        if (next == 0) _isLoading.value = false
    }

    /**
     * Run [block] with the loading flag held; safe under cancellation/throws.
     * `block` is `suspend` so call sites can await network work (the common
     * case) without spawning an extra scope.
     */
    suspend inline fun <T> tracked(block: () -> T): T {
        begin()
        try { return block() } finally { end() }
    }

    /** Hard reset (sign-out). Should not be needed in normal flow. */
    fun reset() {
        counter.set(0)
        _isLoading.value = false
    }
}

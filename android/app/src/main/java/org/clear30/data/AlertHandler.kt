package org.clear30.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AlertHandler — ported from `AlertHandler` (Data/General/AlertHandler.swift).
 *
 * The iOS app routes every "system" alert (network failure, sign-in error,
 * out-of-credits, paywall failure) through a single master `.alert` modifier so
 * we never stack two alerts. The Android equivalent is a StateFlow the root
 * composable consumes; one alert is visible at a time, the next call replaces
 * the previous one.
 *
 * Heavier popups (achievement reveal, tutorial overlay, confetti) go through
 * [PopupManager] — keeping the two channels separate so an info alert doesn't
 * jump the popup queue.
 */
object AlertHandler {
    data class Alert(
        val title: String,
        val message: String,
        val primaryLabel: String = "OK",
        val onPrimary: (() -> Unit)? = null,
        val secondaryLabel: String? = null,
        val onSecondary: (() -> Unit)? = null,
    )

    private val _current = MutableStateFlow<Alert?>(null)
    val current: StateFlow<Alert?> = _current.asStateFlow()

    /** Show (or replace) the master alert. */
    fun show(alert: Alert) { _current.value = alert }

    /** Convenience for the common one-button info alert. */
    fun info(title: String, message: String, onConfirm: () -> Unit = {}) =
        show(Alert(title = title, message = message, onPrimary = onConfirm))

    /** Convenience for an error surface — mirrors `AlertHandler.shared.error(...)`. */
    fun error(title: String = "Something went wrong", message: String) =
        show(Alert(title = title, message = message))

    fun dismiss() { _current.value = null }
}

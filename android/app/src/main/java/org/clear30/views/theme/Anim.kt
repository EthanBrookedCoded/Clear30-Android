package org.clear30.views.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Animation specs ported from GlobalData (Clear30App.swift).
 *
 * SwiftUI `.default.speed(1.5)` ~= a ~233ms ease curve; the spring matches
 * SwiftUI `.spring(response: 0.3, dampingFraction: 0.4)`.
 */
object Anim {
    /** GlobalData.defaultAnimation = .default.speed(1.5) */
    fun <T> default() = tween<T>(durationMillis = 233)

    /** GlobalData.springAnimation = .spring(response: 0.3, dampingFraction: 0.4) */
    fun <T> spring() = spring<T>(
        dampingRatio = 0.4f,
        stiffness = responseToStiffness(0.3f),
    )

    /** DefaultButtonStyle press = .spring(response: 0.15, dampingFraction: 0.5) —
     *  the snappy shrink-and-pop every button uses. */
    fun <T> pressSpring() = spring<T>(
        dampingRatio = 0.5f,
        stiffness = responseToStiffness(0.15f),
    )

    /** GlobalData.defaultTransition = .scale + .spring(response: 0.175, dampingFraction: 1) —
     *  card/pop-in appear/disappear. */
    fun <T> transitionSpring() = spring<T>(
        dampingRatio = 1f,
        stiffness = responseToStiffness(0.175f),
    )

    /** GlobalData.rewardSpringAnimation = .spring(response: 0.45, dampingFraction: 0.75) —
     *  the heavier celebratory bounce (check-in rewards, counters). */
    fun <T> rewardSpring() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = responseToStiffness(0.45f),
    )

    /** defaultAnimation.speed(2) — e.g. the tab-bar select (≈117ms). */
    fun <T> fast() = tween<T>(durationMillis = 117)

    /** GlobalData.wait = 0.1s */
    const val waitMillis = 100L

    // SwiftUI spring "response" is the period; stiffness = (2*pi/response)^2.
    private fun responseToStiffness(response: Float): Float {
        val omega = (2.0 * Math.PI / response)
        return (omega * omega).toFloat()
    }
}

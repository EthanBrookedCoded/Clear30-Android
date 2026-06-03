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

    /** GlobalData.wait = 0.1s */
    const val waitMillis = 100L

    // SwiftUI spring "response" is the period; stiffness = (2*pi/response)^2.
    private fun responseToStiffness(response: Float): Float {
        val omega = (2.0 * Math.PI / response)
        return (omega * omega).toFloat()
    }
}

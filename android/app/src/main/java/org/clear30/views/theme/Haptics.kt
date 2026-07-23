package org.clear30.views.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptics ported from GlobalData (Clear30App.swift). iOS UIFeedbackGenerator
 * styles map onto Android [VibrationEffect] predefined effects / amplitudes.
 *
 * Initialise once from Application/Activity via [init]; then call the helpers
 * anywhere (mirrors GlobalData.shared.lightImpact() etc.).
 */
object Haptics {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /** GlobalData.prepHaptics() — no-op on Android (no engine warm-up needed). */
    fun prepHaptics() { /* vibrator is allocated lazily in init */ }

    fun lightImpact() = effect(EFFECT_TICK, amplitude = 80)
    fun mediumImpact() = effect(EFFECT_CLICK, amplitude = 150)
    fun successHeavy() = pattern(longArrayOf(0, 30, 60, 30))   // double tap
    fun successLight() = effect(EFFECT_CLICK, amplitude = 120) // .warning
    fun error() = pattern(longArrayOf(0, 40, 50, 40, 50, 40))  // triple buzz

    private fun effect(predefined: Int, amplitude: Int) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && v.hasAmplitudeControl()) {
            v.vibrate(VibrationEffect.createPredefined(predefined))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(20, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(20)
        }
    }

    private fun pattern(timings: LongArray) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(timings, -1)
        }
    }

    private const val EFFECT_CLICK = VibrationEffect.EFFECT_CLICK
    private const val EFFECT_TICK = VibrationEffect.EFFECT_TICK
}

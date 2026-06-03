package org.clear30.data

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C

/**
 * AudioBaseline — ported from `AudioSessionManager.setupBaseline()` (iOS).
 *
 * The iOS app set `AVAudioSession.sharedInstance().setCategory(.playback,
 * options: .mixWithOthers)` once at launch so a meditation could continue
 * over a phone call's earpiece audio, and so the system speaker volume
 * physical-buttons behaved as media volume.
 *
 * Android does this per-player rather than process-wide: every ExoPlayer
 * picks AudioAttributes at construction time. Exposing them as a shared
 * value here keeps every player consistent — Meditations, message-embedded
 * Reddit clips, and the future Dr Fred voice mode all read [attributes].
 */
object AudioBaseline {
    /** Cached AudioAttributes for the meditation/voice playback baseline. */
    lateinit var attributes: AudioAttributes
        private set

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        attributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()
    }
}

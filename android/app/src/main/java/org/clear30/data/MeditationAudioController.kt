package org.clear30.data

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MeditationAudioController — the app-wide meditation audio engine (iOS
 * `AudioSessionManager` + the AVPlayer in `AudioPlayerViewModel`). One shared
 * ExoPlayer lives here instead of per-composable, so playback survives the UI
 * leaving composition and — hosted by [MeditationPlaybackService] — keeps
 * playing with the app backgrounded (iOS `UIBackgroundModes: audio`), with
 * lock-screen controls from the media notification.
 *
 * The sleep timer also lives here (app-lifetime scope, not a composable
 * `LaunchedEffect`) so "close the app, it plays, it stops when the timer is
 * up" actually holds.
 */
object MeditationAudioController {
    private var exo: ExoPlayer? = null
    private var sleepJob: Job? = null

    /** Remaining sleep-timer selection (minutes), for the menu label. */
    val sleepTimerMinutes = MutableStateFlow<Int?>(null)

    /** The shared player, created on first use (main thread only). */
    fun player(context: Context): ExoPlayer =
        exo ?: ExoPlayer.Builder(context.applicationContext).build().apply {
            // Process-wide audio attributes baseline (iOS AVAudioSession
            // .playback analog). handleAudioFocus = true so the meditation
            // pauses on incoming call / nav prompt instead of being talked over.
            setAudioAttributes(AudioBaseline.attributes, /* handleAudioFocus = */ true)
        }.also { exo = it }

    /**
     * Point the shared player at [url] (keeping position if it's already the
     * current item) and return it. Starts paused — playback begins on the
     * play tap, which should also call [ensureService].
     */
    fun prepare(context: Context, url: String, title: String? = null): ExoPlayer {
        val p = player(context)
        val trimmed = url.trim()
        if (p.currentMediaItem?.localConfiguration?.uri?.toString() != trimmed) {
            val item = MediaItem.Builder()
                .setUri(trimmed)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(title ?: "Meditation").setArtist("Clear30").build(),
                )
                .build()
            p.setMediaItem(item)
            p.prepare()
            p.playWhenReady = false
        }
        return p
    }

    /**
     * Start the media-session service so the OS keeps the process (and audio)
     * alive in the background. Safe to call repeatedly; media3 promotes the
     * service to foreground while the player is playing.
     */
    fun ensureService(context: Context) {
        runCatching { context.startService(Intent(context, MeditationPlaybackService::class.java)) }
    }

    /**
     * Arm (or clear, with null) the sleep timer. Runs on the app-lifetime
     * scope; on expiry the shared player pauses — including in the background,
     * since the foreground service keeps the process alive (iOS
     * `.clear30SleepTimerExpired`).
     */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepTimerMinutes.value = minutes
        sleepJob = if (minutes == null) {
            null
        } else {
            org.clear30.Clear30Application.appScope.launch {
                delay(minutes * 60_000L)
                withContext(Dispatchers.Main) { exo?.pause() }
                sleepTimerMinutes.value = null
            }
        }
    }
}

/**
 * Foreground media service hosting the shared meditation player's
 * [MediaSession] — gives background playback plus the system media
 * notification / lock-screen controls (media3 manages the foreground
 * promotion and notification automatically).
 */
class MeditationPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, MeditationAudioController.player(this)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away stops audio (the sleep flow keeps the task).
        mediaSession?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        // Release only the session — the player itself is app-scoped and may
        // be rebound if the service restarts.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}

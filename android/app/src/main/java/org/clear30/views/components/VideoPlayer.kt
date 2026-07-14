package org.clear30.views.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * In-app video playback (replaces the previous `UriHandler.openUri` jumps to the
 * browser / external player). Mirrors iOS `InlineNativeVideoView` (AVPlayer):
 * direct video files (`.mp4`/`.mov`, Supabase storage URLs, on-disk journal
 * clips) play through Media3 ExoPlayer; YouTube links play through an embedded
 * IFrame ([YouTubeDialog]) since ExoPlayer can't read a YouTube watch URL.
 */

/** A 16:9 thumbnail card with a centered play badge. The non-playing state of
 *  [InlineVideoPlayer], also reused as a tappable cover for YouTube / feed cards. */
@Composable
fun VideoThumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val base = modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(Dimens.cornerRadius))
        .background(Clear30Colors.opacityGray)
    Box(
        if (onClick != null) base.clickable(onClick = onClick) else base,
        contentAlignment = Alignment.Center,
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        PlayBadge()
    }
}

@Composable
private fun PlayBadge() {
    Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(14.dp)) {
        Icon(sfSymbol("play.fill"), contentDescription = "Play", tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * Thumbnail + play badge that swaps to an inline ExoPlayer (with controls) on
 * tap, staying within the screen. Use for direct video URLs / files.
 */
@Composable
fun InlineVideoPlayer(
    uri: String,
    thumbnailUrl: String? = null,
    modifier: Modifier = Modifier,
    onStart: (() -> Unit)? = null,
) {
    var playing by remember(uri) { mutableStateOf(false) }
    if (!playing) {
        VideoThumbnail(thumbnailUrl, modifier) { playing = true; onStart?.invoke() }
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Dimens.cornerRadius))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) { VideoSurface(uri, Modifier.fillMaxSize()) }
    }
}

/**
 * FeedNativeVideoPlayer — iOS `InlineNativeVideoView`: a bare full-bleed video
 * (no card chrome, no platform controller) with the `MediaProgressBar`
 * treatment — a draggable white progress track pinned to the bottom edge.
 * Plays while [playTrigger] is true (feed page focused), pauses when it flips
 * false, and tap toggles play/pause like iOS.
 */
@Composable
fun FeedNativeVideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
    playTrigger: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // iOS `isFeedFocused && !isFeedScrolling` → play / pause.
    LaunchedEffect(playTrigger, uri) {
        if (playTrigger) player.play() else player.pause()
    }

    var positionMs by remember(uri) { mutableLongStateOf(0L) }
    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    LaunchedEffect(uri) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            kotlinx.coroutines.delay(250)
        }
    }

    Box(
        modifier
            .clickable { if (player.isPlaying) player.pause() else player.play() },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; setUseController(false) } },
            modifier = Modifier.fillMaxSize(),
        )
        // MediaProgressBar — a draggable white track pinned to the bottom edge
        // (iOS overlays it inside the video frame with cardSpacing insets).
        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        BoxWithConstraints(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Dimens.cardSpacing)
                .padding(bottom = Dimens.cardSpacing)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            fun seekTo(x: Float) {
                if (durationMs > 0 && widthPx > 0) {
                    val target = ((x / widthPx).coerceIn(0f, 1f) * durationMs).toLong()
                    positionMs = target
                    player.seekTo(target)
                }
            }
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(widthPx, durationMs) {
                        detectTapGestures { offset -> seekTo(offset.x) }
                    }
                    .pointerInput(widthPx, durationMs) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            seekTo(change.position.x)
                        }
                    },
            )
        }
    }
}

/** Full-screen immediate-playback dialog for a direct video URL / on-disk file. */
@Composable
fun VideoPlayerDialog(uri: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoSurface(uri, Modifier.fillMaxSize().align(Alignment.Center))
            CloseButton(onDismiss)
        }
    }
}

/** Open [videoId] in the YouTube app (or a browser) — the fallback when the
 *  in-app embed can't play (blocked embed, region lock, removed video). */
fun openYouTubeExternally(context: android.content.Context, videoId: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId"),
            ),
        )
    }
}

/**
 * Embedded YouTube player driven by the IFrame Player API with a JS bridge, so
 * in-player failures actually surface (a raw embed WebView renders embed errors
 * as a white/black frame with no signal). Mirrors iOS's YouTubePlayerKit error
 * state (`YouTubeViewer.swift:150-158`): [onFailed] fires on player error codes
 * (2 invalid id, 5 HTML5 error, 100 removed/private, 101/150 embedding
 * disabled), a main-frame load failure, or a load timeout; [onPlaying] fires
 * when playback actually starts.
 *
 * The page is served via `loadDataWithBaseURL("https://www.youtube.com", …)` so
 * the player sees a genuine youtube.com origin — an opaque/null origin is what
 * triggers the embedded-player "Error code: 150/152".
 */
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    onPlaying: () -> Unit = {},
    onFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    var playing by remember(videoId) { mutableStateOf(false) }

    // If the IFrame API never reaches a playable state (script blocked, dead
    // network — cases with no error callback at all), fail over after 12s.
    androidx.compose.runtime.LaunchedEffect(videoId) {
        kotlinx.coroutines.delay(12_000)
        if (!playing) onFailed()
    }

    val webView = remember(videoId) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Cache aggressively so re-opening a video (or the same one after a
            // network blip) is near-instant instead of a full cold load each time.
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            setBackgroundColor(android.graphics.Color.BLACK)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) mainHandler.post { onFailed() }
                }
            }
            addJavascriptInterface(
                object {
                    @android.webkit.JavascriptInterface
                    fun playerStateChange(state: Int) {
                        // 1 = playing, 3 = buffering — both mean the video works.
                        if (state == 1 || state == 3) mainHandler.post { playing = true; onPlaying() }
                    }

                    @android.webkit.JavascriptInterface
                    fun playerError(code: Int) {
                        android.util.Log.w("YouTubeEmbed", "player error $code for $videoId")
                        mainHandler.post { onFailed() }
                    }
                },
                "Clear30Bridge",
            )
            loadDataWithBaseURL(
                "https://www.youtube.com",
                """
                <!DOCTYPE html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden}
                #player{position:absolute;top:0;left:0;width:100%;height:100%}</style>
                </head><body><div id="player"></div>
                <script src="https://www.youtube.com/iframe_api"></script>
                <script>
                function onYouTubeIframeAPIReady() {
                  new YT.Player('player', {
                    videoId: '$videoId',
                    playerVars: {autoplay: 1, playsinline: 1, fs: 1, rel: 0},
                    events: {
                      onReady: function(e) { e.target.playVideo(); },
                      onStateChange: function(e) { Clear30Bridge.playerStateChange(e.data); },
                      onError: function(e) { Clear30Bridge.playerError(e.data); }
                    }
                  });
                }
                </script></body></html>
                """.trimIndent(),
                "text/html",
                "utf-8",
                null,
            )
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
    AndroidView(factory = { webView }, modifier = modifier)
}

/**
 * Thumbnail + play badge that swaps to the embedded YouTube player IN PLACE on
 * tap — the feed-card equivalent of iOS's inline `YouTubeViewer(inline: true)`.
 * On an embed failure it swaps to a "Watch on YouTube" fallback in the same
 * 16:9 frame.
 */
@Composable
fun InlineYouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = false,
    onStart: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var playing by remember(videoId) { mutableStateOf(false) }
    var failed by remember(videoId) { mutableStateOf(false) }
    // Feed-focus autoplay (iOS YouTubeViewer `isFeedFocused`): mounting the
    // embed starts playback; unmounting on focus loss stops it so a swiped-away
    // card never keeps playing audio.
    LaunchedEffect(autoplay) {
        if (autoplay && !failed && !playing) {
            playing = true
            onStart?.invoke()
        } else if (!autoplay && playing) {
            playing = false
        }
    }
    val frame = modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(Dimens.cornerRadius))
        .background(Color.Black)
    when {
        !playing -> VideoThumbnail(
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            modifier,
        ) { playing = true; onStart?.invoke() }
        failed -> Box(frame, contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SmallText("This video can't play in-app.", color = Color.White)
                androidx.compose.foundation.layout.Spacer(Modifier.size(Dimens.cardSpacing))
                StretchedButton("Watch on YouTube", gradient = Clear30Gradients.youtube) {
                    openYouTubeExternally(context, videoId)
                }
            }
        }
        else -> Box(frame) {
            // Thumbnail underneath so the tapped frame stays visible while the
            // embed loads; the WebView draws over it once ready.
            AsyncImage(
                model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            YouTubeEmbedPlayer(
                videoId = videoId,
                onFailed = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Full-screen embedded YouTube player (in-app, no external jump). */
@Composable
fun YouTubeDialog(videoId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var loading by remember(videoId) { mutableStateOf(true) }
    var failed by remember(videoId) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().popEntrance().background(Color.Black)) {
            // Show the thumbnail INSTANTLY behind the player so tapping gives an
            // immediate visual (the frame you tapped), not a black screen + spinner
            // while YouTube's embed page loads. The WebView draws over it once ready.
            if (loading || failed) {
                AsyncImage(
                    model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.Center),
                )
            }
            if (!failed) {
                YouTubeEmbedPlayer(
                    videoId = videoId,
                    onPlaying = { loading = false },
                    onFailed = { failed = true; loading = false },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.Center),
                )
            }
            when {
                failed -> androidx.compose.foundation.layout.Column(
                    modifier = Modifier.align(Alignment.Center).padding(Dimens.horizontalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SmallText("This video can't play in-app.", color = Color.White)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(Dimens.cardSpacing))
                    StretchedButton("Watch on YouTube", gradient = Clear30Gradients.youtube) { openYouTubeExternally(context, videoId) }
                }
                loading -> androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                )
            }
            // Always-available external escape hatch, even mid-load, so a slow embed
            // never traps the user on a spinner.
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Dimens.cardSpacing / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    sfSymbol("arrow.up.right.square"),
                    contentDescription = "Open in YouTube",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp).pressScale { openYouTubeExternally(context, videoId) }.padding(4.dp),
                )
            }
            CloseButton(onDismiss)
        }
    }
}

/**
 * Full-screen in-app web viewer (Reddit threads, articles, any external link) so
 * people don't leave the app. Mirrors iOS's WebView-based RedditViewer.
 */
@Composable
fun WebViewDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val webView = remember(url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Keep navigation inside the in-app viewer instead of bouncing to the
            // browser / native app.
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl(url)
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().popEntrance().background(Clear30Colors.background)) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 44.dp),
            )
            CloseButton(onDismiss)
        }
    }
}

/** The actual Media3 PlayerView surface; owns + releases the ExoPlayer. */
@Composable
private fun VideoSurface(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }
    // Pause when the app is backgrounded so audio doesn't keep playing.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; setUseController(true) } },
        modifier = modifier,
    )
}

@Composable
private fun BoxScope.CloseButton(onClick: () -> Unit) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(Dimens.cardSpacing)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Icon(sfSymbol("xmark"), contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * Extract a YouTube video id from a watch / short / embed URL, or null if the
 * URL isn't YouTube (so the caller can fall back to the native player).
 */
fun youTubeId(url: String): String? {
    val patterns = listOf(
        Regex("""youtu\.be/([\w-]{11})"""),
        Regex("""youtube\.com/watch\?v=([\w-]{11})"""),
        Regex("""youtube\.com/embed/([\w-]{11})"""),
        Regex("""youtube\.com/shorts/([\w-]{11})"""),
    )
    for (p in patterns) p.find(url)?.let { return it.groupValues[1] }
    return null
}

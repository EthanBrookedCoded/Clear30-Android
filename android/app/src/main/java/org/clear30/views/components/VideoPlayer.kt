package org.clear30.views.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Full-screen embedded YouTube IFrame player (in-app, no external jump). */
@Composable
fun YouTubeDialog(videoId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var loading by remember(videoId) { mutableStateOf(true) }
    var failed by remember(videoId) { mutableStateOf(false) }

    // Open the video in the YouTube app (or a browser) — the escape hatch when the
    // in-app embed is blocked (error 150/152) or the network is too slow to wait on.
    val openExternally = {
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId"),
                ),
            )
        }
    }

    val webView = remember(videoId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // Cache aggressively so re-opening a video (or the same one after a
            // network blip) is near-instant instead of a full cold load each time.
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            setBackgroundColor(android.graphics.Color.BLACK)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) { loading = false }
                // Only a MAIN-frame failure means the video won't play — surface the
                // "Watch on YouTube" fallback then (subresource errors are noise).
                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) { failed = true; loading = false }
                }
            }
            // Load the embed page DIRECTLY (not a raw <iframe> inside loadData):
            // a real navigation to youtube.com gives the player a genuine
            // youtube.com origin + referer, which avoids the embedded-player
            // "Error code: 150/152" that an opaque/null origin triggers. Send a
            // matching Referer too, belt-and-suspenders.
            loadUrl(
                "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&fs=1&rel=0",
                mapOf("Referer" to "https://www.youtube.com"),
            )
        }
    }
    DisposableEffect(webView) { onDispose { webView.destroy() } }
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
                AndroidView(
                    factory = { webView },
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
                    StretchedButton("Watch on YouTube", gradient = Clear30Gradients.youtube) { openExternally() }
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
                    modifier = Modifier.size(28.dp).pressScale { openExternally() }.padding(4.dp),
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

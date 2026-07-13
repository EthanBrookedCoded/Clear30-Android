package org.clear30.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.Instant
import org.clear30.data.RedditComment
import org.clear30.data.RedditPost
import org.clear30.data.RedditScraper
import org.clear30.data.RedditThread
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.util.now

/**
 * RedditDialog — the in-app Reddit viewer (port of iOS `RedditViewer`). Fetches
 * the thread JSON via [RedditScraper] and renders the post + nested comments
 * natively instead of loading reddit.com in a WebView. If the fetch fails (e.g.
 * a share link the `reddit_proxy` edge function can't parse), it falls back to
 * [WebViewDialog] so the thread is always reachable.
 */
@Composable
fun RedditDialog(url: String, onDismiss: () -> Unit) {
    var thread by remember(url) { mutableStateOf<RedditThread?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }

    androidx.compose.runtime.LaunchedEffect(url) {
        val t = RedditScraper.fetch(url)
        if (t != null) thread = t else failed = true
        loading = false
    }

    if (failed) {
        WebViewDialog(url, onDismiss) // graceful fallback to the web thread
        return
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().popEntrance(), color = Clear30Colors.background) {
            Column(Modifier.fillMaxSize()) {
                RedditTopBar(onClose = onDismiss, onOpenWeb = url)
                val t = thread
                if (loading || t == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Clear30Colors.reddit1)
                    }
                } else {
                    RedditThreadContent(t)
                }
            }
        }
    }
}

@Composable
private fun RedditTopBar(onClose: () -> Unit, onOpenWeb: String) {
    val uri = LocalUriHandler.current
    Row(
        Modifier.fillMaxWidth().background(Clear30Gradients.reddit).padding(Dimens.cardSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallText("Reddit", color = Color.White)
        Spacer(Modifier.weight(1f))
        SmallText("Open ↗", color = Color.White, modifier = Modifier.pressScale { uri.openUri(onOpenWeb) })
        Box(Modifier.padding(start = Dimens.cardSpacing).size(28.dp).clip(RoundedCornerShape(8.dp)).pressScale { onClose() }) {
            Icon(sfSymbol("xmark"), contentDescription = "Close", tint = Color.White)
        }
    }
}

@Composable
private fun RedditThreadContent(thread: RedditThread) {
    val post = thread.post
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        TinyText("r/${post.subreddit}", color = Clear30Colors.text.copy(alpha = 0.5f))
        AuthorRow(post.author, post.createdUtc)
        Heading3(post.title)
        postBody(post)?.let { SmallText(it, color = Clear30Colors.text.copy(alpha = 0.75f)) }
        RedditStats(score = post.score, comments = post.numComments)

        Spacer(Modifier.size(Dimens.cardSpacing / 2))
        Box(Modifier.fillMaxWidth().size(1.dp).background(Clear30Colors.text.copy(alpha = 0.25f)))

        // Comments: reveal 5 at a time (iOS `commentsToShow` = 5, +10 on "Show more").
        var shown by remember { mutableStateOf(5) }
        val visible = thread.comments.take(shown)
        visible.forEach { RedditCommentView(it) }
        if (thread.comments.size > shown) {
            Spacer(Modifier.size(Dimens.cardSpacing / 2))
            StretchedButton("Show more comments", modifier = Modifier.fillMaxWidth()) { shown += 10 }
        }
        Spacer(Modifier.size(Dimens.cardSpacing))
    }
}

@Composable
private fun RedditCommentView(comment: RedditComment) {
    var repliesOpen by remember { mutableStateOf(comment.depth < 1) } // top-level replies open by default
    Column(Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 2)) {
        AuthorRow(comment.author, comment.createdUtc)
        SmallText(comment.body, color = Clear30Colors.text.copy(alpha = 0.75f))
        if (comment.score != 0) TinyText(scoreLabel(comment.score), color = Clear30Colors.text.copy(alpha = 0.5f))

        if (comment.replies.isNotEmpty()) {
            TinyText(
                if (repliesOpen) "Hide replies" else "Show ${comment.replies.size} repl${if (comment.replies.size == 1) "y" else "ies"}",
                color = Clear30Colors.reddit1,
                modifier = Modifier.padding(top = 2.dp).pressScale { repliesOpen = !repliesOpen },
            )
            if (repliesOpen) {
                Row(Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing / 3)) {
                    // Thread line + indent for the nested replies.
                    Box(Modifier.width(2.dp).background(Clear30Colors.text.copy(alpha = 0.25f)))
                    Column(Modifier.padding(start = Dimens.cardSpacing / 2)) {
                        comment.replies.forEach { RedditCommentView(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthorRow(author: String, createdUtc: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 3)) {
        TinyText(authorEmoji(author))
        TinyText("u/$author", color = Clear30Colors.text.copy(alpha = 0.75f))
        val ago = timeAgo(createdUtc)
        if (ago.isNotEmpty()) TinyText("· $ago", color = Clear30Colors.text.copy(alpha = 0.5f))
    }
}

@Composable
private fun RedditStats(score: Int, comments: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(sfSymbol(if (score >= 0) "chevron.up" else "chevron.down"), contentDescription = null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            TinyText("${kotlin.math.abs(score)}", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(sfSymbol("text.bubble.fill"), contentDescription = null, tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            TinyText("$comments", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
    }
}

private fun postBody(post: RedditPost): String? = when (post.body.trim()) {
    "" -> null
    "[removed]", "[deleted]" ->
        "The original post was removed, however we've still included it as the comments are helpful."
    else -> post.body
}

private fun scoreLabel(score: Int): String =
    "${if (score >= 0) "▲" else "▼"} ${kotlin.math.abs(score)}"

// Deterministic per-author emoji from iOS's 16 priority emojis (iOS assigns
// randomly per thread; a stable hash is simpler and reads the same in-app).
private val REDDIT_EMOJIS = listOf(
    "❤️", "😊", "🙏", "🔥", "✨", "💪", "😍", "🥰", "💯", "🤗", "👌", "👏", "🫶", "🤙", "‼️", "🎉",
)

private fun authorEmoji(name: String): String {
    if (name.isEmpty()) return "😊"
    val h = name.fold(0) { acc, c -> acc * 31 + c.code }
    return REDDIT_EMOJIS[((h % REDDIT_EMOJIS.size) + REDDIT_EMOJIS.size) % REDDIT_EMOJIS.size]
}

private fun timeAgo(createdUtc: Double): String {
    if (createdUtc <= 0) return ""
    val then = Instant.fromEpochSeconds(createdUtc.toLong())
    val secs = (now() - then).inWholeSeconds
    return when {
        secs < 60 -> "Just now"
        secs < 3600 -> "${secs / 60} min ago"
        secs < 86_400 -> "${secs / 3600} hr ago"
        secs < 604_800 -> "${secs / 86_400} day ago"
        secs < 2_629_800 -> "${secs / 604_800} week ago"
        secs < 31_557_600 -> "${secs / 2_629_800} month ago"
        else -> "${secs / 31_557_600} year ago"
    }
}

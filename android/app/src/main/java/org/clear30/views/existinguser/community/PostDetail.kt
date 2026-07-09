package org.clear30.views.existinguser.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.clear30.data.model.Comment
import org.clear30.data.model.Post
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.addComment
import org.clear30.data.supabase.addReaction
import org.clear30.data.supabase.getPostComments
import org.clear30.data.supabase.getUserID
import org.clear30.data.supabase.incrementCommunityPostView
import org.clear30.data.supabase.removeReaction
import androidx.compose.ui.text.style.TextAlign
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.MultiLineOffWhiteInput
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** Emojis offered when adding a reaction (the "+" picker). */
private val REACTIONS = listOf("❤️", "🙌", "🔥", "🌱", "👏", "😎", "🥹", "💪")

/**
 * PostDetail — the full post, its reaction pills (aggregated from the post's
 * `reactions`), and its comments (fetched from community.comments — the feed RPC
 * only returns a count). Add a reaction or a comment from the bottom row.
 */
@Composable
fun PostDetail(post: Post, userInfo: UserInfo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val comments = remember { mutableStateListOf<Comment>() }
    var newComment by remember { mutableStateOf("") }

    LaunchedEffect(post.id) {
        // Count this open as a view (community.increment_view_count).
        SupabaseController.incrementCommunityPostView(post.id)
        SupabaseController.getPostComments(post.id).onSuccess { fetched ->
            comments.clear()
            comments.addAll(fetched)
            fetched.forEach { c -> c.userId?.let { org.clear30.data.UserDirectory.lookup(it) } }
            org.clear30.data.UserDirectory.flush()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading3(post.title)
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            // Video posts play inline (in-app ExoPlayer) at the top of the detail.
            if (post.isVideo && !post.videoUrl.isNullOrBlank()) {
                item {
                    org.clear30.views.components.InlineVideoPlayer(
                        uri = post.videoUrl!!,
                        thumbnailUrl = post.thumbnailUrl,
                        modifier = Modifier.padding(top = Dimens.cardSpacing),
                    )
                }
            }
            if (post.body.isNotBlank()) {
                item { SmallText(post.body, Modifier.padding(vertical = Dimens.cardSpacing)) }
            }
            item {
                ReactionRow(
                    post = post,
                    userInfo = userInfo,
                    onReact = { emoji ->
                        scope.launch {
                            val uid = SupabaseController.getUserID() ?: userInfo.userID
                            org.clear30.data.Logger.logEvent(
                                userInfo.loggingID,
                                org.clear30.data.LogEventType.reactedToCommunityPost,
                                mapOf(
                                    org.clear30.data.LogEventExtraDataType.ID to post.id,
                                    org.clear30.data.LogEventExtraDataType.EXTRA to emoji,
                                ),
                            )
                            SupabaseController.addReaction(post.id, uid, emoji)
                        }
                    },
                    onRemove = { emoji ->
                        scope.launch {
                            val uid = SupabaseController.getUserID() ?: userInfo.userID
                            SupabaseController.removeReaction(post.id, uid, emoji)
                        }
                    },
                )
            }
            item {
                val count = maxOf(post.totalCommentsCount, comments.size)
                if (count == 0) {
                    // iOS PostDetailView empty state: centered SmallText at 0.5.
                    SmallText(
                        "Be the first to comment!",
                        Modifier.fillMaxWidth().padding(vertical = Dimens.cardSpacing * 2),
                        color = Clear30Colors.text.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    TinyText("$count comments", color = Clear30Colors.text.copy(alpha = 0.5f))
                }
            }
            items(comments) { c -> CommentCard(c) }
        }

        // iOS ChatComposeMessageViewBinding: borderless off-white compose field +
        // a gradient arrow.up circle that only appears once there's text.
        fun sendComment() {
            val body = newComment.trim()
            if (body.isEmpty()) return
            newComment = ""
            comments.add(Comment(id = "local-${comments.size}", userId = userInfo.userID, body = body))
            scope.launch {
                val uid = SupabaseController.getUserID() ?: userInfo.userID
                org.clear30.data.Logger.logEvent(
                    userInfo.loggingID,
                    org.clear30.data.LogEventType.commentedOnCommunityPost,
                    mapOf(org.clear30.data.LogEventExtraDataType.ID to post.id),
                )
                SupabaseController.addComment(post.id, uid, body)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            MultiLineOffWhiteInput(newComment, { newComment = it }, placeholder = "Comment", modifier = Modifier.weight(1f), smallText = true)
            if (newComment.isNotBlank()) {
                Box(
                    Modifier.pressScale(onClick = { sendComment() }).size(30.dp).clip(CircleShape).background(Clear30Gradients.clear30),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        sfSymbol("arrow.up"),
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

/** A single comment: author label (from the user directory) + body. */
@Composable
private fun CommentCard(c: Comment) {
    val directory by org.clear30.data.UserDirectory.cache.collectAsStateWithLifecycle()
    val author = remember(c.userId, directory) { c.userId?.let { org.clear30.data.UserDirectory.lookup(it) } }
    // iOS CommentView renders comments card-less: emoji at full opacity, the
    // name at 0.5 (UserNameWithEmoji), then the body.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
        author?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4)) {
                it.emoji?.let { e -> SmallText(e) }
                SmallText(it.displayName, color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
        SmallText(c.body)
    }
}

/**
 * ReactionRow — pills for each emoji actually used on the post (counts derived
 * from the `reactions` array the feed returns), the user's own reaction
 * highlighted, plus a "+" that reveals an emoji picker to add one.
 */
@Composable
private fun ReactionRow(post: Post, userInfo: UserInfo, onReact: (String) -> Unit, onRemove: (String) -> Unit) {
    val serverCounts = remember(post.reactions) {
        post.reactions.orEmpty().groupingBy { it.emoji }.eachCount()
    }
    val initialMine = remember(post.reactions, userInfo.userID) {
        post.reactions?.firstOrNull { it.userId == userInfo.userID }?.emoji
    }
    var mine by remember { mutableStateOf(initialMine) }
    var showPicker by remember { mutableStateOf(false) }

    // Toggle the user's reaction: tapping their current emoji removes it; tapping a
    // different one swaps (remove the old, add the new), matching a one-per-user model.
    fun choose(emoji: String) {
        showPicker = false
        val prev = mine
        if (prev == emoji) {
            mine = null
            onRemove(emoji)
        } else {
            mine = emoji
            if (prev != null) onRemove(prev)
            onReact(emoji)
        }
    }

    val emojis = (serverCounts.keys + listOfNotNull(mine)).distinct()

    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emojis.forEach { emoji ->
            val isMine = mine == emoji
            val count = (serverCounts[emoji] ?: 0) + if (isMine && initialMine != emoji) 1 else 0
            ReactionPill(emoji, count, isMine) { choose(emoji) }
        }
        AddPill { showPicker = !showPicker }
        if (showPicker) {
            REACTIONS.distinct().forEach { emoji ->
                ReactionPill(emoji, 0, highlighted = false) { choose(emoji) }
            }
        }
    }
}

@Composable
private fun ReactionPill(emoji: String, count: Int, highlighted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(99.dp))
            .then(if (highlighted) Modifier.background(Clear30Gradients.community) else Modifier.background(Clear30Colors.opacityGray))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SmallText(emoji)
        if (count > 0) {
            // iOS ReactionView renders the count as plain TinyText (full opacity).
            TinyText(
                count.toString(),
                color = if (highlighted) Color.White else Clear30Colors.text,
            )
        }
    }
}

@Composable
private fun AddPill(onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp)).background(Clear30Colors.opacityGray)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        SmallText("+", color = Clear30Colors.text.copy(alpha = 0.5f))
    }
}

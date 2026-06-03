package org.clear30.views.existinguser.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.clear30.data.model.Post
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.createCommunityPost
import org.clear30.data.supabase.getCommunityFeed
import org.clear30.data.supabase.getUserID
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * CommunityTab — ported from the community feed (CommunityFeedViewModel /
 * Support community views). Loads the post feed from Supabase and renders post
 * cards; tapping a post (detail), creating a post, and reactions are layered on
 * with the navigation stack.
 */
@Composable
fun CommunityTab(userInfo: org.clear30.data.model.UserInfo) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<Post>?>(null) }
    var reload by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Post?>(null) }

    LaunchedEffect(reload) {
        posts = SupabaseController.getCommunityFeed().getOrNull() ?: emptyList()
        // Prime the user-directory cache for the visible authors — touch every
        // post id so UserDirectory.flush() picks them up in a single batched
        // query, instead of N separate selects per card.
        posts?.forEach { org.clear30.data.UserDirectory.lookup(it.userId) }
        org.clear30.data.UserDirectory.flush()
    }

    // Drain a deep-link to a specific post (clear30://post/<id>). We wait for
    // the feed to load so we can pick the canonical Post object, but fall back
    // to a stub if the feed lookup misses (the user might have a link to a
    // post they aren't in the cached page for).
    val sub by org.clear30.AppState.pendingSubRoute.collectAsStateWithLifecycle()
    LaunchedEffect(sub, posts) {
        val r = sub as? org.clear30.data.DeepLinkRoute.Post ?: return@LaunchedEffect
        val loaded = posts ?: return@LaunchedEffect  // wait for the feed
        val target = loaded.firstOrNull { it.id == r.id }
            // Fall back to a stub if the post isn't on the cached feed page;
            // the detail view will still render title/body once the lookup
            // network call lands.
            ?: Post(
                id = r.id, userId = "", title = "Post",
                contentType = "text", body = "", createdAt = "",
            )
        detail = target
        org.clear30.AppState.requestSubRoute(null)
    }

    detail?.let { selected ->
        // System back closes the detail instead of exiting the app.
        androidx.activity.compose.BackHandler { detail = null }
        PostDetail(selected, userInfo, onBack = { detail = null })
        return
    }

    androidx.compose.material3.Scaffold(
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = org.clear30.views.theme.Clear30Colors.green,
            ) { androidx.compose.material3.Icon(org.clear30.views.components.sfSymbol("plus"), "New post", tint = androidx.compose.ui.graphics.Color.White) }
        },
    ) { padding ->
        when (val p = posts) {
            null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                item { Heading1("Community", Modifier.padding(vertical = Dimens.headingTopPadding)) }
                if (p.isEmpty()) {
                    item { SmallText("No posts yet — be the first to share.", color = Clear30Colors.text.copy(alpha = 0.5f)) }
                } else {
                    items(p, key = { it.id }) { post ->
                        PostCard(post) {
                            detail = post
                            org.clear30.data.Logger.logEvent(
                                userInfo.loggingID,
                                org.clear30.data.LogEventType.openedCommunityPost,
                                mapOf(org.clear30.data.LogEventExtraDataType.ID to post.id),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePostDialog(
            onDismiss = { showCreate = false },
            onSubmit = { title, body ->
                showCreate = false
                scope.launch {
                    val userId = SupabaseController.getUserID() ?: userInfo.userID
                    org.clear30.data.Logger.logEvent(
                        userInfo.loggingID,
                        org.clear30.data.LogEventType.createdCommunityPost,
                    )
                    SupabaseController.createCommunityPost(
                        org.clear30.data.supabase.CreatePost(
                            p_title = title, p_content_type = "text", p_body = body, p_user_id = userId,
                        ),
                    )
                    reload++
                }
            },
        )
    }
}

@Composable
private fun CreatePostDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("New post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                androidx.compose.material3.OutlinedTextField(title, { title = it }, label = { androidx.compose.material3.Text("Title") }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(body, { body = it }, label = { androidx.compose.material3.Text("Share something...") })
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSubmit(title, body) }, enabled = title.isNotBlank()) { androidx.compose.material3.Text("Post") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") } },
    )
}

@Composable
private fun PostCard(post: Post, onClick: () -> Unit) {
    // Author label sourced from the shared UserDirectory cache (batched
    // public.users lookup). The cache returns a stable "User #abc123" handle
    // on cold-miss so the row never renders blank; the real display name
    // streams in once the feed-wide flush() resolves.
    val directory by org.clear30.data.UserDirectory.cache.collectAsStateWithLifecycle()
    val author = remember(post.userId, directory) {
        org.clear30.data.UserDirectory.lookup(post.userId)
    }
    Clear30Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            TinyText(
                buildString {
                    author.emoji?.let { append(it).append("  ") }
                    append(author.displayName)
                },
                color = Clear30Colors.text.copy(alpha = 0.5f),
            )
            Heading3(post.title)
            SmallText(post.body, maxLines = 4)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                val reactionTotal = post.reactionCounts?.sumOf { it.count } ?: 0
                TinyText("♡ $reactionTotal", color = Clear30Colors.text.copy(alpha = 0.5f))
                TinyText("💬 ${post.totalCommentsCount}", color = Clear30Colors.text.copy(alpha = 0.5f))
            }
        }
    }
}

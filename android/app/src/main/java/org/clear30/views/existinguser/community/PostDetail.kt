package org.clear30.views.existinguser.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.clear30.data.model.Comment
import org.clear30.data.model.Post
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.addComment
import org.clear30.data.supabase.addReaction
import org.clear30.data.supabase.getUserID
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading2
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

private val REACTIONS = listOf("❤️", "🙌", "🔥", "🌱", "👏")

/**
 * PostDetail — ported from the community post-detail view. Shows the full post,
 * reaction row (insert into reactions), comments, and an add-comment field.
 */
@Composable
fun PostDetail(post: Post, userInfo: UserInfo, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val comments = remember { mutableStateListOf<Comment>().apply { addAll(post.comments ?: emptyList()) } }
    var newComment by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2(post.title)
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            item { SmallText(post.body, Modifier.padding(vertical = Dimens.cardSpacing)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                    REACTIONS.forEach { emoji ->
                        Clear30Card(modifier = Modifier.clickableReact {
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
                        }) { SmallText(emoji) }
                    }
                }
            }
            item { TinyText("${comments.size} comments", color = Clear30Colors.text.copy(alpha = 0.5f)) }
            items(comments) { c ->
                Clear30Card(modifier = Modifier.fillMaxWidth()) { SmallText(c.body) }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            OutlinedTextField(newComment, { newComment = it }, modifier = Modifier.weight(1f))
            IconButton("arrow.right") {
                val body = newComment.trim()
                if (body.isEmpty()) return@IconButton
                newComment = ""
                comments.add(Comment(id = "local-${comments.size}", body = body))
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
        }
    }
}

private fun Modifier.clickableReact(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

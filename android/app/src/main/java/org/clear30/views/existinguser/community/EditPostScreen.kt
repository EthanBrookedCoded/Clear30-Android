package org.clear30.views.existinguser.community

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import org.clear30.data.AlertHandler
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.Post
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.editCommunityPost
import org.clear30.data.supabase.editCommunityVideoPost
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3Input
import org.clear30.views.components.IconButton
import org.clear30.views.components.MultiLineOffWhiteInput
import org.clear30.views.components.SmallText
import org.clear30.views.components.VideoThumbnail
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * EditPostScreen — ported from EditPostView.swift. Owners re-title (and, for
 * text posts, re-body) an existing post; tags and video content are fixed.
 * Saves through `community.posts` updates (Swift editPost / editVideoPost) and
 * hands the refresh back to the caller via [onSaved].
 */
@Composable
internal fun EditPostScreen(
    post: Post,
    userInfo: UserInfo,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(post.title) }
    var body by remember { mutableStateOf(post.body) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedEditCommunityPost)
    }

    fun save() {
        val strippedTitle = title.trim()
        val strippedBody = body.trim()
        // iOS handleSave validation copy (ERROR_TITLE_MISSING / _DESCRIPTION_MISSING).
        if (strippedTitle.isEmpty()) {
            AlertHandler.error("Error 😞", "Don't forget to add a title ☺️.")
            return
        }
        if (!post.isVideo && strippedBody.isEmpty()) {
            AlertHandler.error("Error 😞", "Hey, don't forget to add a description ✏️.")
            return
        }
        saving = true
        scope.launch {
            val err = if (post.isVideo) {
                SupabaseController.editCommunityVideoPost(post.id, strippedTitle)
            } else {
                SupabaseController.editCommunityPost(post.id, strippedTitle, strippedBody)
            }
            saving = false
            if (err != null) {
                AlertHandler.error("Error 😞", "Could not save post. ${err.message}")
                return@launch
            }
            Logger.logEvent(
                userInfo.loggingID,
                LogEventType.editedCommunityPost,
                mapOf(
                    LogEventExtraDataType.TITLE to strippedTitle,
                    LogEventExtraDataType.TYPE to if (post.isVideo) "video" else "text",
                ),
            )
            AlertHandler.info("Success 💯", "Post updated!") { onSaved() }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        IconButton("xmark", onClick = onClose)

        // iOS UserNameWithEmojiAndTime — the editing user's emoji + name.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 4),
        ) {
            userInfo.emoji?.let { SmallText(it) }
            SmallText(userInfo.name, color = Clear30Colors.text.copy(alpha = 0.5f))
        }

        Heading3Input(title, { title = it }, placeholder = "Title", modifier = Modifier.fillMaxWidth(), returnNewLine = false)

        // Tags can't change while editing (iOS SimpleNonEditableTagsView).
        val tags = post.postTags.orEmpty().mapNotNull { it.tag }
        if (tags.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                tags.forEach { tag -> TagPill(tag) }
            }
        }

        if (post.isVideo) {
            // The clip itself isn't editable — show its poster like iOS.
            VideoThumbnail(post.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f / 1.5f))
            Box(Modifier.weight(1f))
        } else {
            MultiLineOffWhiteInput(
                body,
                { body = it },
                placeholder = "Description",
                modifier = Modifier.fillMaxWidth().weight(1f),
                returnNewLine = true,
            )
        }

        val valid = title.isNotBlank() && (post.isVideo || body.isNotBlank())
        if (saving) {
            CircularProgressIndicator()
        } else if (valid) {
            DefaultButton("Save Changes  ✓", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) { save() }
        }
    }
}

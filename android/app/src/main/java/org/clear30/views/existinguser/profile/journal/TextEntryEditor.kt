package org.clear30.views.existinguser.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.clear30.views.components.Clear30Alert
import org.clear30.views.components.SmallTextEditor
import org.clear30.views.components.TextIconButton
import org.clear30.views.components.Heading3Input
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * TextEntryEditor — full-screen journal text editor, ported from iOS
 * `TextEntry.swift`: back chevron + trash header, a Heading3 "Title" field, a
 * borderless body editor filling the page, and a community-gradient
 * "Share to Community" button once the entry has both a title and content
 * (hidden again after it's been shared, iOS `communityPostId == nil` check).
 *
 * Like iOS, edits are committed on the way OUT (`onDisappear` →
 * [onClose]) — emptied fields fall back to their initial values so an existing
 * entry can't be blanked by accident.
 */
@Composable
fun TextEntryEditor(
    initialTitle: String,
    initialContent: String,
    alreadyShared: Boolean,
    onDelete: (() -> Unit)? = null,
    onShare: ((title: String, content: String) -> Unit)? = null,
    onClose: (title: String, content: String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }
    var shared by remember { mutableStateOf(alreadyShared) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showShareConfirm by remember { mutableStateOf(false) }

    fun close() {
        // iOS onDisappear: restore emptied fields before committing.
        val outTitle = if (title.isBlank() && initialTitle.isNotBlank()) initialTitle else title
        val outContent = if (content.isBlank() && initialContent.isNotBlank()) initialContent else content
        onClose(outTitle.trim(), outContent.trim())
    }

    BackHandler { close() }

    // iOS focuses the title when empty, else the body when it's empty.
    val titleFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (initialTitle.isEmpty()) titleFocus.requestFocus()
        else if (initialContent.isEmpty()) contentFocus.requestFocus()
    }

    // No statusBarsPadding: every presentation context (JournalSection, the
    // Today feed, MessageDetail) is inline inside AllTabs' Scaffold content,
    // which is already inset below the status bar.
    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Column(
            Modifier.fillMaxSize()
                .padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        ) {
            // Header — back chevron • trash (existing, non-empty entries only).
            Row(
                Modifier.fillMaxWidth().padding(bottom = Dimens.headingTopPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                org.clear30.views.components.IconButton("chevron.backward") { close() }
                Spacer(Modifier.weight(1f))
                if (onDelete != null && title.isNotBlank() && content.isNotBlank()) {
                    Box(
                        Modifier.size(30.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Clear30Colors.opacityGray)
                            .pressScale { showDeleteConfirm = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            sfSymbol("trash"),
                            contentDescription = "Delete",
                            tint = Clear30Colors.text,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Heading3Input(
                title, { title = it },
                placeholder = "Title",
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).padding(bottom = Dimens.cardSpacing / 2),
                returnNewLine = false,
            )

            // Body — the shared borderless editor filling the page
            // (iOS `SmallTextEditor(text:, placeholder: "")`).
            SmallTextEditor(
                content, { content = it },
                placeholder = "",
                modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(contentFocus),
            )

            if (onShare != null && !shared && title.isNotBlank() && content.isNotBlank()) {
                // iOS TextIconButton(text:, imageName: "person.3.fill",
                // gradient: communityGradient).
                TextIconButton(
                    text = "Share to Community",
                    icon = "person.3.fill",
                    gradient = Clear30Gradients.community,
                    modifier = Modifier.padding(bottom = Dimens.cardSpacing),
                ) { showShareConfirm = true }
            }
        }
    }

    if (showDeleteConfirm) {
        Clear30Alert(
            onDismissRequest = { showDeleteConfirm = false },
            title = "Delete",
            message = "Are you sure you want to delete this journal entry?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDeleteConfirm = false; onDelete?.invoke() },
            dismissLabel = "Cancel",
        )
    }

    if (showShareConfirm) {
        Clear30Alert(
            onDismissRequest = { showShareConfirm = false },
            title = "Share to Community",
            message = "Post this journal entry to the community feed?",
            confirmLabel = "Share",
            onConfirm = {
                showShareConfirm = false
                shared = true
                onShare?.invoke(title.trim(), content.trim())
            },
            dismissLabel = "Cancel",
        )
    }
}

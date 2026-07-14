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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.clear30.views.components.GradientActionButton
import org.clear30.views.components.Heading3Input
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Lexend

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

    Box(Modifier.fillMaxSize().background(Clear30Colors.background)) {
        Column(
            Modifier.fillMaxSize()
                .statusBarsPadding()
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

            // Body — borderless small-text editor filling the page (iOS SmallTextEditor).
            BasicTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(contentFocus),
                textStyle = TextStyle(fontFamily = Lexend, fontSize = 15.5.sp, color = Clear30Colors.text),
                cursorBrush = SolidColor(Clear30Colors.text),
            )

            if (onShare != null && !shared && title.isNotBlank() && content.isNotBlank()) {
                GradientActionButton(
                    iconName = "person.3.fill",
                    gradient = Clear30Gradients.community,
                    title = "Share to Community",
                    modifier = Modifier.padding(bottom = Dimens.cardSpacing),
                ) { showShareConfirm = true }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete") },
            text = { Text("Are you sure you want to delete this journal entry?") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete?.invoke() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showShareConfirm) {
        AlertDialog(
            onDismissRequest = { showShareConfirm = false },
            title = { Text("Share to Community") },
            text = { Text("Post this journal entry to the community feed?") },
            confirmButton = {
                TextButton(onClick = {
                    showShareConfirm = false
                    shared = true
                    onShare?.invoke(title.trim(), content.trim())
                }) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { showShareConfirm = false }) { Text("Cancel") } },
        )
    }
}

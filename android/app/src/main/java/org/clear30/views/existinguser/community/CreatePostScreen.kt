package org.clear30.views.existinguser.community

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import org.clear30.data.model.PostTag
import org.clear30.data.model.Program
import org.clear30.util.extractVideoThumbnail
import org.clear30.views.components.Heading3Input
import org.clear30.views.components.SmallTextEditor
import org.clear30.views.components.TextIconButton
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import java.io.File

/**
 * CreatePostScreen — ported from CreatePostView.swift. Title + an inline tag row
 * (the forced program tag + up to two added tags via the picker) + either a
 * Description (text post) or a recorded clip (video post). Tapping the video
 * badge records via the system camera; on success the description is replaced by
 * the captured thumbnail and posting routes through [onSubmitVideo] (which
 * uploads the clip). The gradient Post button appears once the post is valid.
 */
@Composable
internal fun CreatePostScreen(
    program: Program,
    allTags: List<PostTag.Tag>,
    onClose: () -> Unit,
    onSubmit: (title: String, body: String, tagNames: List<String>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    // iOS CreatePostView.setup(): the current "Day N" tag is auto-attached as a
    // *selected* tag — visible in the tag row and removable — never during
    // start-soon (getDayTag returns null then).
    var selectedTagIds by remember { mutableStateOf(setOfNotNull(allTags.getDayTag(program)?.id)) }
    var showTagPicker by remember { mutableStateOf(false) }
    // iOS forces the community program tag (getProgramTag → communityProgramTagName),
    // which is "Clear30"/"Clear30 Preparation" during a break — not coreProgramName.
    val forcedTag = program.communityProgramTagName

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton("xmark", onClick = onClose)
        }

        // iOS CreatePostView: Heading3Input(placeholder: "Title", returnNewLine: false).
        Heading3Input(title, { title = it }, placeholder = "Title", modifier = Modifier.fillMaxWidth(), returnNewLine = false)

        // Tag row: forced program tag + any added tags + a "+" to open the picker.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SolidTagPill(forcedTag, Clear30Colors.green, removable = false) {}
            selectedTagIds.forEach { id ->
                allTags.firstOrNull { it.id == id }?.let { tag ->
                    SolidTagPill(tag.name, tagAccent(tag), removable = true) { selectedTagIds = selectedTagIds - id }
                }
            }
            if (selectedTagIds.size < 2) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Clear30Colors.opacityGray).pressScale { showTagPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sfSymbol("plus"), contentDescription = "Add tag", tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }

        // iOS CreatePostView: SmallTextEditor(placeholder: "Description") — a
        // borderless Lexend editor filling the page, NOT a Material outlined box.
        SmallTextEditor(
            body, { body = it },
            placeholder = "Description",
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        val canPost = title.isNotBlank() && body.isNotBlank()
        if (canPost) {
            // iOS TextIconButton(text: "Post", gradient: clear30Gradient).
            TextIconButton(
                text = "Post",
                gradient = Clear30Gradients.clear30,
                modifier = Modifier.padding(bottom = Dimens.headingTopPadding),
            ) {
                val names = listOf(forcedTag) + selectedTagIds.mapNotNull { id -> allTags.firstOrNull { it.id == id }?.name }
                onSubmit(title.trim(), body.trim(), names)
            }
        }
    }

    if (showTagPicker) {
        TagFilterSheet(
            tags = allTags,
            selected = selectedTagIds,
            onToggle = { id -> selectedTagIds = if (id in selectedTagIds) selectedTagIds - id else selectedTagIds + id },
            onDone = { showTagPicker = false },
            onDismiss = { showTagPicker = false },
        )
    }
}

/** Solid colored tag pill (selected/forced state) with an optional remove "×". */
@Composable
private fun SolidTagPill(name: String, accent: Color, removable: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(accent).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SmallText(name, color = Color.White)
        if (removable) {
            Icon(
                sfSymbol("xmark"),
                contentDescription = "Remove",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(12.dp).clickable(onClick = onRemove),
            )
        }
    }
}

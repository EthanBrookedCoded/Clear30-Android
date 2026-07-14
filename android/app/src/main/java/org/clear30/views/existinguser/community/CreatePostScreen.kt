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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading3Input
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
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
    onSubmitVideo: (title: String, tagNames: List<String>, video: File, thumbnail: Bitmap?) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedTagIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTagPicker by remember { mutableStateOf(false) }
    // iOS forces the community program tag (getProgramTag → communityProgramTagName),
    // which is "Clear30"/"Clear30 Preparation" during a break — not coreProgramName.
    val forcedTag = program.communityProgramTagName

    // Recording state — same system-camera pipeline as JournalSection: allocate a
    // destination file under filesDir/videos (FileProvider-mapped), capture into
    // it, then grab a poster frame for the preview/upload.
    var recordedVideo by remember { mutableStateOf<File?>(null) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var pendingVideoId by remember { mutableStateOf<String?>(null) }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        val id = pendingVideoId
        pendingVideoId = null
        val file = id?.let { communityVideoFile(context, it) }
        if (saved == true && file != null && file.exists() && file.length() > 0) {
            recordedVideo = file
            thumbnail = extractVideoThumbnail(context, android.net.Uri.fromFile(file))
        } else {
            file?.delete()
        }
    }

    fun startRecording() {
        val id = "community-${System.currentTimeMillis()}"
        pendingVideoId = id
        val dest = communityVideoFile(context, id)
        dest.parentFile?.mkdirs()
        dest.createNewFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        videoLauncher.launch(uri)
    }

    val hasVideo = recordedVideo != null

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton("xmark", onClick = onClose)
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (hasVideo) Clear30Colors.green else Clear30Colors.opacityGray)
                    .clickable { startRecording() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    sfSymbol("video.fill"),
                    contentDescription = "Record video",
                    tint = if (hasVideo) Color.White else Clear30Colors.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
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
                    Modifier.size(32.dp).clip(CircleShape).background(Clear30Colors.opacityGray).clickable { showTagPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sfSymbol("plus"), contentDescription = "Add tag", tint = Clear30Colors.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }

        // Video posts show the captured frame (tap to re-record); text posts show
        // the description editor.
        if (hasVideo) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Dimens.cornerRadius))
                    .background(Clear30Colors.opacityGray).clickable { startRecording() },
                contentAlignment = Alignment.Center,
            ) {
                thumbnail?.let {
                    Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(14.dp)) {
                    Icon(sfSymbol("play.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            SmallText("Tap the clip to re-record.", color = Clear30Colors.text.copy(alpha = 0.5f))
            Box(Modifier.weight(1f))
        } else {
            OutlinedTextField(body, { body = it }, placeholder = { Text("Description") }, modifier = Modifier.fillMaxWidth().weight(1f))
        }

        val canPost = title.isNotBlank() && (hasVideo || body.isNotBlank())
        if (canPost) {
            DefaultButton("Post", gradient = Clear30Gradients.clear30, modifier = Modifier.fillMaxWidth()) {
                val names = listOf(forcedTag) + selectedTagIds.mapNotNull { id -> allTags.firstOrNull { it.id == id }?.name }
                val video = recordedVideo
                if (video != null) onSubmitVideo(title.trim(), names, video, thumbnail)
                else onSubmit(title.trim(), body.trim(), names)
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

/** Temp destination for a community recording (FileProvider-mapped `videos/` dir). */
private fun communityVideoFile(context: android.content.Context, id: String): File =
    File(File(context.filesDir, "videos"), "$id.mp4")

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
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(12.dp).clickable(onClick = onRemove),
            )
        }
    }
}

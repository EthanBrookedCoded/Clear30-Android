package org.clear30.views.existinguser.support

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramResource
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading2
import org.clear30.views.components.IconButton
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** Which resource shelf to render — Reddit threads or YouTube videos. */
internal enum class ResourceKind(val title: String, val emptyMsg: String, val tint: Color) {
    REDDIT(
        title = "Reddit stories",
        emptyMsg = "Reddit stories will appear here as your content unlocks.",
        tint = Clear30Colors.reddit1,
    ),
    YOUTUBE(
        title = "YouTube",
        emptyMsg = "YouTube videos will appear here as your content unlocks.",
        tint = Clear30Colors.youTube1,
    );
}

/**
 * ResourcesScreen — ported (in spirit) from Support/Library/Reddit + YouTube.
 * Pulls every matching `ProgramResource` from the user's content graph and
 * opens each in the system browser / Reddit app / YouTube app via the standard
 * VIEW intent. The full in-app webview viewer was an iOS WKWebView UX choice
 * that's better handled by Android's intent-resolution.
 */
@Composable
internal fun ResourcesScreen(program: Program, kind: ResourceKind, onBack: () -> Unit) {
    val context = LocalContext.current

    val resources: List<ProgramResource> = remember(program.contentInfo.size, kind) {
        program.contentInfo.values
            .flatMap { it.messages }
            .flatMap { it.allResources }
            .filter { if (kind == ResourceKind.REDDIT) it.isReddit else it.isYouTube }
            .distinctBy { it.url }
    }
    val brandGradient = if (kind == ResourceKind.REDDIT) Clear30Gradients.reddit else Clear30Gradients.youtube
    val iconName = if (kind == ResourceKind.REDDIT) "person.3" else "play.fill"

    var search by remember { mutableStateOf("") }
    val visible = remember(resources, search) {
        if (search.isBlank()) resources
        else resources.filter { it.title.contains(search, ignoreCase = true) || it.url.contains(search, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading2(kind.title)
        }

        if (resources.isNotEmpty()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search ${kind.title.lowercase()}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing),
            )
        }

        if (visible.isEmpty()) {
            Clear30Card(modifier = Modifier.fillMaxWidth().padding(top = Dimens.cardSpacing)) {
                SmallText(
                    if (search.isBlank()) kind.emptyMsg else "No matches for \"$search\".",
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(top = Dimens.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
        ) {
            items(visible, key = { it.url }) { res ->
                Clear30Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        // System intent — resolves to the official app if installed, browser otherwise.
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(res.url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    gradient = brandGradient,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                        Icon(sfSymbol(iconName), contentDescription = null, tint = Color.White)
                        Column {
                            SmallText(res.title, color = Color.White)
                            TinyText(res.url, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(sfSymbol("arrow.forward"), contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}

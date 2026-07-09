package org.clear30.views.existinguser.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.data.model.Post
import org.clear30.data.model.PostTag
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getCommunityFeed
import org.clear30.views.components.DefaultButton
import org.clear30.views.components.Heading1
import org.clear30.views.components.Heading3
import org.clear30.views.components.IconButton
import org.clear30.views.components.PillPicker
import org.clear30.views.components.PillPickerStyle
import org.clear30.views.components.SmallText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/** Feed sort (iOS `SortMode`): Newest -> recent, Top -> engagement. */
internal enum class SortMode(val label: String, val rpc: String) {
    NEWEST("Newest", "recent"),
    TOP("Top", "engagement"),
}

/** Time window for "Top" (iOS `DateRange`); null = all time. */
internal enum class DateRange(val label: String, val days: Int?) {
    WEEK("7d", 7), MONTH("30d", 30), THREE("90d", 90), ALL("All", null),
}

/** Community header — title, the filter/bell/+ icons, and the sort + time toggles. */
@Composable
internal fun CommunityHeader(
    sortMode: SortMode,
    onSort: (SortMode) -> Unit,
    range: DateRange,
    onRange: (DateRange) -> Unit,
    unreadActivity: Boolean,
    onFilter: () -> Unit,
    onActivity: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        Modifier.padding(top = Dimens.headingTopPadding, bottom = Dimens.cardSpacing / 2),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Heading1("Community")
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                CircleIconBtn("line.3.horizontal.decrease", onFilter)
                Box {
                    CircleIconBtn("bell.fill", onActivity)
                    // Unread-activity dot (iOS CommunityFeed): 8pt red circle
                    // tucked into the bell's top-trailing corner.
                    if (unreadActivity) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-3).dp, y = 3.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Clear30Colors.red1),
                        )
                    }
                }
                CircleIconBtn("plus", onCreate)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            PillPicker(sortMode, SortMode.values().toList(), { it.label }, onSort, style = PillPickerStyle.Primary())
            if (sortMode == SortMode.TOP) {
                PillPicker(range, DateRange.values().toList(), { it.label }, onRange, style = PillPickerStyle.Secondary)
            }
        }
    }
}

@Composable
private fun CircleIconBtn(icon: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Clear30Colors.opacityGray).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(sfSymbol(icon), contentDescription = icon, tint = Clear30Colors.text, modifier = Modifier.size(18.dp))
    }
}

/** Filter-by-Tag bottom sheet — gray pills, colored gradient when selected. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TagFilterSheet(
    tags: List<PostTag.Tag>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.horizontalPadding).padding(bottom = Dimens.headingTopPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
        ) {
            Heading3("Filter by Tag")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
            ) {
                tags.forEach { tag -> FilterTagPill(tag, selected = tag.id in selected) { onToggle(tag.id) } }
            }
            DefaultButton("Done  ✓", gradient = null, modifier = Modifier.fillMaxWidth()) { onDone() }
        }
    }
}

@Composable
private fun FilterTagPill(tag: PostTag.Tag, selected: Boolean, onClick: () -> Unit) {
    val accent = tagAccent(tag)
    Box(
        Modifier.clip(RoundedCornerShape(99.dp))
            .then(if (selected) Modifier.background(accent) else Modifier.background(Clear30Colors.opacityGray))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        SmallText(tag.name, color = if (selected) Color.White else Clear30Colors.text.copy(alpha = 0.6f))
    }
}

/** Activity — iOS notifications / my-posts. Notifications is a stub for now. */
internal enum class ActivityMode(val label: String) { NOTIFICATIONS("Notifications"), MY_POSTS("My Posts") }

@Composable
internal fun ActivityScreen(userInfo: UserInfo, onBack: () -> Unit, onOpenPost: (Post) -> Unit) {
    var mode by remember { mutableStateOf(ActivityMode.MY_POSTS) }
    var myPosts by remember { mutableStateOf<List<Post>?>(null) }

    LaunchedEffect(mode) {
        if (mode == ActivityMode.MY_POSTS && myPosts == null) {
            val loaded = SupabaseController.getCommunityFeed(onlyMyPosts = true).getOrNull() ?: emptyList()
            loaded.forEach { org.clear30.data.UserDirectory.lookup(it.userId) }
            org.clear30.data.UserDirectory.flush()
            myPosts = loaded
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading3("Activity", color = Clear30Colors.text.copy(alpha = 0.5f))
        }
        PillPicker(mode, ActivityMode.values().toList(), { it.label }, { mode = it }, style = PillPickerStyle.Primary(Clear30Gradients.community))

        when (mode) {
            ActivityMode.NOTIFICATIONS ->
                SmallText("No activity, yet…", color = Clear30Colors.text.copy(alpha = 0.5f))
            ActivityMode.MY_POSTS -> {
                val mine = myPosts
                if (mine == null) {
                    androidx.compose.material3.CircularProgressIndicator()
                } else if (mine.isEmpty()) {
                    SmallText("No posts, yet…", color = Clear30Colors.text.copy(alpha = 0.5f))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                        items(mine, key = { it.id }) { post -> PostCard(post) { onOpenPost(post) } }
                    }
                }
            }
        }
    }
}

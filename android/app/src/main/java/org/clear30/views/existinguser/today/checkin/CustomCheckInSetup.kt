package org.clear30.views.existinguser.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.launch
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventExtraDataType
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.CustomCheckIn
import org.clear30.data.model.CustomCheckInOption
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading1
import org.clear30.views.components.HuePicker
import org.clear30.views.components.IconButton
import org.clear30.views.components.OffWhiteInput
import org.clear30.views.components.SmallText
import org.clear30.views.components.TinyText
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * Custom check-in list + editor — rebuilt to iOS `CustomCheckInEdit.swift` /
 * `CustomCheckInSetup.swift` (W9). The list shows the fixed default weed
 * check-in, each custom one as a pill with edit / delete controls, and an add
 * button. The editor is the iOS two-phase form: activity input with template
 * chips, then the slider preview whose two 40dp corner buttons are EMOJI
 * pickers (left = didn't-do 🚫, right = did ✅) over editable labels, plus the
 * one real color control — the HuePicker (sat 0.6 / bri 0.9, gradient
 * hue → hue+0.07). iOS's phase-3 "try it out" demo is not ported (noted).
 */
@Composable
fun CustomCheckInSetup(
    program: Program,
    userInfo: UserInfo,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CustomCheckIn?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CustomCheckIn?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") rev

    fun persist() {
        scope.launch { Clear30Store.save(program) }
        rev++
    }

    if (creating || editing != null) {
        CustomCheckInEditor(
            existing = editing,
            onCancel = { creating = false; editing = null },
            onSave = { saved ->
                val idx = program.customCheckIns.indexOfFirst { it.id == saved.id }
                if (idx >= 0) program.customCheckIns[idx] = saved else program.customCheckIns.add(saved)
                Logger.logEvent(
                    userInfo.loggingID,
                    LogEventType.openedCustomCheckIn,
                    mapOf(LogEventExtraDataType.CUSTOM_CHECK_IN_ID to saved.id),
                )
                persist()
                creating = false
                editing = null
            },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onBack)
            Heading1("Check Ins")
        }

        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                // Fixed default weed check-in — not editable or removable (iOS
                // CustomCheckInEdit.swift:65-69).
                OptionPill("🤩", "Didn't smoke", Clear30Gradients.clear30, dimmed = true)

                program.customCheckIns.forEach { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OptionPill(c.completeOption.emoji, c.completeOption.name, c.gradient, dimmed = true)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            sfSymbol("slider.horizontal.3"),
                            contentDescription = "Edit",
                            tint = Clear30Colors.text,
                            modifier = Modifier.size(18.dp).clickable { editing = c },
                        )
                        Spacer(Modifier.width(Dimens.cardSpacing))
                        Icon(
                            sfSymbol("minus.circle"),
                            contentDescription = "Delete",
                            tint = Clear30Colors.red2,
                            modifier = Modifier.size(20.dp).clickable { deleting = c },
                        )
                    }
                }

                // Add — iOS TextIconButton("Custom check in", plus.circle.fill).
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(Dimens.cornerRadius))
                        .background(Clear30Gradients.clear30)
                        .pressScale { creating = true }
                        .padding(vertical = Dimens.cardSpacing * 0.75f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(sfSymbol("plus.circle.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Dimens.cardSpacing / 2))
                    SmallText("Custom check in", color = Color.White)
                }
            }
        }
    }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${c.completeOption.emoji} ${c.name}?") },
            text = { Text("This will also delete all of its check-in data.") },
            confirmButton = {
                TextButton(onClick = {
                    program.customCheckIns.removeAll { it.id == c.id }
                    persist()
                    deleting = null
                }) { Text("Yes", color = Clear30Colors.red2) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("No") } },
        )
    }
}

/** Emoji + name pill (iOS `CheckInStatus` look). */
@Composable
private fun OptionPill(emoji: String, name: String, gradient: Brush, dimmed: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(99.dp))
            .background(gradient)
            .padding(horizontal = Dimens.cardSpacing, vertical = Dimens.cardSpacing / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
    ) {
        SmallText(emoji)
        SmallText(name, color = if (dimmed) Color.White.copy(alpha = 0.5f) else Color.White)
    }
}

/** iOS `CustomCheckInSetup` templates (hardcoded, all hue 0.55). */
private data class CheckInTemplate(val emoji: String, val name: String, val notEmoji: String = "🚫")
private val TEMPLATES = listOf(
    CheckInTemplate("🏃‍♂️", "Run"),
    CheckInTemplate("🧘‍♀️", "Meditate"),
    CheckInTemplate("🎨", "Draw"),
    CheckInTemplate("📖", "Read"),
    CheckInTemplate("💪", "Exercise"),
    CheckInTemplate("🍳", "Cook"),
)

@Composable
private fun CustomCheckInEditor(
    existing: CustomCheckIn?,
    onCancel: () -> Unit,
    onSave: (CustomCheckIn) -> Unit,
) {
    var activity by remember { mutableStateOf(existing?.name ?: "") }
    var notEmoji by remember { mutableStateOf(existing?.incompleteOption?.emoji ?: "🚫") }
    var didEmoji by remember { mutableStateOf(existing?.completeOption?.emoji ?: "✅") }
    var notName by remember { mutableStateOf(existing?.incompleteOption?.name ?: "") }
    var didName by remember { mutableStateOf(existing?.completeOption?.name ?: "") }
    var hue by remember { mutableStateOf((existing?.hue ?: 0.55).toFloat()) }
    // Which emoji the picker dialog is editing: 0 = none, 1 = left, 2 = right.
    var pickingEmoji by remember { mutableIntStateOf(0) }

    // iOS: leaving the activity field auto-fills the two labels.
    fun autofill() {
        if (activity.isNotBlank()) {
            if (notName.isBlank()) notName = "Didn't ${activity.trim().lowercase()}"
            if (didName.isBlank()) didName = activity.trim()
        }
    }

    val sliderGradient = Brush.linearGradient(
        listOf(
            Color.hsv((hue * 360f) % 360f, 0.6f, 0.9f),
            Color.hsv(((hue + 0.07f) * 360f) % 360f, 0.6f, 0.9f),
        ),
    )
    val valid = activity.isNotBlank() && notName.isNotBlank() && didName.isNotBlank()

    Column(
        Modifier.fillMaxSize().padding(horizontal = Dimens.horizontalPadding, vertical = Dimens.headingTopPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
            IconButton("chevron.backward", onClick = onCancel)
            Heading1(if (existing == null) "New Check In" else "Edit Check In")
        }

        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing)) {
                // Phase 1 — activity (iOS activitySelection).
                SmallText("I want to...", color = Clear30Colors.text.copy(alpha = 0.5f))
                OffWhiteInput(
                    value = activity,
                    onValueChange = { activity = it },
                    placeholder = "Habit (Run, Draw, ...)",
                    // iOS autofills the two labels on LEAVING this field — never
                    // during composition (that refilled a cleared label under
                    // the user's cursor).
                    modifier = Modifier.onFocusChanged { state -> if (!state.isFocused) autofill() },
                )
                // Debounced fallback for the keep-focus-and-tap-Save path.
                LaunchedEffect(activity) {
                    if (activity.isNotBlank()) {
                        kotlinx.coroutines.delay(800)
                        autofill()
                    }
                }
                if (activity.isBlank()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    ) {
                        TEMPLATES.forEach { t ->
                            TinyText(
                                "${t.emoji} ${t.name}",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(Clear30Colors.opacityGray)
                                    .clickable {
                                        activity = t.name
                                        didEmoji = t.emoji
                                        notEmoji = t.notEmoji
                                        hue = 0.55f
                                        notName = "Didn't ${t.name.lowercase()}"
                                        didName = t.name
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                // Phase 2 — slider customization (iOS sliderCustomization).
                if (activity.isNotBlank()) {
                    SmallText("Customize your slider", color = Clear30Colors.text.copy(alpha = 0.5f))

                    // Slider preview: gray track, hue-gradient thumb, and the two
                    // 40dp EMOJI buttons on the corners (iOS sliderTemplate).
                    Box(
                        Modifier.fillMaxWidth().height(65.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Clear30Colors.opacityGray),
                    ) {
                        Box(
                            Modifier.align(Alignment.Center).size(55.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(sliderGradient),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                sfSymbol("arrow.left.and.right"),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        EmojiCornerButton(notEmoji, Modifier.align(Alignment.CenterStart).padding(start = 8.dp)) { pickingEmoji = 1 }
                        EmojiCornerButton(didEmoji, Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) { pickingEmoji = 2 }
                    }

                    // Editable option labels under each side (iOS TinyTextInput).
                    Row(Modifier.fillMaxWidth()) {
                        OffWhiteInput(
                            value = notName,
                            onValueChange = { notName = it },
                            placeholder = "Didn't ...",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(Dimens.cardSpacing / 2))
                        OffWhiteInput(
                            value = didName,
                            onValueChange = { didName = it },
                            placeholder = "Did it",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // The ONE color control: the hue slider (iOS HuePicker).
                    HuePicker(hue = hue, onHueChange = { hue = it }, modifier = Modifier.fillMaxWidth())

                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(Dimens.cornerRadius))
                            .background(if (valid) sliderGradient else androidx.compose.ui.graphics.SolidColor(Clear30Colors.opacityGray))
                            .pressScale {
                                if (valid) {
                                    onSave(
                                        CustomCheckIn(
                                            // iOS id: "<name lowercased, spaces→'-'>-<8-char uuid>".
                                            id = existing?.id
                                                ?: "${activity.trim().lowercase().replace(' ', '-')}-${UUID.randomUUID().toString().take(8)}",
                                            name = activity.trim(),
                                            incompleteOption = CustomCheckInOption(emoji = notEmoji, name = notName.trim()),
                                            completeOption = CustomCheckInOption(emoji = didEmoji, name = didName.trim()),
                                            hue = hue.toDouble(),
                                        ),
                                    )
                                }
                            }
                            .padding(vertical = Dimens.cardSpacing * 0.75f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SmallText("Save", color = if (valid) Color.White else Clear30Colors.text.copy(alpha = 0.5f))
                        Spacer(Modifier.width(Dimens.cardSpacing / 2))
                        Icon(
                            sfSymbol("checkmark"),
                            contentDescription = null,
                            tint = if (valid) Color.White else Clear30Colors.text.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }

    if (pickingEmoji != 0) {
        EmojiPickDialog(
            onPick = { e ->
                if (pickingEmoji == 1) notEmoji = e else didEmoji = e
                pickingEmoji = 0
            },
            onDismiss = { pickingEmoji = 0 },
        )
    }
}

/** 40dp rounded emoji button on the slider preview (iOS 40×40, radius 12). */
@Composable
private fun EmojiCornerButton(emoji: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Clear30Colors.background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        SmallText(emoji, textAlign = TextAlign.Center)
    }
}

private val PICKER_EMOJIS = listOf(
    "🚫", "✅", "💨", "🤩", "🏃‍♂️", "🧘‍♀️", "🎨", "📖", "💪", "🍳", "🚭", "🍵",
    "☕", "🍺", "🥤", "💧", "😴", "🌅", "📵", "📱", "🎮", "🛌", "🚿", "🦷",
    "🧹", "📝", "🎯", "🌿", "🍎", "🥗", "🚲", "🏋️", "🥊", "⛹️", "🎸", "🎹",
    "👍", "👎", "🙏", "❤️", "🔥", "⭐", "🌙", "☀️", "😁", "😌", "😬", "😮‍💨",
)

/** Simple emoji grid picker (iOS presents its EmojiPicker sheet here). */
@Composable
private fun EmojiPickDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick an emoji") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth().height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(PICKER_EMOJIS) { e ->
                    Box(
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(e) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

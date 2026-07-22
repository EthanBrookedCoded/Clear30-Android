package org.clear30.views.existinguser.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.clear30.data.AlertHandler
import org.clear30.data.Clear30Store
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.SupabaseUserProps
import org.clear30.data.supabase.updateUser
import org.clear30.views.components.Clear30CardDialog
import org.clear30.views.components.Heading2
import org.clear30.views.components.SmallText
import org.clear30.views.components.pressScale
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Dimens

/**
 * UserEmojiPickerButton — ported from UserEmojiPicker.swift. The user's emoji
 * in an `opacityGray` circle; tapping opens a picker grid, and the choice is
 * patched to `users.emoji` (optimistically, with an alert on failure).
 *
 * iOS presents its full searchable CustomEmojiPickerView; that whole emoji
 * keyboard isn't ported, so this shows a curated grid in a dialog instead.
 */
@Composable
fun UserEmojiPickerButton(userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf(userInfo.emoji ?: "😁") }

    Box(
        Modifier.clip(RoundedCornerShape(percent = 50))
            .background(Clear30Colors.opacityGray)
            .pressScale { showPicker = true }
            .padding(7.5.dp),
        contentAlignment = Alignment.Center,
    ) {
        SmallText(emoji)
    }

    if (showPicker) {
        Clear30CardDialog(onDismiss = { showPicker = false }) {
            Column(
                Modifier.fillMaxWidth().padding(Dimens.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing),
            ) {
                SmallText("Pick your emoji", color = Clear30Colors.text.copy(alpha = 0.5f))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2),
                ) {
                    items(USER_EMOJIS) { candidate ->
                        Box(
                            Modifier.clip(RoundedCornerShape(Dimens.cornerRadius / 2))
                                .background(
                                    if (candidate == emoji) Clear30Colors.opacityGray else Clear30Colors.button,
                                )
                                .pressScale {
                                    showPicker = false
                                    emoji = candidate
                                    userInfo.emoji = candidate
                                    scope.launch {
                                        Clear30Store.save(userInfo)
                                        val error = SupabaseController.updateUser(
                                            JsonObject(mapOf(SupabaseUserProps.EMOJI to JsonPrimitive(candidate))),
                                        )
                                        if (error != null) {
                                            AlertHandler.show(
                                                AlertHandler.Alert(
                                                    title = "Could not update emoji",
                                                    message = error.message,
                                                ),
                                            )
                                        }
                                    }
                                }
                                .padding(Dimens.cardSpacing / 2),
                            contentAlignment = Alignment.Center,
                        ) {
                            Heading2(candidate)
                        }
                    }
                }
            }
        }
    }
}

/** Curated set — the faces/characters the iOS picker's "Smileys" page leads with. */
private val USER_EMOJIS = listOf(
    "😁", "😀", "😄", "😊", "🙂", "😌",
    "😎", "🤩", "🥳", "😇", "🤗", "😏",
    "🙃", "😉", "🤓", "🧐", "🤠", "🥸",
    "💪", "🧠", "🌱", "🌿", "🍀", "🌞",
    "🌈", "⭐", "🔥", "💚", "💙", "🫶",
    "🐢", "🐸", "🦁", "🐼", "🦊", "🐙",
    "🏃", "🧘", "🏋️", "🚴", "🏄", "⛰️",
    "🎯", "🏆", "🎸", "🎨", "📚", "☕",
)

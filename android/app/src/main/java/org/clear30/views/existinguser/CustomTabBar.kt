package org.clear30.views.existinguser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import org.clear30.views.components.MiniText
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors
import java.util.Calendar

/**
 * Time-of-day icon for the Today tab — ported from `TodayTabIcon` in
 * CustomTabBar.swift. The icon shifts as the day progresses so the tab feels
 * alive without the user having to think about it.
 */
private enum class TodayTabIcon(val symbol: String, val iconSize: Float, val selectedYOffset: Float) {
    MORNING("sunrise", 25f, -0.5f),    // 5–10
    NOON("sun.max", 26f, 0f),          // 10–15
    AFTERNOON("sunset", 25f, -0.4f),   // 15–18
    EVENING("moonrise", 26f, -1f),     // 18–21
    NIGHT("moon", 27f, 0f);            // 21–5

    fun yOffset(selected: Boolean): Float = if (selected) selectedYOffset else 0f

    companion object {
        fun now(): TodayTabIcon {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..9 -> MORNING
                in 10..14 -> NOON
                in 15..17 -> AFTERNOON
                in 18..20 -> EVENING
                else -> NIGHT
            }
        }
    }
}

/**
 * CustomTabBarItem — ported 1:1 from CustomTabBar.swift. Order, labels, and
 * symbols match the iOS app exactly. Note: Profile is labeled **"Progress"**
 * (the iOS app reframed it as a progress tracker even though the route maps
 * to ProfileTab on Android).
 */
enum class CustomTabBarItem(val label: String, private val baseSymbol: () -> String, val hasFillVariant: Boolean) {
    SUPPORT("Support", { "hand.thumbsup" }, true),
    COMMUNITY("Community", { "person.3" }, true),
    TODAY("Today", { TodayTabIcon.now().symbol }, true),
    GROUPS("Groups", { "person.crop.rectangle.stack" }, true),
    PROFILE("Progress", { "chart.bar" }, true);

    fun iconName(selected: Boolean): String {
        val sym = baseSymbol()
        return if (selected && hasFillVariant) "$sym.fill" else sym
    }

    /** Icon point size, matching iOS `fontSize(selected:)`. Profile is the largest. */
    fun iconSize(selected: Boolean): Float = when (this) {
        SUPPORT -> if (selected) 26.5f else 25f
        COMMUNITY -> 26f
        TODAY -> TodayTabIcon.now().iconSize
        GROUPS -> 26f
        PROFILE -> 29f
    }

    /** Vertical nudge for the selected icon — only Today shifts, matching iOS `yOffset`. */
    fun yOffset(selected: Boolean): Float = when (this) {
        TODAY -> TodayTabIcon.now().yOffset(selected)
        else -> 0f
    }

    companion object {
        val barItems = listOf(SUPPORT, COMMUNITY, TODAY, GROUPS, PROFILE)
    }
}

/**
 * Bottom navigation bar — faithful port of the iOS CustomTabBar.
 *
 * Visual rules (from CustomTabBar.swift):
 *   • Selected tab: icon at ~0.8 opacity (rendered 0.75 to honor the project
 *     alpha rule — visually identical), label at 0.25, container scale 1.0
 *   • Unselected:    icon at opacity 0.5, label at 0.25, container scale 0.9
 *   • Per-tab icon sizes match iOS `fontSize(selected:)` (Profile largest at 29,
 *     Support grows 25→26.5 when selected, Today varies by time of day).
 *   • No brand pill / no background highlight — the iOS tab bar is a clean
 *     icon-and-label affordance; the pill we shipped earlier was wrong.
 *   • Bottom inset matches the navigation bar so the tap targets sit clear
 *     of the gesture handle.
 */
@Composable
fun CustomTabBar(
    selected: CustomTabBarItem,
    badges: Map<CustomTabBarItem, Int> = emptyMap(),
    onSelect: (CustomTabBarItem) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Clear30Colors.button)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CustomTabBarItem.barItems.forEach { item ->
            TabSlot(
                item = item,
                selected = item == selected,
                badged = (badges[item] ?: 0) > 0,
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabSlot(
    item: CustomTabBarItem,
    selected: Boolean,
    badged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Scale: 1.0 selected, 0.9 unselected — animated through the standard
    // Compose spring so the tap feels weighted.
    val scale by animateFloatAsState(if (selected) 1f else 0.9f, label = "tabScale")
    Column(
        modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(35.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = sfSymbol(item.iconName(selected)),
                contentDescription = item.label,
                tint = Clear30Colors.text.copy(alpha = if (selected) 0.75f else 0.5f),
                modifier = Modifier
                    .size(item.iconSize(selected).dp)
                    .offset(y = item.yOffset(selected).dp),
            )
            // Unread dot — bottom-right of the icon, only when badged. Kept
            // tiny so it reads as a presence indicator, not a label.
            if (badged) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(6.dp)
                        .background(Clear30Colors.green, shape = androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
        MiniText(
            text = item.label,
            color = Clear30Colors.text.copy(alpha = 0.25f),
        )
    }
}

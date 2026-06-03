package org.clear30.views.existinguser

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.clear30.views.components.sfSymbol
import org.clear30.views.theme.Clear30Colors

/**
 * CustomTabBarItem — ported from CustomTabBar.swift. The bottom-bar tabs, in
 * display order (support, community, today, groups, profile). `lobby` is a
 * contextual destination, not a bar tab.
 */
enum class CustomTabBarItem(val sfSymbol: String, val label: String) {
    SUPPORT("hand.thumbsup", "Support"),
    COMMUNITY("person.3", "Community"),
    TODAY("house.fill", "Today"),
    GROUPS("person.crop.rectangle.stack", "Groups"),
    PROFILE("chart.bar", "Profile");

    fun iconName(selected: Boolean): String = if (selected) "$sfSymbol.fill" else sfSymbol

    companion object {
        val barItems = listOf(SUPPORT, COMMUNITY, TODAY, GROUPS, PROFILE)
    }
}

/**
 * Bottom navigation bar — ported from CustomTabBar.swift. Renders the brand
 * gradient tint on the selected item and per-tab unread badges.
 */
@Composable
fun CustomTabBar(
    selected: CustomTabBarItem,
    badges: Map<CustomTabBarItem, Int> = emptyMap(),
    onSelect: (CustomTabBarItem) -> Unit,
) {
    NavigationBar(containerColor = Clear30Colors.button) {
        CustomTabBarItem.barItems.forEach { item ->
            val isSelected = item == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(item) },
                icon = {
                    val badge = badges[item] ?: 0
                    BadgedBox(badge = { if (badge > 0) Badge { } }) {
                        Icon(
                            sfSymbol(item.iconName(isSelected)),
                            contentDescription = item.label,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Clear30Colors.green,
                    unselectedIconColor = Clear30Colors.gray,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

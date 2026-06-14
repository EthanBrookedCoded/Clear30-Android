package org.clear30.views.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps iOS SF Symbol names (used as `icon: String` throughout the app) to the
 * closest Material icon. Expanded as more symbols are referenced; unknown names
 * fall back to a filled circle.
 */
fun sfSymbol(name: String): ImageVector = when (name) {
    "chevron.backward", "chevron.left", "arrow.left" -> Icons.Rounded.ChevronLeft
    "chevron.forward", "chevron.right" -> Icons.Rounded.ChevronRight
    "arrow.left", "arrow.backward" -> Icons.AutoMirrored.Rounded.ArrowBack
    "arrow.right", "arrow.forward" -> Icons.AutoMirrored.Rounded.ArrowForward
    "xmark", "xmark.circle", "xmark.circle.fill" -> Icons.Rounded.Close
    "plus" -> Icons.Rounded.Add
    "plus.circle.fill", "plus.circle" -> Icons.Rounded.AddCircle
    "checkmark", "checkmark.circle", "checkmark.circle.fill" -> Icons.Rounded.Check
    "book.closed.fill", "book.closed", "book.fill", "book" -> Icons.Rounded.MenuBook
    "house.fill", "house" -> Icons.Rounded.Home
    "person.fill", "person" -> Icons.Rounded.Person
    "person.2.fill", "person.3.fill", "person.3" -> Icons.Rounded.Group
    "person.crop.rectangle.stack", "person.crop.rectangle.stack.fill" -> Icons.Rounded.Dashboard
    "person.2", "groups" -> Icons.Rounded.Groups
    "hand.thumbsup", "hand.thumbsup.fill" -> Icons.Rounded.ThumbUp
    "chart.bar", "chart.bar.fill" -> Icons.Rounded.BarChart
    "calendar" -> Icons.Rounded.CalendarMonth
    "bell.fill", "bell" -> Icons.Rounded.Notifications
    "gearshape.fill", "gearshape", "gear" -> Icons.Rounded.Settings
    "heart.fill", "heart" -> Icons.Rounded.Favorite
    "star.fill", "star" -> Icons.Rounded.Star
    "bolt.fill", "bolt" -> Icons.Rounded.Bolt
    "leaf.fill", "leaf" -> Icons.Rounded.Eco
    "play.fill", "play" -> Icons.Rounded.PlayArrow
    "pause.fill", "pause" -> Icons.Rounded.Pause
    "video.fill", "video" -> Icons.Rounded.Videocam
    "square.and.arrow.up" -> Icons.Rounded.Share
    "envelope.fill", "envelope" -> Icons.Rounded.Email
    "pencil.and.outline", "pencil", "square.and.pencil", "pencil.line" -> Icons.Rounded.Edit
    "sparkles", "wand.and.stars" -> Icons.Rounded.AutoAwesome
    // Time-of-day icons for the Today tab (sunrise→noon→sunset→moon).
    "sunrise", "sunrise.fill", "sunset", "sunset.fill", "sun.horizon", "sun.horizon.fill" -> Icons.Rounded.WbTwilight
    "sun.max", "sun.max.fill", "sun.min", "sun.min.fill" -> Icons.Rounded.WbSunny
    "moon", "moon.fill", "moonrise", "moonrise.fill", "moon.zzz", "moon.zzz.fill", "moon.stars", "moon.stars.fill" -> Icons.Rounded.DarkMode
    else -> Icons.Rounded.Circle
}

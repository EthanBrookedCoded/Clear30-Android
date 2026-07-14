package org.clear30.views.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ContactSupport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
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
// Achievement icons (achievements.definitions sf_symbols → closest Material icon)
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.EmojiPeople
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps iOS SF Symbol names (used as `icon: String` throughout the app) to the
 * closest Material icon. Expanded as more symbols are referenced; unknown names
 * fall back to a filled circle.
 */
fun sfSymbol(name: String): ImageVector = when (name) {
    "chevron.backward", "chevron.left", "arrow.left" -> Icons.Rounded.ChevronLeft
    "chevron.forward", "chevron.right" -> Icons.Rounded.ChevronRight
    "chevron.down" -> Icons.Rounded.KeyboardArrowDown
    "chevron.up" -> Icons.Rounded.KeyboardArrowUp
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
    "phone.fill", "phone" -> Icons.Rounded.Phone
    "ellipsis.rectangle", "ellipsis.rectangle.fill" -> Icons.Rounded.Dialpad
    "pencil.and.outline", "pencil", "square.and.pencil", "pencil.line" -> Icons.Rounded.Edit
    "sparkles", "wand.and.stars" -> Icons.Rounded.AutoAwesome
    // Time-of-day icons for the Today tab (sunrise→noon→sunset→moon).
    "sunrise", "sunrise.fill", "sunset", "sunset.fill", "sun.horizon", "sun.horizon.fill" -> Icons.Rounded.WbTwilight
    "sun.max", "sun.max.fill", "sun.min", "sun.min.fill" -> Icons.Rounded.WbSunny
    "moon", "moon.fill", "moonrise", "moonrise.fill", "moon.zzz", "moon.zzz.fill", "moon.stars", "moon.stars.fill" -> Icons.Rounded.DarkMode
    // Support tab
    "flame.fill", "flame" -> Icons.Rounded.Whatshot
    "magnifyingglass" -> Icons.Rounded.Search
    "text.bubble.fill", "text.bubble", "message.fill", "message", "bubble.fill", "bubble" -> Icons.Rounded.ChatBubble
    "questionmark.bubble.fill", "questionmark.bubble", "questionmark.circle.fill", "questionmark.circle" -> Icons.Rounded.ContactSupport
    "checklist.checked", "checklist" -> Icons.Rounded.Checklist
    "lightbulb.max.fill", "lightbulb.fill", "lightbulb" -> Icons.Rounded.Lightbulb
    "doc.text.fill", "doc.text", "doc.fill", "doc", "person.crop.rectangle", "person.text.rectangle" -> Icons.Rounded.Description
    // Profile tab
    "archivebox.fill", "archivebox", "tray.full.fill", "tray.fill" -> Icons.Rounded.Inventory2
    "info.circle.fill", "info.circle", "info" -> Icons.Rounded.Info
    "arrow.triangle.2.circlepath", "arrow.counterclockwise", "arrow.2.circlepath" -> Icons.Rounded.Autorenew
    "slider.vertical.3", "slider.horizontal.3", "line.3.horizontal.decrease" -> Icons.Rounded.Tune
    "arrow.left.and.right", "arrow.right.and.left", "arrow.left.arrow.right" -> Icons.Rounded.SwapHoriz
    "note.text", "note", "doc.plaintext" -> Icons.AutoMirrored.Rounded.Notes
    "figure.walk.departure", "figure.walk", "rectangle.portrait.and.arrow.right" -> Icons.AutoMirrored.Rounded.Logout
    "person.fill.badge.plus", "person.badge.plus", "person.crop.circle.badge.plus" -> Icons.Rounded.PersonAdd
    "minus.circle", "minus.circle.fill", "minus" -> Icons.Rounded.RemoveCircle
    "arrow.clockwise", "arrow.clockwise.circle" -> Icons.Rounded.Refresh
    "ellipsis", "ellipsis.circle" -> Icons.Rounded.MoreHoriz
    "arrow.up", "arrow.up.circle" -> Icons.Rounded.ArrowUpward
    "lungs.fill", "lungs" -> Icons.Rounded.Air
    "brain.head.profile", "brain.head.profile.fill", "brain", "brain.fill" -> Icons.Rounded.Psychology
    "face.smiling", "face.smiling.fill" -> Icons.Rounded.SentimentSatisfied
    "clock.fill", "clock", "clock.badge" -> Icons.Rounded.Schedule
    "globe" -> Icons.Rounded.Public
    "figure.run" -> Icons.Rounded.DirectionsRun
    "building.columns.fill", "building.columns" -> Icons.Rounded.AccountBalance
    "location.fill", "location" -> Icons.Rounded.LocationOn
    "link" -> Icons.Rounded.Link
    // ── Achievement icons (achievements.definitions sf_symbols) ──
    "leaf.arrow.circlepath" -> Icons.Rounded.Eco
    "wind" -> Icons.Rounded.Air
    "sunrise.circle.fill", "sun.and.horizon.fill" -> Icons.Rounded.WbTwilight
    "cup.and.saucer.fill" -> Icons.Rounded.LocalCafe
    "fork.knife" -> Icons.Rounded.Restaurant
    "arrow.up.forward.app.fill" -> Icons.Rounded.TrendingUp
    "scissors.circle.fill" -> Icons.Rounded.ContentCut
    "hand.wave.fill" -> Icons.Rounded.WavingHand
    "bubble.left.and.bubble.right.fill" -> Icons.Rounded.Forum
    "cross.case.fill" -> Icons.Rounded.MedicalServices
    "figure.2.and.child.holdinghands" -> Icons.Rounded.FamilyRestroom
    "map.fill" -> Icons.Rounded.Map
    "megaphone.fill" -> Icons.Rounded.Campaign
    "arrowshape.turn.up.left.fill" -> Icons.AutoMirrored.Rounded.Reply
    "highlighter" -> Icons.Rounded.Brush
    "figure.hiking" -> Icons.Rounded.Hiking
    "mountain.2.fill" -> Icons.Rounded.Terrain
    "wineglass.fill" -> Icons.Rounded.WineBar
    "bag.fill" -> Icons.Rounded.ShoppingBag
    "cloud.sun.fill" -> Icons.Rounded.WbCloudy
    "clock.badge.checkmark.fill" -> Icons.Rounded.AlarmOn
    "figure.wave.circle.fill" -> Icons.Rounded.EmojiPeople
    "briefcase.fill" -> Icons.Rounded.Work
    "rectangle.stack.badge.play.fill" -> Icons.Rounded.VideoLibrary
    "quote.bubble.fill" -> Icons.Rounded.FormatQuote
    "hands.clap.fill" -> Icons.Rounded.VolunteerActivism
    "cloud.moon.fill" -> Icons.Rounded.NightsStay
    "medal.fill" -> Icons.Rounded.MilitaryTech
    "key.fill" -> Icons.Rounded.Key
    "shield.fill" -> Icons.Rounded.Shield
    "dollarsign.square.fill" -> Icons.Rounded.Paid
    "clock.arrow.trianglehead.2.counterclockwise.rotate.90" -> Icons.Rounded.History
    "figure.socialdance.circle.fill" -> Icons.Rounded.Celebration
    "theatermasks.fill" -> Icons.Rounded.TheaterComedy
    "water.waves.and.arrow.trianglehead.up" -> Icons.Rounded.Waves
    "figure.mixed.cardio.circle.fill" -> Icons.Rounded.FitnessCenter
    "flag.fill" -> Icons.Rounded.Flag
    "crown.fill" -> Icons.Rounded.WorkspacePremium
    "airplane.departure" -> Icons.Rounded.Flight
    "calendar.day.timeline.leading" -> Icons.Rounded.EventNote
    "hand.rays.fill" -> Icons.Rounded.SelfImprovement
    "graduationcap.fill" -> Icons.Rounded.School
    // Craving-games icons (were falling back to a plain circle).
    "square.grid.2x2.fill", "square.grid.2x2" -> Icons.Rounded.GridView
    "gamecontroller.fill", "gamecontroller" -> Icons.Rounded.SportsEsports
    "arrow.up.arrow.down", "arrow.up.and.down" -> Icons.Rounded.SwapVert
    "hand.tap", "hand.tap.fill" -> Icons.Rounded.TouchApp
    "hand.draw", "hand.draw.fill" -> Icons.Rounded.Gesture
    "chart.line.uptrend.xyaxis" -> Icons.Rounded.TrendingUp
    "metronome", "metronome.fill" -> Icons.Rounded.Speed
    "eye", "eye.fill" -> Icons.Rounded.Visibility
    "timer" -> Icons.Rounded.Timer
    "drop.fill", "drop" -> Icons.Rounded.WaterDrop
    "snowflake" -> Icons.Rounded.AcUnit
    "cloud.fill", "cloud" -> Icons.Rounded.Cloud
    "infinity" -> Icons.Rounded.AllInclusive
    else -> Icons.Rounded.Circle
}

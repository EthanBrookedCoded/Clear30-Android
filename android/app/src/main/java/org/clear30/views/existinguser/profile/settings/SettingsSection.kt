package org.clear30.views.existinguser.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.datetime.atDate
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.clear30.data.AlertHandler
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.AppMode
import org.clear30.data.model.Program
import org.clear30.data.model.ToggleSettings
import org.clear30.data.model.ToggleSettingsOption
import org.clear30.data.model.ToggleSettingsOptionType
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.SupabaseUserProps
import org.clear30.data.supabase.clearPopInRequest
import org.clear30.data.supabase.deleteAccount
import org.clear30.data.supabase.schedulePopInRequest
import org.clear30.data.supabase.updateNotificationSettings
import org.clear30.data.supabase.updateSMSSettings
import org.clear30.data.supabase.updateUser
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.DefaultText
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.components.pressScale
import org.clear30.views.components.sfSymbol
import org.clear30.views.existinguser.today.CustomCheckInSetup
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens
import org.clear30.views.theme.Lexend

/**
 * SettingsSection — ported from Profile/Settings (SettingsView.swift). The
 * Options rows (name, emoji, custom check-in, tutorial), the notification /
 * SMS toggles (persisted to UserInfo + Supabase), the Info links and the
 * account actions. [onClose] dismisses the settings overlay (the tutorial row
 * closes settings before the tutorials re-arm, like iOS `onActionCompleted`).
 */
@Composable
fun SettingsSection(userInfo: UserInfo, program: Program, onSignOut: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") version
    var showCustomCheckIn by remember { mutableStateOf(false) }

    val settings = userInfo.notificationSettings ?: ToggleSettings.notificationDefaults(userInfo.isPaid)
        .also { userInfo.notificationSettings = it }
    val sms = userInfo.smsSettings ?: ToggleSettings.smsDefaults(allEnabled = false)
        .also { userInfo.smsSettings = it }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        Logger.logEvent(userInfo.loggingID, LogEventType.openedSettings)
    }

    fun persist() {
        scope.launch {
            Clear30Store.save(userInfo)
            SupabaseController.updateNotificationSettings(settings)
        }
        // Re-arm or cancel the local schedules so the toggle the user just
        // flipped takes effect immediately — the handlers each read the live
        // settings and short-circuit if their category is muted.
        org.clear30.data.NotificationHandler.scheduleCheckInReminder(userInfo)
        version++
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
        // ===== Options (iOS SettingsView `options`) =====
        SettingsSectionHeader("Options")
        NameCard(userInfo)
        SettingsCard("Emoji") { UserEmojiPickerButton(userInfo) }
        SettingsCard(
            "Custom Check In",
            onClick = { showCustomCheckIn = true },
        ) { SettingsTrailingIcon("calendar.badge.checkmark") }
        SettingsSectionHeader("Notifications")
        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                ToggleRow("All notifications", settings.all) { on ->
                    settings.all = on; persist()
                }
                if (settings.all) {
                    ToggleSettingsOption.entries
                        .filter { it.type == ToggleSettingsOptionType.NOTIFICATIONS }
                        .forEach { option ->
                            ToggleRow(option.displayName, settings.options[option] ?: false) { on ->
                                settings.options[option] = on
                                persist()
                                // Pop-in notifications are server-scheduled; tell the
                                // backend to start (or stop) sending them.
                                if (option == ToggleSettingsOption.POP_IN) {
                                    scope.launch {
                                        if (on) {
                                            // Next morning at 9am local — same default the iOS app used.
                                            val nextNine = nextNineAm()
                                            SupabaseController.schedulePopInRequest(nextNine)
                                        } else {
                                            SupabaseController.clearPopInRequest()
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }

        SettingsSectionHeader("Text messages")
        // iOS disables the Text Messages row for adolescent mode (opacity 0.5 +
        // `.disabled`) — SMS accountability isn't offered to minors.
        val smsDisabled = userInfo.mode == AppMode.ADOLESCENT
        Clear30Card(modifier = Modifier.fillMaxWidth().alpha(if (smsDisabled) 0.5f else 1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                ToggleRow("Accountability texts", sms.all, enabled = !smsDisabled) { on ->
                    sms.all = on
                    // No backend NotificationHandler reschedule — SMS is
                    // server-side and the next outbound batch reads the
                    // updated `users.sms_settings` row directly.
                    scope.launch {
                        Clear30Store.save(userInfo)
                        SupabaseController.updateSMSSettings(sms)
                    }
                    Logger.logEvent(
                        userInfo.loggingID,
                        if (on) LogEventType.signedUpForSms else LogEventType.unsubscribedFromSms,
                    )
                    version++
                }
                if (sms.all) {
                    ToggleSettingsOption.entries
                        .filter { it.type == ToggleSettingsOptionType.SMS }
                        .forEach { option ->
                            ToggleRow(option.displayName, sms.options[option] ?: false, enabled = !smsDisabled) { on ->
                                sms.options[option] = on
                                scope.launch {
                                    Clear30Store.save(userInfo)
                                    SupabaseController.updateSMSSettings(sms)
                                }
                                version++
                            }
                        }
                }
            }
        }

        InfoSection(userInfo)

        AccountSection(userInfo, onSignOut)

        StretchedButton("Sign out", gradient = Clear30Gradients.red, modifier = Modifier.fillMaxWidth()) { onSignOut() }
    }

    // Custom check-in management (today/CustomCheckInSetup) as a full page.
    if (showCustomCheckIn) {
        Dialog(
            onDismissRequest = { showCustomCheckIn = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize().background(Clear30Colors.background).statusBarsPadding()) {
                CustomCheckInSetup(program, userInfo) { showCustomCheckIn = false }
            }
        }
    }
}

// MARK: - Settings building blocks (iOS `SettingsCard` / section labels)

/** Section label — iOS `SmallText(...).opacity(0.5)` between card groups. */
@Composable
private fun SettingsSectionHeader(text: String) {
    SmallText(text, color = Clear30Colors.text.copy(alpha = 0.5f))
}

/** One settings row — title left, optional trailing content, tap action (iOS `SettingsCard`). */
@Composable
private fun SettingsCard(
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null) m = m.pressScale(onClick = onClick)
    Clear30Card(modifier = m) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DefaultText(title)
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

/** Dimmed trailing glyph on a settings row (iOS `chevron` etc. at 0.25). */
@Composable
private fun SettingsTrailingIcon(symbol: String) {
    Icon(
        sfSymbol(symbol),
        contentDescription = null,
        tint = Clear30Colors.text.copy(alpha = 0.25f),
        modifier = Modifier.size(15.dp),
    )
}

/**
 * Name row — iOS SettingsView `name`: a trailing-aligned inline TextField that
 * patches `users.name` when focus leaves with a changed, non-empty value.
 */
@Composable
private fun NameCard(userInfo: UserInfo) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf(userInfo.name) }

    fun commit() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == userInfo.name) {
            name = userInfo.name
            return
        }
        scope.launch {
            val error = SupabaseController.updateUser(
                JsonObject(mapOf(SupabaseUserProps.NAME to JsonPrimitive(trimmed))),
            )
            if (error == null) {
                userInfo.name = trimmed
                name = trimmed
                Clear30Store.save(userInfo)
            } else {
                name = userInfo.name
                AlertHandler.show(
                    AlertHandler.Alert(
                        title = "Could not update name",
                        message = error.message,
                    ),
                )
            }
        }
    }

    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DefaultText("Name")
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) commit() },
                singleLine = true,
                // Matches iOS: DefaultText metrics, trailing-aligned, 0.5 alpha.
                textStyle = TextStyle(
                    fontFamily = Lexend,
                    fontSize = 19.sp,
                    color = Clear30Colors.text.copy(alpha = 0.5f),
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(Clear30Colors.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        }
    }
}

/** Info section — iOS `info`: rate-us + manage-subscription cards. */
@Composable
private fun InfoSection(userInfo: UserInfo) {
    val context = LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current

    SettingsSectionHeader("Info")
    SettingsCard(
        "Rate us!",
        onClick = {
            Logger.logEvent(userInfo.loggingID, LogEventType.clickedReviewButton)
            // Play Store app first; fall back to the web listing.
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.clear30")))
            } catch (e: ActivityNotFoundException) {
                uri.openUri("https://play.google.com/store/apps/details?id=org.clear30")
            }
        },
    ) { DefaultText("🌟") }
    SettingsCard(
        "Manage Subscription",
        onClick = {
            Logger.logEvent(userInfo.loggingID, LogEventType.openedManageSubscription)
            // Play subscriptions deep link — handled by the Play Store app.
            uri.openUri("https://play.google.com/store/account/subscriptions?package=org.clear30")
        },
    ) { DefaultText("⚙️") }
}

@Composable
private fun AccountSection(userInfo: org.clear30.data.model.UserInfo, onSignOut: () -> Unit) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    SettingsSectionHeader("Account")
    Clear30Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
            // Privacy policy + terms — open in the system browser. URLs match
            // the iOS app's footer links so legal review only had to land once.
            SmallText("Privacy policy",
                modifier = Modifier.fillMaxWidth().clickable {
                    uri.openUri("https://clear30.org/privacy")
                })
            SmallText("Terms of service",
                modifier = Modifier.fillMaxWidth().clickable {
                    uri.openUri("https://clear30.org/terms")
                })
            SmallText("Delete account",
                color = org.clear30.views.theme.Clear30Colors.red2,
                modifier = Modifier.fillMaxWidth().clickable {
                    confirmDelete = true
                })
        }
    }
    if (confirmDelete) {
        // Two-step confirmation — destructive, so we route through the
        // standard AlertHandler instead of an inline dialog so the user can
        // back out cleanly. On confirm: server delete → sign-out wipes
        // local state.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            org.clear30.data.AlertHandler.show(
                org.clear30.data.AlertHandler.Alert(
                    title = "Delete your account?",
                    message = "This permanently removes your check-ins, journals, and group membership. This can't be undone.",
                    primaryLabel = "Delete",
                    onPrimary = {
                        scope.launch {
                            org.clear30.data.LoadingCoordinator.tracked {
                                org.clear30.data.supabase.SupabaseController.deleteAccount()
                            }
                            org.clear30.data.Logger.logEvent(
                                userInfo.loggingID,
                                org.clear30.data.LogEventType.deletedAccount,
                            )
                            onSignOut()
                        }
                    },
                    secondaryLabel = "Cancel",
                    onSecondary = { confirmDelete = false },
                )
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SmallText(label, color = Clear30Colors.text)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Next 9:00 local — when the iOS app schedules pop-ins. */
private fun nextNineAm(): kotlinx.datetime.Instant {
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(tz)
    val targetToday = kotlinx.datetime.LocalTime(9, 0).atDate(now.date)
    val target = if (targetToday > now) targetToday
        else kotlinx.datetime.LocalTime(9, 0).atDate(
            // LocalDate + DatePeriod is the resolvable overload in 0.6.x; the
            // (Int, DateTimeUnit) form needs an explicit import that isn't on
            // every kotlinx-datetime version, so prefer the period form.
            now.date.plus(kotlinx.datetime.DatePeriod(days = 1))
        )
    return target.toInstant(tz)
}

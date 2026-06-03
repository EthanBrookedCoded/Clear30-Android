package org.clear30.views.existinguser.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.datetime.atDate
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.clear30.data.Clear30Store
import org.clear30.data.LogEventType
import org.clear30.data.Logger
import org.clear30.data.model.ToggleSettings
import org.clear30.data.model.ToggleSettingsOption
import org.clear30.data.model.ToggleSettingsOptionType
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.clearPopInRequest
import org.clear30.data.supabase.schedulePopInRequest
import org.clear30.data.supabase.updateNotificationSettings
import org.clear30.data.supabase.updateSMSSettings
import org.clear30.views.components.Clear30Card
import org.clear30.views.components.Heading3
import org.clear30.views.components.SmallText
import org.clear30.views.components.StretchedButton
import org.clear30.views.theme.Clear30Colors
import org.clear30.views.theme.Clear30Gradients
import org.clear30.views.theme.Dimens

/**
 * SettingsSection — ported from Profile/Settings. Edits notification toggles
 * (the ported [ToggleSettings]), persisting to UserInfo + Supabase, and signs
 * out. SMS settings + the full settings list (account, privacy, etc.) follow.
 */
@Composable
fun SettingsSection(userInfo: UserInfo, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") version

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
        Heading3("Notifications")
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

        Heading3("Text messages")
        Clear30Card(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.cardSpacing / 2)) {
                ToggleRow("Accountability texts", sms.all) { on ->
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
                            ToggleRow(option.displayName, sms.options[option] ?: false) { on ->
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

        AccountSection(userInfo, onSignOut)

        StretchedButton("Sign out", gradient = Clear30Gradients.red, modifier = Modifier.fillMaxWidth()) { onSignOut() }
    }
}

@Composable
private fun AccountSection(userInfo: org.clear30.data.model.UserInfo, onSignOut: () -> Unit) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Heading3("Account")
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
            SmallText("Manage subscription",
                modifier = Modifier.fillMaxWidth().clickable {
                    org.clear30.data.Logger.logEvent(
                        userInfo.loggingID,
                        org.clear30.data.LogEventType.openedManageSubscription,
                    )
                    // Play subscriptions deep link — handled by the Play Store app.
                    uri.openUri("https://play.google.com/store/account/subscriptions?package=org.clear30")
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
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SmallText(label, color = Clear30Colors.text)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Next 9:00 local — when the iOS app schedules pop-ins. */
private fun nextNineAm(): kotlinx.datetime.Instant {
    val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
    val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(tz)
    val targetToday = kotlinx.datetime.LocalTime(9, 0).atDate(now.date)
    val target = if (targetToday > now) targetToday
        else kotlinx.datetime.LocalTime(9, 0).atDate(now.date.plus(1, kotlinx.datetime.DateTimeUnit.DAY))
    return target.toInstant(tz)
}

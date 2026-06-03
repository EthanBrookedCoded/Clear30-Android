package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** ToggleSettingsOptionType — ported from ToggleSettings.swift. */
enum class ToggleSettingsOptionType { SMS, NOTIFICATIONS }

/**
 * ToggleSettingsOption — ported 1:1. @SerialName carries the iOS rawValue so
 * persisted/Supabase JSON stays byte-compatible (options is a string-keyed map).
 */
@Serializable
enum class ToggleSettingsOption(val displayName: String) {
    @SerialName("Accountability Texts") ACCOUNTABILITY("Accountability Texts"),
    @SerialName("Message Notifications") CONTENT("Message Notifications"),
    @SerialName("Check In Notifications") CHECK_IN("Check In Notifications"),
    @SerialName("Progress Notifications") POP_IN("Progress Notifications"),
    @SerialName("Community Notifications") COMMUNITY("Community Notifications"),
    @SerialName("Group Notifications") GROUP("Group Notifications"),
    @SerialName("Health Notifications") HEALTH("Health Notifications"),
    @SerialName("Achievement Notifications") ACHIEVEMENT("Achievement Notifications");

    val type: ToggleSettingsOptionType
        get() = if (this == ACCOUNTABILITY) ToggleSettingsOptionType.SMS
        else ToggleSettingsOptionType.NOTIFICATIONS

    val requiresSetup: Boolean get() = false

    /** Whether this option is gated behind a paid entitlement. */
    val paid: Boolean
        get() = when (this) {
            ACCOUNTABILITY, CONTENT, HEALTH, ACHIEVEMENT -> true
            CHECK_IN, POP_IN, COMMUNITY, GROUP -> false
        }
}

/**
 * ToggleSettings — ported from ToggleSettings.swift.
 *
 * The iOS type used a custom Codable so the options dict serialises with String
 * keys (Supabase expects `{key: value}`). kotlinx encodes an enum-keyed map
 * using each entry's @SerialName, producing the same `{ "Message Notifications": true, ... }`
 * shape, so no custom serializer is needed here.
 */
@Serializable
data class ToggleSettings(
    val name: String,
    var all: Boolean,
    var options: MutableMap<ToggleSettingsOption, Boolean> = mutableMapOf(),
) {
    fun typeEnabled(type: ToggleSettingsOption): Boolean = all && (options[type] ?: false)

    companion object {
        fun smsDefaults(allEnabled: Boolean = true): ToggleSettings = ToggleSettings(
            name = "Text Messages",
            all = allEnabled,
            options = ToggleSettingsOption.entries
                .filter { it.type == ToggleSettingsOptionType.SMS }
                .associateWith { false }
                .toMutableMap(),
        )

        fun notificationDefaults(paid: Boolean): ToggleSettings = ToggleSettings(
            name = "Notifications",
            all = true,
            options = ToggleSettingsOption.entries
                .filter { it.type == ToggleSettingsOptionType.NOTIFICATIONS }
                .associateWith { option -> (option.paid && paid) || !option.paid }
                .toMutableMap(),
        )
    }
}

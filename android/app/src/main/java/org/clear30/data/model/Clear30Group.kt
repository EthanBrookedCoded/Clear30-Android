package org.clear30.data.model

import androidx.compose.ui.graphics.Brush
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.views.theme.Clear30Gradients

/**
 * Clear30Group — ported from Clear30Group.swift. An accountability group with
 * its members; the per-member day-info drives the group's aggregate stats.
 * (Now the full model — it had been a placeholder while only UserInfo's
 * deprecated `group` field referenced it.)
 */
@Serializable
data class Clear30Group(
    val id: String = "",
    val name: String? = null,
    val hue: Double = 0.4,
    val members: List<Clear30GroupMember> = emptyList(),
    val subscribed: List<Clear30GroupSubscription>? = emptyList(),
    val notes: List<Clear30GroupNote>? = emptyList(),
) {
    val gradient: Brush get() = Clear30Gradients.hue(hue, GROUP_SATURATION, GROUP_BRIGHTNESS, hueOffset = 0.0)

    val daysCheckedIn: Int get() = members.sumOf { it.daysCheckedIn }
    val daysSober: Int get() = members.sumOf { it.daysSoberCount }
    val daysSmoked: Int get() = members.sumOf { it.daysSmokedCount }
}

@Serializable
data class Clear30GroupMember(
    val memberID: String,
    val name: String,
    val emoji: String,
    val dayInfo: Map<String, ProgramDayInfo>? = null,
    val showInRank: Boolean? = null,
    val joinDate: Instant? = null,
) {
    val daysCheckedIn: Int get() = dayInfo?.size ?: 0
    val daysSoberCount: Int get() = dayInfo?.values?.count { it.sober == true } ?: 0
    val daysSmokedCount: Int get() = dayInfo?.values?.count { it.sober == false } ?: 0
}

@Serializable
data class Clear30GroupSubscription(val subscribedTo: String)

@Serializable
data class Clear30GroupNote(
    val fromMemberID: String,
    val message: String,
    val timestamp: Instant? = null,
)

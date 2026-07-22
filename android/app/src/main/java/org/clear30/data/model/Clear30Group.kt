package org.clear30.data.model

import androidx.compose.ui.graphics.Brush
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
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
    // Defaults so a member whose users row has no name/emoji can't throw and nuke
    // the whole group decode (which would leave the user stuck on the create/join
    // screen even though the group exists).
    val name: String = "",
    val emoji: String = "",
    // The `groups.get()` payload keys this `_dayInfo` (see migration); without the
    // @SerialName it deserialized to null and every member showed 0 days.
    @SerialName("_dayInfo") val dayInfo: Map<String, ProgramDayInfo>? = null,
    val showInRank: Boolean? = null,
    val joinDate: Instant? = null,
) {
    /**
     * iOS `filteredDayInfo` (Clear30Group.swift:120-126): drop days BEFORE the
     * member's join date so pre-join history doesn't inflate their group stats.
     * dayInfo keys are zero-padded `YYYY-MM-DD`, so a lexical `>=` compare equals
     * a chronological one — no parsing needed.
     */
    private val filteredDayInfo: Map<String, ProgramDayInfo>? get() {
        val di = dayInfo ?: return null
        val join = joinDate?.let { PlainDate.from(it).dateString } ?: return di
        return di.filterKeys { it >= join }
    }

    // iOS (Clear30Group.swift:128-138): checked-in = days with a sober value (NOT
    // map size — symptom-only days have sober == null and must not count).
    val daysCheckedIn: Int get() = filteredDayInfo?.values?.count { it.sober != null } ?: 0
    val daysSoberCount: Int get() = filteredDayInfo?.values?.count { it.sober == true } ?: 0
    val daysSmokedCount: Int get() = filteredDayInfo?.values?.count { it.sober == false } ?: 0
}

@Serializable
data class Clear30GroupSubscription(val subscribedTo: String)

@Serializable
data class Clear30GroupNote(
    val fromMemberID: String,
    val message: String,
    val timestamp: Instant? = null,
)

/** A group chat message (`groups.group_messages`) — Swift `Clear30GroupChatMessage`. */
@Serializable
data class Clear30GroupChatMessage(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val message: String,
    val timestamp: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
)

/** Group activity kinds (`groups.group_activity.activity`) — Swift `Clear30GroupActivityItemType`. */
@Serializable
enum class Clear30GroupActivityItemType {
    @SerialName("smoked") SMOKED,
    @SerialName("sober") SOBER,
    @SerialName("joined") JOINED,
    @SerialName("message") MESSAGE,
}

/** A group activity-feed row (`groups.group_activity`) — Swift `Clear30GroupActivityItem`. */
@Serializable
data class Clear30GroupActivityItem(
    val id: String,
    @SerialName("group_id") val groupId: String = "",
    @SerialName("user_id") val userId: String,
    val activity: String,
    val timestamp: String,
) {
    val type: Clear30GroupActivityItemType?
        get() = when (activity) {
            "smoked" -> Clear30GroupActivityItemType.SMOKED
            "sober" -> Clear30GroupActivityItemType.SOBER
            "joined" -> Clear30GroupActivityItemType.JOINED
            "message" -> Clear30GroupActivityItemType.MESSAGE
            else -> null
        }
}

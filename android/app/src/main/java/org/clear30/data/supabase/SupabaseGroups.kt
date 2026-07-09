package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.Clear30GroupActivityItem
import org.clear30.data.model.Clear30GroupActivityItemType
import org.clear30.data.model.Clear30GroupChatMessage
import org.clear30.data.supabase.SupabaseController.SupabaseFunctionError
import org.clear30.util.now
import java.util.UUID

/**
 * Groups data layer — ported from SupabaseGroups.swift.
 *
 * CRITICAL: every group table and RPC lives in the non-public **`groups`** schema
 * (`groups.groups`, `groups.group_messages`, `groups.get()`, `groups.add_member()`,
 * …). The previous Android code used bare `from("groups")` and the public-schema
 * `callFunction` helpers, so every group call hit the wrong schema and silently
 * failed (same class of bug as community/achievements). All calls here target
 * `GROUPS_SCHEMA` via `from(schema, table)` / `rpc(name, params) { schema = … }`.
 */
private const val GROUPS_SCHEMA = "groups"

@Serializable private data class CreateGroupInsert(val id: String, val name: String, val hue: Double)

/** Fetch the caller's group with members/notes (`groups.get()` → json). */
suspend fun SupabaseController.getGroup(): Result<Clear30Group> = runCatching {
    client.postgrest.rpc("get", buildJsonObject {}) { schema = GROUPS_SCHEMA }
        .decodeAs<Clear30Group>()
}.onFailure { android.util.Log.w("SupabaseGroups", "getGroup failed: ${it.message}") }

/**
 * Insert a new group and return its id (Swift `createGroup`).
 *
 * The id is generated client-side (iOS `SupabaseGroup.id`) and sent in the insert
 * so we do NOT read the row back. The `groups` SELECT RLS policy
 * (`get_user_group() = groups.id`) hides the new row until the caller is a
 * member — so the previous `insert(...) { select() }` read-back always came back
 * empty and createGroup returned null, making "Start your group" silently fail.
 * Membership is added immediately after, via the SECURITY DEFINER `add_member`.
 */
suspend fun SupabaseController.createGroup(name: String, hue: Double): Result<String> = runCatching {
    val id = UUID.randomUUID().toString()
    client.postgrest.from(GROUPS_SCHEMA, "groups").insert(CreateGroupInsert(id, name, hue))
    id
}.onFailure { android.util.Log.w("SupabaseGroups", "createGroup failed: ${it.message}") }

/** Rename / recolor the group (Swift `updateGroup`). */
suspend fun SupabaseController.updateGroup(groupId: String, name: String?, hue: Double?): SupabaseFunctionError? = runCatching {
    client.postgrest.from(GROUPS_SCHEMA, "groups").update({
        name?.let { set("name", it) }
        hue?.let { set("hue", it) }
    }) { filter { eq("id", groupId) } }
}.fold({ null }, { it.toError() })

/** Add the current authenticated user to [groupId] (`groups.add_member`). */
suspend fun SupabaseController.addGroupMember(groupId: String): SupabaseFunctionError? = runCatching {
    client.postgrest.rpc("add_member", buildJsonObject { put("group_id", groupId) }) { schema = GROUPS_SCHEMA }
}.fold({ null }, { it.toError() })

/** Remove [userID] from their group (`groups.remove_member(user_id)`). */
suspend fun SupabaseController.removeGroupMember(userID: String): SupabaseFunctionError? = runCatching {
    client.postgrest.rpc("remove_member", buildJsonObject { put("user_id", userID) }) { schema = GROUPS_SCHEMA }
}.fold({ null }, { it.toError() })

// MARK: - Notes (Swift sendGroupNote) — directed (group_notes.to_member_id is NOT NULL)

@Serializable
private data class GroupNoteInsert(
    val group_id: String,
    val to_member_id: String,
    val from_member_id: String,
    val message: String,
)

/** Send a note to a specific member (Swift `sendGroupNote`). */
suspend fun SupabaseController.addGroupNote(
    groupId: String, toMemberId: String, fromMemberId: String, message: String,
): SupabaseFunctionError? = runCatching {
    client.postgrest.from(GROUPS_SCHEMA, "group_notes")
        .insert(GroupNoteInsert(groupId, toMemberId, fromMemberId, message))
}.fold({ null }, { it.toError() })

// MARK: - Pings (Swift sendGroupPing)

@Serializable
private data class GroupPingInsert(val from_user_id: String, val to_user_id: String, val group_id: String)

/** Nudge another member (`groups.group_pings`). */
suspend fun SupabaseController.sendGroupPing(groupId: String, toUserId: String, fromUserId: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(GROUPS_SCHEMA, "group_pings")
        .insert(GroupPingInsert(fromUserId, toUserId, groupId))
}.fold({ null }, { it.toError() })

// MARK: - Check-in activity (Swift addGroupCheckInActivity → groups.add_checkin_activity)

/** Log the user's check-in to the group activity feed. [sober] null = no smoke/sober tag. */
suspend fun SupabaseController.addGroupCheckInActivity(sober: Boolean?): SupabaseFunctionError? = runCatching {
    val params = buildJsonObject {
        put("timezone", TimeZone.currentSystemDefault().id)
        if (sober != null) {
            put("activity_type", if (sober) Clear30GroupActivityItemType.SOBER.name.lowercase() else Clear30GroupActivityItemType.SMOKED.name.lowercase())
        }
    }
    client.postgrest.rpc("add_checkin_activity", params) { schema = GROUPS_SCHEMA }
}.fold({ null }, { it.toError() })

/** Paged activity items, newest-first (Swift `getGroupActivityItems`). */
suspend fun SupabaseController.getGroupActivityItems(groupId: String, startIndex: Int = 0, count: Int = 20): Result<List<Clear30GroupActivityItem>> = runCatching {
    client.postgrest.from(GROUPS_SCHEMA, "group_activity")
        .select {
            filter { eq("group_id", groupId) }
            order("timestamp", Order.DESCENDING)
            range(startIndex.toLong(), (startIndex + count - 1).toLong())
        }
        .decodeList<Clear30GroupActivityItem>()
}

// MARK: - Subscriptions (Swift updateGroupSubscriptions) — replace-all semantics

@Serializable
private data class GroupSubscriptionInsert(val group_id: String, val user_id: String, val subscribed_to: String)

/** Replace the user's per-member notification subscriptions for the group. */
suspend fun SupabaseController.updateGroupSubscriptions(groupId: String, userId: String, subscribedTo: List<String>): SupabaseFunctionError? = runCatching {
    client.postgrest.from(GROUPS_SCHEMA, "group_subscriptions").delete {
        filter {
            eq("group_id", groupId)
            eq("user_id", userId)
        }
    }
    if (subscribedTo.isNotEmpty()) {
        client.postgrest.from(GROUPS_SCHEMA, "group_subscriptions")
            .insert(subscribedTo.map { GroupSubscriptionInsert(groupId, userId, it) })
    }
}.fold({ null }, { it.toError() })

// MARK: - Group chat (Swift getGroupChatMessages / sendGroupChatMessage / deleteGroupChatMessage)

/** Page of (all) chat messages, newest-first. Filter out `isDeleted` in the UI. */
suspend fun SupabaseController.getGroupChatMessages(groupId: String, startIndex: Int = 0, count: Int = 20): Result<List<Clear30GroupChatMessage>> = runCatching {
    client.postgrest.from(GROUPS_SCHEMA, "group_messages")
        .select {
            filter { eq("group_id", groupId) }
            order("timestamp", Order.DESCENDING)
            range(startIndex.toLong(), (startIndex + count - 1).toLong())
        }
        .decodeList<Clear30GroupChatMessage>()
}

/** Send a chat message (client generates id + timestamp, like iOS). */
suspend fun SupabaseController.sendGroupChatMessage(groupId: String, userId: String, message: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(GROUPS_SCHEMA, "group_messages").insert(
        Clear30GroupChatMessage(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            userId = userId,
            message = message,
            timestamp = now().toString(),
            isDeleted = false,
        ),
    )
}.fold({ null }, { it.toError() })

/** Soft-delete one of the user's own chat messages. */
suspend fun SupabaseController.deleteGroupChatMessage(messageId: String): SupabaseFunctionError? = runCatching {
    client.postgrest.from(GROUPS_SCHEMA, "group_messages")
        .update({ set("is_deleted", true) }) { filter { eq("id", messageId) } }
}.fold({ null }, { it.toError() })

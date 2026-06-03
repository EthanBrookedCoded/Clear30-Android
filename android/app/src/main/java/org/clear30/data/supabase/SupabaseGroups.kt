package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

/**
 * Group write path — ported from SupabaseGroups.swift. Creating a group is a
 * direct insert into the `groups` table (returning the new id); joining /
 * leaving fan out to the `add_member` / `remove_member` RPCs the iOS app uses.
 */

@Serializable
private data class CreateGroupInsert(val name: String, val hue: Double)

@Serializable
private data class GroupRow(val id: String)

/** Insert a new group, return the id. */
suspend fun SupabaseController.createGroup(name: String, hue: Double): String? = runCatching {
    client.postgrest.from("groups")
        .insert(CreateGroupInsert(name, hue)) { select() }
        .decodeList<GroupRow>()
        .firstOrNull()?.id
}.getOrNull()

/** Add the current authenticated user as a member of [groupId]. */
suspend fun SupabaseController.addGroupMember(groupId: String): SupabaseController.SupabaseFunctionError? =
    callFunction(SupabaseFunction.addGroupMember, mapOf("group_id" to groupId))

/** Remove the current authenticated user from their group. */
suspend fun SupabaseController.removeGroupMember(): SupabaseController.SupabaseFunctionError? =
    callFunction(SupabaseFunction.removeGroupMember)

/** Insert a group note (Swift `addGroupNote`). RLS lets only members write. */
@Serializable
private data class GroupNoteInsert(val group_id: String, val from_member_id: String, val message: String)

suspend fun SupabaseController.addGroupNote(
    groupId: String, fromMemberId: String, message: String,
): SupabaseController.SupabaseFunctionError? = runCatching {
    client.postgrest.from("group_notes")
        .insert(GroupNoteInsert(groupId, fromMemberId, message))
}.fold({ null }, { it.toError() })

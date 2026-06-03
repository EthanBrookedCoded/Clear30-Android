package org.clear30.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.SupabaseFunction
import org.clear30.data.supabase.addGroupMember
import org.clear30.data.supabase.addGroupNote
import org.clear30.data.supabase.createGroup
import org.clear30.data.supabase.removeGroupMember

/**
 * GroupController — ported from GroupController.swift. Owns the user's group
 * state and refreshes it from the `get_user_group` RPC; create/join/leave fan
 * out to the matching backend writes and re-pull the canonical group on success
 * so the local copy stays in sync.
 */
class GroupController(private val userInfo: UserInfo) {

    private val _group = MutableStateFlow<Clear30Group?>(null)
    val group: StateFlow<Clear30Group?> = _group.asStateFlow()

    /** Load the user's group (no-op if they aren't in one). */
    suspend fun refresh() {
        if (userInfo.groupID.isNullOrEmpty()) { _group.value = null; return }
        val (group, _) = SupabaseController.callFunction(SupabaseFunction.getUserGroup, Clear30Group::class.java)
        _group.value = group
    }

    /**
     * Create a fresh group with [name] / [hue], add the current user as the
     * first member, and pull the resulting group so [group] reflects it.
     * Returns null on success, a user-facing error message on failure.
     */
    suspend fun create(name: String, hue: Double = 0.4): String? {
        val groupId = SupabaseController.createGroup(name, hue)
            ?: return "Couldn't create the group right now. Try again in a moment."
        val joinErr = SupabaseController.addGroupMember(groupId)
        if (joinErr != null) return joinErr.message.ifBlank { "Couldn't add you to the new group." }

        userInfo.groupID = groupId
        Clear30Store.save(userInfo)
        Logger.logEvent(userInfo.loggingID, LogEventType.createdGroup)
        refresh()
        return null
    }

    /** Join an existing group by id (the "group code"). */
    suspend fun join(groupId: String): String? {
        val err = SupabaseController.addGroupMember(groupId)
        if (err != null) return err.message.ifBlank { "Couldn't join that group." }

        userInfo.groupID = groupId
        Clear30Store.save(userInfo)
        Logger.logEvent(userInfo.loggingID, LogEventType.joinedGroup)
        refresh()
        return null
    }

    /**
     * Add a note to the group from the current user. Returns null on success
     * or a user-facing error message on failure. Refreshes the group on
     * success so the note appears in [group] immediately.
     */
    suspend fun addNote(message: String): String? {
        val groupId = userInfo.groupID ?: return "You're not in a group yet."
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return "Note can't be empty."
        val err = SupabaseController.addGroupNote(groupId, userInfo.userID, trimmed)
        if (err != null) return err.message.ifBlank { "Couldn't post the note." }
        Logger.logEvent(userInfo.loggingID, LogEventType.createdGroupNote)
        refresh()
        return null
    }

    /** Leave the current group (no-op if not in one). */
    suspend fun leave(): String? {
        if (userInfo.groupID.isNullOrEmpty()) return null
        val err = SupabaseController.removeGroupMember()
        if (err != null) return err.message.ifBlank { "Couldn't leave the group." }

        userInfo.groupID = null
        Clear30Store.save(userInfo)
        Logger.logEvent(userInfo.loggingID, LogEventType.leftGroup)
        _group.value = null
        return null
    }
}

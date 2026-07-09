package org.clear30.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.clear30.data.model.Clear30Group
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.addGroupMember
import org.clear30.data.supabase.addGroupNote
import org.clear30.data.supabase.createGroup
import org.clear30.data.supabase.ensureUserRow
import org.clear30.data.supabase.getGroup
import org.clear30.data.supabase.removeGroupMember
import org.clear30.data.supabase.updateDayInfo
import org.clear30.data.supabase.updateGroup

/**
 * GroupController — ported from GroupController.swift. Owns the user's group
 * state and refreshes it from the `get_user_group` RPC; create/join/leave fan
 * out to the matching backend writes and re-pull the canonical group on success
 * so the local copy stays in sync.
 */
class GroupController(private val userInfo: UserInfo, private val program: Program) {

    private val _group = MutableStateFlow<Clear30Group?>(null)
    val group: StateFlow<Clear30Group?> = _group.asStateFlow()

    /** Load the user's group (no-op if they aren't in one). Uses the `groups.get()`
     *  RPC, which returns the full group json (members/notes); `get_user_group`
     *  only returns the bare uuid. */
    suspend fun refresh() {
        if (userInfo.groupID.isNullOrEmpty()) { _group.value = null; return }
        _group.value = SupabaseController.getGroup().getOrNull()
    }

    /**
     * Create a fresh group with [name] / [hue], add the current user as the
     * first member, and pull the resulting group so [group] reflects it.
     * Returns null on success, a user-facing error message on failure.
     */
    suspend fun create(name: String, hue: Double = 0.7): String? {
        ensureProvisioned()?.let { return it }
        val groupId = SupabaseController.createGroup(name, hue).getOrElse { e ->
            return e.message?.takeIf { it.isNotBlank() }
                ?: "Couldn't create the group right now. Try again in a moment."
        }
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
        ensureProvisioned()?.let { return it }
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
    suspend fun addNote(message: String, toMemberId: String = userInfo.userID): String? {
        val groupId = userInfo.groupID ?: return "You're not in a group yet."
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return "Note can't be empty."
        // group_notes.to_member_id is NOT NULL (notes are directed in iOS). Until a
        // recipient picker exists, default the recipient to the sender.
        val err = SupabaseController.addGroupNote(groupId, toMemberId, userInfo.userID, trimmed)
        if (err != null) return err.message.ifBlank { "Couldn't post the note." }
        Logger.logEvent(userInfo.loggingID, LogEventType.createdGroupNote)
        refresh()
        return null
    }

    /** Rename / recolor the group (iOS GroupSettings name + hue edit). */
    suspend fun rename(name: String, hue: Double): String? {
        val gid = userInfo.groupID ?: return null
        val err = SupabaseController.updateGroup(gid, name.ifBlank { null }, hue)
        if (err != null) return err.message.ifBlank { "Couldn't update the group." }
        refresh()
        return null
    }

    /** Remove another member from the group (iOS GroupSettings remove). */
    suspend fun removeMember(userID: String): String? {
        val err = SupabaseController.removeGroupMember(userID)
        if (err != null) return err.message.ifBlank { "Couldn't remove that member." }
        refresh()
        return null
    }

    /**
     * Guarantee the caller has a `public.users` row before any groups RPC. A
     * brand-new account that never completed onboarding's create_user (or whose
     * create_user failed silently) has no row, so `get_user_id()` is null and both
     * the `groups.groups` insert policy and `add_member` reject — the user could
     * never start OR join a group. Returns an error message on failure, else null.
     */
    private suspend fun ensureProvisioned(): String? {
        val (uid, err) = SupabaseController.ensureUserRow(
            name = userInfo.name,
            emoji = userInfo.emoji ?: "😁",
            loggingID = userInfo.loggingID,
            fcmToken = userInfo.fcmToken,
            smsSettings = userInfo.smsSettings,
        )
        if (err != null) return err.message.ifBlank { "Couldn't set up your account. Try again." }
        if (uid.isNullOrEmpty()) return "Couldn't set up your account. Try again."
        // Keep the local copy in sync with the resolved server users.id.
        if (userInfo._userID != uid) {
            userInfo._userID = uid
            Clear30Store.save(userInfo)
        }
        // Normalize day_info to the backend's positional-array shape. Older Android
        // builds wrote it as a JSON object, which makes groups.get() throw on
        // jsonb_array_length() — so the group was created but never rendered. We push
        // the device's authoritative day_info (same value the check-in sync sends),
        // which both repairs the shape and is lossless. Best-effort.
        SupabaseController.updateDayInfo(program.dayInfo)
        return null
    }

    /** Leave the current group (no-op if not in one). */
    suspend fun leave(): String? {
        if (userInfo.groupID.isNullOrEmpty()) return null
        val err = SupabaseController.removeGroupMember(userInfo.userID)
        if (err != null) return err.message.ifBlank { "Couldn't leave the group." }

        userInfo.groupID = null
        Clear30Store.save(userInfo)
        Logger.logEvent(userInfo.loggingID, LogEventType.leftGroup)
        _group.value = null
        return null
    }
}

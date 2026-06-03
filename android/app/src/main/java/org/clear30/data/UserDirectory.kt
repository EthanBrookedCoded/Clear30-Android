package org.clear30.data

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.data.supabase.SupabaseController

/**
 * UserDirectory — in-memory cache of public profile bits (display name +
 * emoji) keyed by user id.
 *
 * Ported from the iOS users-row helper that backed PostCard / CommentCard /
 * GroupMember labels — every screen that shows "who said this" hits this
 * cache, the cache batches misses into a single `in()` query against
 * `public.users`, and updates a shared StateFlow so multiple cards bound to
 * the same id render once the response lands.
 *
 * Misses fall back to a stable handle derived from the id, so the UI never
 * shows a blank slot while we wait on the network.
 */
object UserDirectory {

    @Serializable
    private data class UsersRow(
        val id: String,
        @SerialName("display_name") val displayName: String? = null,
        @SerialName("emoji") val emoji: String? = null,
        @SerialName("name") val name: String? = null,
    )

    data class Entry(val displayName: String, val emoji: String?)

    private val _cache = MutableStateFlow<Map<String, Entry>>(emptyMap())
    val cache: StateFlow<Map<String, Entry>> = _cache.asStateFlow()

    /** Pending ids waiting on the next batched fetch; flushed by [flush]. */
    private val pending = mutableSetOf<String>()
    private val mutex = Mutex()

    /**
     * Look up [userId] synchronously. Returns the cached entry or, on miss,
     * queues a fetch and returns a stable fallback derived from the id —
     * "User #abc123" for non-empty ids, "anon" for blanks.
     */
    fun lookup(userId: String): Entry {
        if (userId.isBlank()) return Entry(displayName = "anon", emoji = null)
        _cache.value[userId]?.let { return it }
        // Mark as pending; caller-side [flush] picks it up. Synchronization is
        // fine on a plain MutableSet here — this is called from Compose on the
        // main thread.
        pending += userId
        return Entry(displayName = "User #${userId.takeLast(6)}", emoji = null)
    }

    /**
     * Flush any pending ids in a single batched query. Suspends; safe to call
     * from a LaunchedEffect on the screens that list user ids (community feed,
     * comment list, group member list). Re-entrant calls coalesce — only the
     * first caller does the I/O while later calls see the populated cache.
     */
    suspend fun flush() {
        val ids = mutex.withLock {
            val snap = pending - _cache.value.keys
            pending.clear()
            snap
        }
        if (ids.isEmpty()) return
        runCatching {
            SupabaseController.client.postgrest.from("users")
                .select(Columns.raw("id, display_name, emoji, name")) {
                    filter { isIn("id", ids.toList()) }
                }
                .decodeList<UsersRow>()
        }.onSuccess { rows ->
            val additions = rows.associate { row ->
                row.id to Entry(
                    displayName = row.displayName?.takeIf { it.isNotBlank() }
                        ?: row.name?.takeIf { it.isNotBlank() }
                        ?: "User #${row.id.takeLast(6)}",
                    emoji = row.emoji?.takeIf { it.isNotBlank() },
                )
            }
            _cache.value = _cache.value + additions
        }
    }

    /** Sign-out — flush the cache so the next account starts clean. */
    fun clear() {
        pending.clear()
        _cache.value = emptyMap()
    }
}

package org.clear30.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.clear30.data.model.JournalEntry
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.fetchUserData
import org.clear30.data.supabase.getUserID
import org.clear30.data.supabase.syncProgramState
import org.clear30.util.now

/**
 * OldAppMigrationHandler — backlog D2: recover what the OLD React-Native
 * Clear30 app left behind for its ~3.2k users. Two independent sources:
 *
 *  1. **Server**: the old app synced check-ins to `users.day_info` in the SAME
 *     positional wire format this app reads (~1,650 of the old rows carry
 *     real history). The returning-user gate keys on `content_info` (which the
 *     old app never wrote), so these users run fresh onboarding — this merge
 *     brings their check-in history back afterwards, restoring streaks,
 *     calendar, sober counts and achievements. Works on ANY device.
 *
 *  2. **Device**: the new app ships as an UPDATE to the old package id, so the
 *     RN AsyncStorage SQLite (`databases/RKStorage`, table
 *     `catalystLocalStorage`) is still on disk after updating: `last_smoked`
 *     (ISO string) and `journal_entries` (array of {title, content, date}).
 *     The old app kept its program/break state device-local too, but a break
 *     must be re-created through onboarding anyway (matching the X4/§17-Q7
 *     routing), so only the durable user data migrates.
 *
 * Runs best-effort after a successful onboarding submit; guarded by a cached
 * flag so journals never double-append. Merge rules: freshly-seeded days win
 * over old history on date collisions; `lastSmoked` keeps the most RECENT of
 * the two values (the old device may know about a logged smoke the user's
 * onboarding self-estimate missed); journals append.
 */
object OldAppMigrationHandler {

    private const val MIGRATED_KEY = "old_android_app_migrated"

    suspend fun migrateAfterOnboarding(userInfo: UserInfo, program: Program) {
        if (userInfo.getCachedBool(MIGRATED_KEY)) return
        var programChanged = false

        // 1. Server-side check-in history.
        runCatching {
            val userID = SupabaseController.getUserID()?.takeIf { it.isNotEmpty() } ?: return@runCatching
            val serverDays = SupabaseController.fetchUserData(userID)?.day_info.orEmpty()
            serverDays.forEach { (day, info) ->
                if (day !in program.dayInfo) {
                    program.dayInfo[day] = info
                    programChanged = true
                }
            }
        }

        // 2. On-device AsyncStorage leftovers from the RN app.
        val stored = runCatching { readOldAsyncStorage(org.clear30.Clear30Application.instance) }.getOrNull()
        if (stored != null) {
            runCatching {
                stored["last_smoked"]?.let { iso ->
                    runCatching { Instant.parse(iso) }.getOrNull()?.let { old ->
                        if (old > program.lastSmoked) {
                            program.lastSmoked = old
                            programChanged = true
                        }
                    }
                }
            }
            runCatching {
                val journals = stored["journal_entries"]?.let { parseOldJournals(it) }.orEmpty()
                if (journals.isNotEmpty()) {
                    val je = Clear30Store.loadJournalEntries()
                    je.entries.addAll(journals)
                    Clear30Store.save(je)
                }
            }
        }

        if (programChanged) {
            // Recompute the derived state the merged history feeds.
            runCatching { program.updateHealthSetbackDays() }
            Clear30Store.save(program)
            runCatching { SupabaseController.syncProgramState(program) }
            runCatching { AchievementEngine.evaluateNow(userInfo, program) }
        }

        userInfo.setCacheBool(MIGRATED_KEY, true)
        Clear30Store.save(userInfo)
        if (programChanged || stored != null) {
            android.util.Log.i(
                "OldAppMigration",
                "migrated old-app data (serverDays merged=$programChanged, device store=${stored != null})",
            )
        }
    }

    /** Every key/value from the RN AsyncStorage SQLite, or null when absent. */
    private fun readOldAsyncStorage(context: Context): Map<String, String>? {
        val dbFile = context.getDatabasePath("RKStorage")
        if (!dbFile.exists()) return null
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val map = mutableMapOf<String, String>()
                db.rawQuery("SELECT key, value FROM catalystLocalStorage", null).use { c ->
                    while (c.moveToNext()) {
                        val k = c.getString(0) ?: continue
                        val v = c.getString(1) ?: continue
                        map[k] = v
                    }
                }
                map
            }
        }.getOrNull()
    }

    /** Old journal entries: `[{id, title, content, date(ISO)}]`, newest first. */
    private fun parseOldJournals(json: String): List<JournalEntry> = runCatching {
        Json.parseToJsonElement(json).jsonArray.mapNotNull { el ->
            val obj = el.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
            val date = obj["date"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: now()
            JournalEntry(title = title, content = content, date = date)
        }
    }.getOrDefault(emptyList())
}

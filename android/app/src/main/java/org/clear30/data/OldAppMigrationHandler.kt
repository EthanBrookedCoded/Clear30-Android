package org.clear30.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.clear30.data.model.Program
import org.clear30.data.model.UserInfo

/**
 * OldAppMigrationHandler — handles users of the OLD React-Native Clear30 app who
 * update to this (new native) app.
 *
 * We deliberately do NOT back-load their old data. The old RN local schema plus
 * only-partial server state made a faithful restore of check-ins / break /
 * program unreliable, so instead: a user who completed onboarding in the old app
 * (detected from the leftover RN `RKStorage` on disk after the in-place update)
 * gets **free access** (`freeCode`) and a one-time "thanks for testing the beta"
 * welcome. Detection runs at onboarding submit (AllSignUp), which is BEFORE the
 * paywall screen, so free access lands in time to skip it.
 */
object OldAppMigrationHandler {

    /** Cache guard so detection + free-grant only happen once. */
    private const val HANDLED_KEY = "legacy_user_handled"

    /** Set on a detected legacy user; the app shows the welcome popup once and clears it. */
    const val WELCOME_KEY = "legacy_welcome_pending"

    /** freeCode marker that unlocks the app for migrated old-app users. */
    const val FREE_CODE = "legacy_user"

    /**
     * Detect a legacy old-app user and, if so, grant free access + queue the
     * one-time welcome popup. Called from onboarding submit (AllSignUp), before
     * the Payment screen. No data is migrated.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun migrateAfterOnboarding(userInfo: UserInfo, program: Program) {
        if (userInfo.getCachedBool(HANDLED_KEY)) return
        userInfo.setCacheBool(HANDLED_KEY, true)

        val isLegacy = runCatching { hasCompletedOldOnboarding(org.clear30.Clear30Application.instance) }
            .getOrDefault(false)
        if (isLegacy) {
            userInfo.freeCode = FREE_CODE
            userInfo.setCacheBool(WELCOME_KEY, true)
            android.util.Log.i("OldAppMigration", "legacy old-app user detected → free access granted")
        }
        Clear30Store.save(userInfo)
    }

    /**
     * True when the old RN app's AsyncStorage (`databases/RKStorage`) shows a
     * completed onboarding — i.e. this device previously ran the old app. The DB
     * survives the same-package in-place update.
     */
    private fun hasCompletedOldOnboarding(context: Context): Boolean {
        val dbFile = context.getDatabasePath("RKStorage")
        if (!dbFile.exists()) return false
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT value FROM catalystLocalStorage WHERE key = ?",
                    arrayOf("onboarding_completed"),
                ).use { c -> c.moveToNext() && c.getString(0)?.contains("true", ignoreCase = true) == true }
            }
        }.getOrDefault(false)
    }
}

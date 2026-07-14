package org.clear30.data

import org.clear30.data.model.Program
import org.clear30.data.model.ProgramHealthProgress
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getHealthData
import org.clear30.util.adding
import org.clear30.util.withCurrentTime

/**
 * Populates `program.healthProgress` from `library.health_categories` +
 * `health_steps` — the Android analog of the iOS AllTabs health fetch
 * (AllTabs.swift:453-479, `ProgramHealthProgress.updateHealthProgress`).
 *
 * The model mirrors iOS 1:1 (per-category steps with real unlock dates), so
 * this is a straight merge: fetched definitions replace the cached ones while
 * each category's per-user state (startDate/setback/best/lastVisited) is
 * preserved. First-time anchors on Day 1 of the current break, else the
 * program start (with the current time-of-day, like iOS).
 */
object HealthDataHandler {

    /**
     * Fetch + merge when the list is empty or any category is >24h stale
     * (iOS `shouldUpdateHealthData`). Returns true when `program.healthProgress`
     * changed (callers bump their revision counter and re-schedule notifications).
     */
    suspend fun ensureHealthData(userInfo: UserInfo, program: Program): Boolean {
        val shouldUpdate = program.healthProgress.isEmpty() ||
            program.healthProgress.any { it.shouldUpdateHealthData }
        if (!shouldUpdate) return false

        val (categories, steps) = SupabaseController.getHealthData()
        if (categories.isNullOrEmpty() || steps.isNullOrEmpty()) return false

        // Start date (only used for categories fetched for the first time):
        // Day 1 of the Clear30, else the program start date (iOS AllTabs.swift:462-469).
        val startDate = program.currentBreakNotStartSoon?.startDate?.adding(days = 1)
            ?: program.startDate

        program.healthProgress = ProgramHealthProgress.updateHealthProgress(
            existing = program.healthProgress,
            categories = categories,
            steps = steps,
            startDate = startDate.withCurrentTime(),
        ).toMutableList()
        program.updateHealthSetbackDays()

        runCatching { Clear30Store.save(program) }
        return true
    }
}

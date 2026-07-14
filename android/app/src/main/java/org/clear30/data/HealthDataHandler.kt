package org.clear30.data

import org.clear30.data.model.HealthCategory
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramHealthProgress
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getHealthData
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.now

/**
 * Populates `program.healthProgress` from `library.health_categories` +
 * `health_steps` — the Android analog of the iOS AllTabs health fetch
 * (AllTabs.swift:455-479, `ProgramHealthProgress.updateHealthProgress`). Before
 * this, `healthProgress` was never populated on Android: the profile gauges ran
 * on a synthetic ramp and the health timeline was empty.
 *
 * Each backend step maps to one milestone. The Android model is day-based
 * (unlock when `program.currentDay − setbackDays >= unlockedOnDay`), so a
 * step's `days_without_weed` is anchored on the break/program start captured
 * at fetch time, mirroring iOS's per-category `startDate` (break start + 1 day,
 * else program start).
 */
object HealthDataHandler {

    /** UserInfo cache key stamping the last successful fetch. */
    private const val LAST_FETCH_KEY = "healthDataLastFetch"
    private const val STALE_HOURS = 24

    /**
     * Fetch + map when the list is empty or the last fetch is >24h old.
     * Returns true when `program.healthProgress` changed (callers bump their
     * revision counter and re-schedule notifications).
     */
    suspend fun ensureHealthData(userInfo: UserInfo, program: Program): Boolean {
        val lastFetch = userInfo.getCachedDate(LAST_FETCH_KEY, fallback = kotlinx.datetime.Instant.DISTANT_PAST)
        val stale = (now() - lastFetch).inWholeHours >= STALE_HOURS
        if (program.healthProgress.isNotEmpty() && !stale) return false

        val (categories, steps) = SupabaseController.getHealthData()
        if (categories.isNullOrEmpty() || steps.isNullOrEmpty()) return false

        val symbolByCategory = categories.associate { it.name to it.sf_symbol }
        // iOS anchors each category's step dates on the break start + 1 day
        // (else program start); the day-based Android model anchors unlock days
        // the same way relative to program.startDate.
        val anchor = program.currentBreakNotStartSoon?.startDate?.adding(days = 1) ?: program.startDate
        val anchorOffset = program.startDate.daysTo(anchor).coerceAtLeast(0)

        program.healthProgress = steps
            .sortedWith(compareBy({ it.category }, { it.days_without_weed }))
            .map { step ->
                ProgramHealthProgress(
                    id = step.id.toString(),
                    category = HealthCategory.fromName(step.category),
                    title = step.text,
                    description = step.citation.orEmpty(),
                    unlockedOnDay = anchorOffset + step.days_without_weed,
                    icon = symbolByCategory[step.category] ?: "heart.fill",
                    notificationTitle = step.notification_title,
                    notificationBody = step.notification_body,
                )
            }
            .toMutableList()
        // Seed per-milestone setbacks from the live smoked-day count (this is
        // recomputed wholesale on every check-in, so nothing is lost by the remap).
        program.updateHealthSetbackDays()

        userInfo.setCacheDate(LAST_FETCH_KEY, now())
        runCatching {
            Clear30Store.save(program)
            Clear30Store.save(userInfo)
        }
        return true
    }
}

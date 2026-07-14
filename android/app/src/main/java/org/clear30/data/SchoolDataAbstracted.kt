package org.clear30.data

import org.clear30.data.model.Program
import org.clear30.data.model.ProgramClairePrompt
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.ProgramResource
import kotlinx.datetime.Instant
import org.clear30.data.model.SchoolData
import org.clear30.data.model.SchoolDataActivity
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getSchoolData
import org.clear30.util.adding
import org.clear30.util.isSameDay
import org.clear30.util.isSameDayOfWeek
import org.clear30.util.justDay
import org.clear30.util.now
import org.clear30.util.withTimeFrom

/**
 * SchoolData behaviors — ported from SchoolDataAbstracted.swift: fetch/refresh,
 * message generation, and the activity scheduling used by the school library
 * (`getTodayActivities` / `getFutureActivities`). School messages are TRANSIENT —
 * they're regenerated from [UserInfo.schoolData] on every app load (AllTabs) and
 * are never pushed to the backend. iOS `getGradient`/`getBadge` are view-layer
 * (Compose Brush) and live in views/existinguser/support/library/school/.
 */

private const val CLIENT_NAME = "_CLIENTNAME_"

/** Refresh the cached school bundle by its own id (iOS `updateSchoolData`). */
suspend fun updateSchoolData(userInfo: UserInfo) {
    val schoolID = userInfo.schoolData?.school_id ?: return
    SupabaseController.getSchoolData(schoolID)?.let {
        userInfo.schoolData = it
        runCatching { Clear30Store.save(userInfo) }
    }
}

/**
 * Generate the feed messages for this school (iOS `getMessages`): one
 * [ProgramMessage] per [SchoolDataMessage], unlocked on `program.startDate +
 * day days + 10h` (iOS `adding(days:hours:)` — 10 hours on top of the start
 * date's time-of-day, not "10:00 sharp").
 */
fun SchoolData.getMessages(program: Program, userInfo: UserInfo): List<ProgramMessage> =
    messages.map { message ->
        ProgramMessage(
            unlockOn = program.startDate.adding(days = message.day, seconds = 10L * 3600L),
            title = message.title,
            subtitle = message.subtitle,
            message = message.message.replace(CLIENT_NAME, userInfo.name),
        ).apply {
            isSchoolMessage = true
            visited = true
            message.prompts?.let { prompts ->
                clairePrompts = prompts.map { (title, prompt) -> ProgramClairePrompt(title = title, prompt = prompt) }
            }
            journalPrompts = message.journal_prompts
            message.resources?.let { resources ->
                allResources = resources.map { (title, url) -> ProgramResource(title = title, url = url) }
            }
            if (message.meditation_name != null && message.meditation_link != null) {
                meditation = ProgramMeditation(name = message.meditation_name, url = message.meditation_link)
            }
        }
    }

/**
 * The activities occurring on [date] (iOS `getTodayActivities`): weekly repeats
 * whose series started on/before [date] and share its weekday are re-dated onto
 * [date] (keeping their time-of-day); one-offs must land exactly on [date].
 */
fun SchoolData.getTodayActivities(date: Instant = now()): List<SchoolDataActivity> =
    activities.mapNotNull { activity ->
        when {
            activity.repeatsWeekly &&
                activity.date_time < date.adding(days = 1).justDay &&
                activity.date_time.isSameDayOfWeek(date) -> activity.withDate(date)
            activity.date_time.isSameDay(date) -> activity
            else -> null
        }
    }

/**
 * All upcoming activities, sorted by date (iOS `getFutureActivities`): weekly
 * repeats expand into their next 3 occurrences; one-offs are included when they
 * fall after the start of today.
 */
fun SchoolData.getFutureActivities(): List<SchoolDataActivity> {
    val startOfToday = now().justDay
    return activities.flatMap { activity ->
        when {
            activity.repeatsWeekly -> activity.getFutureWeeklyActivities()
            activity.date_time > startOfToday -> listOf(activity)
            else -> emptyList()
        }
    }.sortedBy { it.date_time }
}

/**
 * The next [numActivities] weekly occurrences (iOS `getFutureWeeklyActivities`):
 * advance from `date_time` in whole weeks until on/after the start of today,
 * then emit that occurrence plus one per following week.
 */
fun SchoolDataActivity.getFutureWeeklyActivities(numActivities: Int = 3): List<SchoolDataActivity> {
    val startOfDay = now().justDay
    var nextOccurrence = date_time
    while (nextOccurrence < startOfDay) {
        nextOccurrence = nextOccurrence.adding(days = 7)
    }

    val futureActivities = mutableListOf(withDate(nextOccurrence))
    repeat(numActivities - 1) {
        futureActivities.add(withDate(futureActivities.last().date_time.adding(days = 7)))
    }
    return futureActivities
}

/** A copy of this activity on [date]'s calendar day, keeping the original time-of-day (iOS `withDate`). */
fun SchoolDataActivity.withDate(date: Instant): SchoolDataActivity =
    copy(date_time = date.withTimeFrom(date_time))

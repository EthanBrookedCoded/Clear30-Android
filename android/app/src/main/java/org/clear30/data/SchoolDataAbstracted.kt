package org.clear30.data

import org.clear30.data.model.Program
import org.clear30.data.model.ProgramClairePrompt
import org.clear30.data.model.ProgramMeditation
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.ProgramResource
import org.clear30.data.model.SchoolData
import org.clear30.data.model.UserInfo
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.getSchoolData
import org.clear30.util.adding

/**
 * SchoolData behaviors — ported from SchoolDataAbstracted.swift (the feed slice:
 * fetch/refresh + message generation). School messages are TRANSIENT — they're
 * regenerated from [UserInfo.schoolData] on every app load (AllTabs) and are
 * never pushed to the backend. `getTodayActivities`/`getFutureActivities`/
 * `getBadge`/`getGradient` (the iOS school-library UI) are out of scope for F1.
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

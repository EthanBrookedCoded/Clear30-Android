package org.clear30.data

import kotlinx.datetime.Instant
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.SupabasePackagedMessages
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.SupabaseFunction
import org.clear30.util.adding
import org.clear30.util.justDay

/**
 * ProgramMessageHandler — ported (core path) from ProgramMessageHandler.swift.
 * Fetches the packaged program messages and buckets them into
 * `Program.contentInfo` by their unlock date (startDate + message.day), with the
 * referenced stage attached. The fuller handler (core-program/start-soon/stage
 * variants) layers on from here.
 */
object ProgramMessageHandler {

    /** program_get_messages RPC. */
    suspend fun getMessages(): SupabasePackagedMessages? =
        SupabaseController.callFunction(SupabaseFunction.getMessages, SupabasePackagedMessages::class.java).first

    /**
     * Fetch + apply messages to [program], unlocking from [startDate]. Returns
     * the number of messages added beyond what was already cached — call sites
     * can use this to decide whether to invalidate the UI (or fire a "new
     * content unlocked" notification once the user is signed in).
     *
     * Refresh policy: we always re-call the backend regardless of the existing
     * cache. The packaged feed is small (<100KB typical) and the server may
     * have shipped revised copy that the user should see next time they open
     * the message; clobbering preserves consistency across devices without
     * forcing a manual pull-to-refresh.
     */
    suspend fun fetchAndApply(program: Program, startDate: Instant): Int {
        val packaged = getMessages() ?: return 0
        val stagesById = packaged.stages.associateBy { it.id }

        val byDate = mutableMapOf<PlainDate, ContentInfo>()
        packaged.messages.forEach { msg ->
            val unlockOn = startDate.justDay.adding(days = msg.day)
            val plainDate = PlainDate.from(unlockOn)
            val stage = msg.stage?.let { stagesById[it]?.toStage() }
            val existing = byDate[plainDate]
            byDate[plainDate] = if (existing == null) {
                ContentInfo(messages = listOf(msg.toProgramMessage(unlockOn)), stage = stage)
            } else {
                existing.copy(messages = existing.messages + msg.toProgramMessage(unlockOn), stage = existing.stage ?: stage)
            }
        }

        val before = program.contentInfo.values.sumOf { it.messages.size }
        program.contentInfo.putAll(byDate)
        val after = program.contentInfo.values.sumOf { it.messages.size }
        program.latestUpdate = org.clear30.util.now()
        Clear30Store.save(program)
        return (after - before).coerceAtLeast(0)
    }

    /** Stale check — re-fetch if our latest pull is more than [hours] old. */
    fun isStale(program: Program, hours: Int = 12): Boolean {
        val last = program.latestUpdate ?: return true
        val cutoff = org.clear30.util.now().toEpochMilliseconds() - hours * 60L * 60_000L
        return last.toEpochMilliseconds() < cutoff
    }
}

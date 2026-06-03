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

    /** Fetch + apply messages to [program], unlocking from [startDate]. */
    suspend fun fetchAndApply(program: Program, startDate: Instant) {
        val packaged = getMessages() ?: return
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

        program.contentInfo.putAll(byDate)
        program.latestUpdate = org.clear30.util.now()
        Clear30Store.save(program)
    }
}

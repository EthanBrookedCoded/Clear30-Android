package org.clear30.data

import kotlinx.datetime.Instant
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.PlainDate
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramMessage
import org.clear30.data.model.Stage
import org.clear30.data.model.SupabaseMessage
import org.clear30.data.model.SupabasePackagedMessages
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.SupabaseFunction
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.justDay

/**
 * ProgramMessageHandler — ported (core path) from ProgramMessageHandler.swift.
 * Fetches the packaged program messages and buckets them into
 * `Program.contentInfo` by their unlock date (startDate + message.day), with the
 * referenced stage attached. The fuller handler (core-program/start-soon/stage
 * variants) layers on from here.
 */
object ProgramMessageHandler {

    /** Personalization placeholder the backend embeds in copy (iOS `_CLIENTNAME_`). */
    private const val CLIENT_NAME = "_CLIENTNAME_"

    /** iOS unlocks each day's content at 10:00 local (`adding(hours: 10)`). */
    private const val UNLOCK_HOUR_SECONDS = 10 * 3600L

    /** program_get_messages RPC. */
    suspend fun getMessages(): SupabasePackagedMessages? {
        val (packaged, error) = SupabaseController.callFunction(
            SupabaseFunction.getMessages, SupabasePackagedMessages::class.java,
        )
        if (error != null) {
            // The RPC RAISEs when there's no auth session ('User is not
            // authenticated') or the signed-in account has no assessment ('No
            // assessments found'). Surfacing it explains an empty library/feed.
            android.util.Log.w("ProgramMessageHandler", "program_get_messages failed: ${error.message}")
        }
        return packaged
    }

    /** program_get_latest_update RPC — the server's last content-revision time. */
    suspend fun getLatestUpdate(): Instant? {
        val (date, _) = SupabaseController.callFunction(SupabaseFunction.getLatestUpdate, Instant::class.java)
        return date
    }

    /** Substitute the `_CLIENTNAME_` placeholder in the user-facing copy (iOS restore). */
    private fun SupabaseMessage.withClientName(name: String): SupabaseMessage =
        if (name.isBlank() || (CLIENT_NAME !in title && CLIENT_NAME !in subtitle && CLIENT_NAME !in body)) {
            this
        } else {
            copy(
                title = title.replace(CLIENT_NAME, name),
                subtitle = subtitle.replace(CLIENT_NAME, name),
                body = body.replace(CLIENT_NAME, name),
            )
        }

    /** Add a message to the date bucket, attaching [stage] if the bucket has none yet. */
    private fun MutableMap<PlainDate, ContentInfo>.add(date: PlainDate, message: ProgramMessage, stage: Stage?) {
        val existing = this[date]
        this[date] = if (existing == null) {
            ContentInfo(messages = listOf(message), stage = stage)
        } else {
            existing.copy(messages = existing.messages + message, stage = existing.stage ?: stage)
        }
    }

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
    suspend fun fetchAndApply(program: Program, startDate: Instant, clientName: String = ""): Int {
        val packaged = getMessages() ?: return 0
        val stagesById = packaged.stages.associateBy { it.id }

        val byDate = mutableMapOf<PlainDate, ContentInfo>()
        packaged.messages.forEach { raw ->
            val msg = raw.withClientName(clientName)
            // Unlock at 10:00 on (startDate + day); add a per-message second offset so
            // messages sharing a day keep their delivered order (iOS handlePackagedMessages).
            val base = startDate.justDay.adding(days = msg.day, seconds = UNLOCK_HOUR_SECONDS)
            val plainDate = PlainDate.from(base)
            val offset = byDate[plainDate]?.messages?.size ?: 0
            val unlockOn = base.adding(seconds = offset.toLong())
            val stage = msg.stage?.let { stagesById[it]?.toStage() }
            byDate.add(plainDate, msg.toProgramMessage(unlockOn), stage)
        }

        val before = program.contentInfo.values.sumOf { it.messages.size }
        program.contentInfo.putAll(byDate)
        val after = program.contentInfo.values.sumOf { it.messages.size }
        program.latestUpdate = org.clear30.util.now()
        Clear30Store.save(program)
        return (after - before).coerceAtLeast(0)
    }

    /**
     * The scheduler (iOS `handlePackagedMessages`). Maps each library topic's
     * relative `day` offset onto a calendar date anchored at [startDate] Day 0
     * (10:00 local), returning a fresh `[PlainDate: ContentInfo]` map WITHOUT
     * touching [program].
     *
     * [removePrefix] drops the first N library topics AND shifts the rest back N
     * days (used when resuming/switching the Life stream so already-seen topics
     * aren't re-added and the next new topic lands on the right day). Topics that
     * share a calendar day keep their delivered order via +N-second offsets.
     */
    fun schedule(
        startDate: Instant,
        packaged: SupabasePackagedMessages,
        removePrefix: Int = 0,
        clientName: String = "",
    ): MutableMap<PlainDate, ContentInfo> {
        val stagesById = packaged.stages.associateBy { it.id }
        val messages = if (removePrefix <= packaged.messages.size) packaged.messages.drop(removePrefix) else emptyList()
        val anchor = startDate.justDay.adding(seconds = UNLOCK_HOUR_SECONDS) // Day 0 @ 10:00
        val byDate = mutableMapOf<PlainDate, ContentInfo>()
        messages.forEach { raw ->
            val msg = raw.withClientName(clientName)
            val base = anchor.adding(days = msg.day - removePrefix)
            val plainDate = PlainDate.from(base)
            val offset = byDate[plainDate]?.messages?.size ?: 0
            val unlockOn = base.adding(seconds = offset.toLong())
            val stage = msg.stage?.let { stagesById[it]?.toStage() }
            byDate.add(plainDate, msg.toProgramMessage(unlockOn), stage)
        }
        return byDate
    }

    /**
     * The start-soon / preparation scheduler (iOS `handlePackagedMessagesStartSoon`).
     * Walks BACKWARD from [endDate] (the last day *in* the start-soon window)
     * toward [startDate] (today). The library's `start_soon_messages[].day` means
     * "days before the main break's Day 1", and the loop starts at 2 because "1 day
     * before" is Day 0 — owned by the main Clear30 program, not start-soon.
     */
    fun scheduleStartSoon(
        startDate: Instant,
        endDate: Instant,
        packaged: SupabasePackagedMessages,
        clientName: String = "",
    ): MutableMap<PlainDate, ContentInfo> {
        val startSoon = packaged.start_soon_messages ?: return mutableMapOf()
        val stagesById = packaged.stages.associateBy { it.id }
        val byDate = mutableMapOf<PlainDate, ContentInfo>()
        val dayCount = startDate.daysTo(endDate)
        for (day in 2..(dayCount + 2)) {
            val dayDate = endDate.adding(days = -day + 2)
            startSoon.filter { it.day == day }.forEach { raw ->
                val msg = raw.withClientName(clientName)
                val base = dayDate.justDay.adding(seconds = UNLOCK_HOUR_SECONDS)
                val plainDate = PlainDate.from(base)
                val offset = byDate[plainDate]?.messages?.size ?: 0
                val unlockOn = base.adding(seconds = offset.toLong())
                val stage = msg.stage?.let { stagesById[it]?.toStage() }
                byDate.add(plainDate, msg.toProgramMessage(unlockOn), stage)
            }
        }
        // Day-0 override: a `day == 0` start-soon topic occupies Day 0 itself, but
        // only if the main program hasn't already claimed that bucket.
        val day0Plain = PlainDate.from(startDate)
        if (day0Plain !in byDate) {
            startSoon.firstOrNull { it.day == 0 }?.let { raw ->
                val msg = raw.withClientName(clientName)
                val unlockOn = startDate.justDay.adding(seconds = UNLOCK_HOUR_SECONDS)
                val stage = msg.stage?.let { stagesById[it]?.toStage() }
                byDate[day0Plain] = ContentInfo(messages = listOf(msg.toProgramMessage(unlockOn)), stage = stage)
            }
        }
        return byDate
    }

    /**
     * Seed the built-in [DailyLessons] into [program] as a FALLBACK — one per
     * program day, unlocking at 10:00 like the backend feed, and ONLY for days
     * the backend hasn't already filled. Call this when `program_get_messages`
     * returns nothing (local dev, or pre-assessment) so the Today feed always
     * has daily content. Returns the number of lessons added.
     */
    suspend fun applyBuiltInLessons(program: Program, startDate: Instant, clientName: String = ""): Int {
        val name = clientName.ifBlank { "friend" }
        var added = 0
        DailyLessons.all.forEach { lesson ->
            val base = startDate.justDay.adding(days = lesson.day, seconds = UNLOCK_HOUR_SECONDS)
            val plainDate = PlainDate.from(base)
            // Don't clobber real backend content for a day that already has it.
            if ((program.contentInfo[plainDate]?.messages?.size ?: 0) > 0) return@forEach
            val msg = ProgramMessage.create(
                unlockOn = base,
                title = lesson.title.replace("{name}", name),
                subtitle = lesson.subtitle,
                message = lesson.body.replace("{name}", name),
            )
            val existing = program.contentInfo[plainDate]
            program.contentInfo[plainDate] =
                if (existing == null) ContentInfo(messages = listOf(msg), stage = null)
                else existing.copy(messages = existing.messages + msg)
            added++
        }
        if (added > 0) {
            program.latestUpdate = org.clear30.util.now()
            Clear30Store.save(program)
        }
        return added
    }

    /** Stale check — re-fetch if our latest pull is more than [hours] old. */
    fun isStale(program: Program, hours: Int = 12): Boolean {
        val last = program.latestUpdate ?: return true
        val cutoff = org.clear30.util.now().toEpochMilliseconds() - hours * 60L * 60_000L
        return last.toEpochMilliseconds() < cutoff
    }
}

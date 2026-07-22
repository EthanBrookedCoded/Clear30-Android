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
 * ProgramMessageHandler — ported from ProgramMessageHandler.swift. Two jobs:
 *
 *  1. SCHEDULING ([schedule] / [scheduleStartSoon]): map the packaged library
 *     topics onto calendar dates in `Program.contentInfo`. Only the break
 *     mechanics in [ProgramTimelineHandler] call these — content is scheduled
 *     when a program starts or a break moves, never on a routine refresh.
 *  2. REFRESH ([updateMessages], via [ensureContent] from the tabs): when the
 *     server content revision changes, copy revised message fields in place by
 *     `messageID` — buckets/unlockOn/progress are never rebuilt.
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
     * Content refresh (iOS `updateMessages`, run on every Program load): compare
     * the server's content revision (`program_get_latest_update`) with the one we
     * last pulled; when it changed, re-fetch the packaged messages and copy the
     * revised fields INTO the existing [ProgramMessage] objects by `messageID`.
     * The `contentInfo` buckets themselves — dates, `unlockOn`, and per-day
     * `progress` — are never touched, so completed-day progress always survives
     * a refresh. Returns true when anything was updated.
     */
    suspend fun updateMessages(program: Program, clientName: String = ""): Boolean {
        val updatedAt = getLatestUpdate() ?: return false
        if (program.latestUpdate == updatedAt) return false
        val packaged = getMessages() ?: return false
        val byID = packaged.messages.associateBy { it.id }

        program.contentInfo.values.flatMap { it.messages }.forEach { msg ->
            val new = byID[msg.messageID]?.withClientName(clientName) ?: return@forEach
            msg.title = new.title
            msg.subtitle = new.subtitle
            msg.message = new.body
            msg.meditation = new.meditation
            msg.pageInfo = (new.page_info ?: emptyList()).map { listOf(it.title, it.body) }
            msg.clairePrompts = new.claire_prompts_json ?: new.claire_prompts ?: emptyList()
            msg.allResources = new.resources ?: emptyList()
            msg.journalPrompts = new.journal_prompts
            msg.questionID = new.question_id
            msg.questionResponse = new.question_response
            msg.thumbnailURL = new.thumbnail_url
            msg.videoURL = new.video_url
            msg.instagramVideos = new.instagram_videos
            msg.carouselImages = new.carousel_images
            msg.memberPerks = new.member_perks
        }

        program.latestUpdate = updatedAt
        Clear30Store.save(program)
        return true
    }

    /**
     * Tab-open content hook. The timeline is normally scheduled once, at
     * onboarding/new-break time (ProgramTimelineHandler.start/newClear30 →
     * [schedule]) — so the steady-state job here is just the in-place copy
     * refresh ([updateMessages]). The empty case is a REPAIR path only (dev
     * installs predating the scheduler wiring, or a signup whose content fetch
     * failed): schedule the full feed from [startDate] with the faithful
     * scheduler. Returns true when the UI should re-derive the feed.
     */
    suspend fun ensureContent(program: Program, startDate: Instant, clientName: String = ""): Boolean {
        if (program.contentInfo.values.any { it.messages.isNotEmpty() }) {
            return updateMessages(program, clientName)
        }
        val packaged = getMessages() ?: return false
        val content = schedule(startDate, packaged, clientName = clientName)
        if (content.isEmpty()) return false
        content.forEach { (date, info) ->
            // Keep anything already there for a day (e.g. an empty restored
            // bucket carrying progress) from being clobbered wholesale.
            val existing = program.contentInfo[date]
            program.contentInfo[date] =
                if (existing == null) info
                else existing.copy(messages = existing.messages + info.messages, stage = existing.stage ?: info.stage)
        }
        program.latestUpdate = getLatestUpdate()
        Clear30Store.save(program)
        return true
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
        val messages = (if (removePrefix <= packaged.messages.size) packaged.messages.drop(removePrefix) else emptyList())
            // Assessment-response messages (carrying a question_id) render AFTER
            // the day's core message (Thatcher, 2026-07-13). The stable sort keeps
            // delivered order otherwise; per-day buckets are keyed by `day`, so
            // only within-day order changes — the +N-second unlockOn offsets then
            // encode it for the feed, the viewer, and the pushed message_ids.
            .sortedBy { it.question_id != null }
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
        // Day-0 override (iOS ProgramMessageHandler.swift:533-537): a `day == 0`
        // start-soon topic REPLACES whatever the backward walk put on the
        // bridge's first day — and ONLY when that bucket exists (a missing
        // bucket makes iOS's optional-chained assignment a no-op, dropping the
        // day-0 topic). The previous inverted logic added it only when the
        // bucket was missing and never replaced.
        val day0Plain = PlainDate.from(startDate)
        byDate[day0Plain]?.let { existing ->
            startSoon.firstOrNull { it.day == 0 }?.let { raw ->
                val msg = raw.withClientName(clientName)
                val unlockOn = startDate.justDay.adding(seconds = UNLOCK_HOUR_SECONDS)
                byDate[day0Plain] = existing.copy(messages = listOf(msg.toProgramMessage(unlockOn)))
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
}

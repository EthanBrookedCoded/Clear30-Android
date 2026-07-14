package org.clear30.data

import kotlinx.datetime.Instant
import org.clear30.data.model.AssessmentQuestionID
import org.clear30.data.model.CheckInMethod
import org.clear30.data.model.ContentInfo
import org.clear30.data.model.PlainDate
import org.clear30.data.model.PopInType
import org.clear30.data.model.Program
import org.clear30.data.model.ProgramAssessmentResponse
import org.clear30.data.model.ProgramBreak
import org.clear30.data.model.ProgramBreakType
import org.clear30.data.model.ProgramDayInfo
import org.clear30.data.model.current
import org.clear30.data.model.sorted
import org.clear30.data.supabase.SupabaseController
import org.clear30.data.supabase.submitAssessment
import org.clear30.data.supabase.syncProgramState
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.justDay
import org.clear30.util.now

/**
 * ProgramTimelineHandler — ported from ProgramMessageHandler.swift (start /
 * newClear30 / switchCore / resumeCore), ProgramTimelineHandler.swift
 * (day0StartNow / restartBreak / endBreak / adjustBreakTime / handleForwardToFutureMove)
 * and ProgramBreakAbstracted.swift (handleBreaks).
 *
 * These are the break MECHANICS: every one is a **date-shift of the resolved
 * `content_info` map** plus a change to a break's `start_date`/`end_date_override`,
 * then a local save and a push to Supabase — so a returning user restores the
 * identical timeline. The scheduling math (library `day` offset → calendar date)
 * lives in [ProgramMessageHandler.schedule] / [ProgramMessageHandler.scheduleStartSoon].
 *
 * Peripheral iOS calls with no Android port yet are intentionally omitted and
 * called out inline: `NotificationHandler.*` (notifications — TODO), `WidgetCenter`
 * (widget refresh), `AlertHandler` (errors are returned as strings instead), and
 * `ExperimentController` (unused here). Health-ring resets collapse to
 * [Program.updateHealthSetbackDays] — Android has no per-entry health start date,
 * so the timeline recomputes from `program.startDate` on load (see MESSAGE_TIMELINE
 * §6.1). `CheckInLogger.resetLastSmoked` collapses to setting `program.lastSmoked`.
 */
object ProgramTimelineHandler {

    // ── Content-map merge helpers (Swift `Dictionary.merge(_:uniquingKeysWith:)`) ──

    /** Keep the EXISTING value on a key collision (Swift `{ current, _ in current }`). */
    private fun MutableMap<PlainDate, ContentInfo>.mergeKeepCurrent(other: Map<PlainDate, ContentInfo>) {
        other.forEach { (k, v) -> if (k !in this) this[k] = v }
    }

    /** Take the NEW value on a key collision (Swift `{ _, new in new }`). */
    private fun MutableMap<PlainDate, ContentInfo>.mergeNewWins(other: Map<PlainDate, ContentInfo>) {
        other.forEach { (k, v) -> this[k] = v }
    }

    /**
     * Start-soon merge (Swift `{ current, new in new.messages += current.messages; new }`):
     * the incoming (start-soon) messages sort FIRST, then the day's existing
     * messages are appended. Paired with the −5s nudge, start-soon topics render
     * before same-day main content.
     */
    private fun MutableMap<PlainDate, ContentInfo>.mergeStartSoon(other: Map<PlainDate, ContentInfo>) {
        other.forEach { (k, v) ->
            val current = this[k]
            this[k] = if (current == null) v
            else v.copy(messages = v.messages + current.messages, stage = v.stage ?: current.stage)
        }
    }

    /**
     * Reset scroll progress for future days (Swift `progress = nil`). iOS is
     * inconsistent on purpose: `restartBreak` resets from today INCLUSIVE
     * (`>= today`, ProgramTimelineHandler.swift:158) — today is the new Day 1 and
     * must render fresh — while `adjustBreakTime` resets strictly after today
     * (`> today`, :309).
     */
    private fun MutableMap<PlainDate, ContentInfo>.resetFutureProgress(today: PlainDate, includeToday: Boolean = false) {
        keys.filter { if (includeToday) it >= today else it > today }
            .forEach { this[it] = this[it]!!.copy(progress = null) }
    }

    /**
     * Shift every bucket whose key matches [inRange] by [days]: mutate each
     * message's `unlockOn` in place (reference type, like iOS) and re-key the
     * bucket by +[days]. Buckets outside [inRange] pass through unchanged.
     */
    private fun shiftContent(
        content: Map<PlainDate, ContentInfo>,
        days: Int,
        inRange: (PlainDate) -> Boolean,
    ): MutableMap<PlainDate, ContentInfo> {
        val result = content.filterKeys { !inRange(it) }.toMutableMap()
        content.filterKeys(inRange).forEach { (date, info) ->
            info.messages.forEach { it.unlockOn = it.unlockOn.adding(days = days) }
            result[date.adding(days = days)] = info
        }
        return result
    }

    /** Persist locally, then push the whole program state to Supabase (iOS `pushProgramData`). */
    private suspend fun finish(program: Program) {
        Clear30Store.save(program)
        SupabaseController.syncProgramState(program)
        // Timeline mutations move the health milestones — reschedule their
        // notifications (iOS ProgramTimelineHandler.swift:96,463). Gated on
        // notificationSettings inside, so the pre-permission signup path no-ops.
        runCatching {
            NotificationHandler.scheduleHealthNotifications(Clear30Store.loadUserInfo(), program)
        }
    }

    // ─────────────────────────── Break creation ───────────────────────────

    /**
     * Build the main (+ optional start-soon) break for a chosen [breakType] and
     * assessment answers (iOS `handleBreaks`). In the live onboarding no start-date
     * is asked, so Day 0 = today and there is no start-soon bridge.
     */
    fun handleBreaks(
        breakType: ProgramBreakType,
        assessmentResponses: List<ProgramAssessmentResponse>,
        nameOverride: String? = null,
    ): Pair<ProgramBreak?, ProgramBreak> {
        val today = now().justDay
        // A START_DATE answer (the new-break assessment's date picker, iOS
        // ProgramBreakAbstracted.swift:20-26) makes Day 0 the day BEFORE the
        // chosen start; the onboarding flow asks no start-date → Day 0 = today.
        val startDateAnswer = assessmentResponses
            .firstOrNull { it.question.strippedPrompt == AssessmentQuestionID.START_DATE.raw }
            ?.question?.options?.firstOrNull()
            ?.let { runCatching { PlainDate.parse(it) }.getOrNull() }
        val mainBreakDay0 = startDateAnswer?.adding(days = -1)?.dateObject ?: today
        val breakName = nameOverride ?: breakType.typeName
        val mainBreak = ProgramBreak.create(breakName, breakType, mainBreakDay0, assessmentResponses)

        if (today.daysTo(mainBreakDay0) <= 0) return null to mainBreak

        val startSoonEnd = mainBreakDay0.adding(days = -1)
        val startSoon = ProgramBreak.create(
            "$breakName ${ProgramBreakType.CLEAR30_START_SOON.typeName}",
            ProgramBreakType.CLEAR30_START_SOON, today, assessmentResponses,
        )
        startSoon.overrideEndDate(startSoonEnd)
        return startSoon to mainBreak
    }

    // ─────────────────────────── start() ───────────────────────────

    /**
     * Initial program creation on sign-up (iOS `Program.start`). Seeds sober-day
     * history + program start, then routes to the Clear30 or Life track. Returns
     * an error message on failure, null on success.
     */
    suspend fun start(
        program: Program,
        mainBreak: ProgramBreak?,
        startSoonBreak: ProgramBreak?,
        lastSmoked: Instant,
        checkInMethod: CheckInMethod? = null,
        clientName: String = "",
    ): String? {
        setupDaysSoberAndLastSmoked(program, lastSmoked, mainBreak, startSoonBreak)
        program.latestCheckInMethod = checkInMethod
        // Register the break(s) so the timeline windows line up with the content.
        if (mainBreak != null && program.breaks.none { it === mainBreak }) program.breaks.add(mainBreak)
        if (startSoonBreak != null && program.breaks.none { it === startSoonBreak }) program.breaks.add(startSoonBreak)
        return if (mainBreak != null) startWithClear30(program, mainBreak, startSoonBreak, clientName)
        else startWithCore(program, clientName)
    }

    /**
     * Back-calculate the program start from days-sober and seed `day_info` sober
     * flags (iOS `setupDaysSoberAndLastSmoked`). The program can predate the break
     * when the user already had sober days.
     */
    private fun setupDaysSoberAndLastSmoked(
        program: Program,
        lastSmoked: Instant,
        mainBreak: ProgramBreak?,
        startSoonBreak: ProgramBreak?,
    ) {
        val daysSober = lastSmoked.daysTo(now())
        val programStart = startSoonBreak?.startDate ?: mainBreak?.startDate ?: now().justDay
        val startDate = programStart.adding(days = -daysSober)
        program.startDate = startDate
        program.lastSmoked = lastSmoked

        val newDayInfo = mutableMapOf<PlainDate, ProgramDayInfo>()
        newDayInfo[PlainDate.from(startDate)] = ProgramDayInfo.withSober(sober = false)
        if (daysSober > 0) {
            for (day in 0 until daysSober) {
                newDayInfo[PlainDate.from(startDate.adding(days = day + 1))] = ProgramDayInfo.withSober(sober = true)
            }
        }
        newDayInfo.remove(PlainDate.from(now())) // clear today (iOS sets it nil)
        program.dayInfo = newDayInfo
    }

    private suspend fun startWithClear30(
        program: Program,
        mainBreak: ProgramBreak,
        startSoonBreak: ProgramBreak?,
        clientName: String,
    ): String? {
        val packaged = ProgramMessageHandler.getMessages() ?: return "Could not get Clear30 messages."
        val content = ProgramMessageHandler.schedule(mainBreak.startDate, packaged, clientName = clientName)
        if (startSoonBreak != null) {
            val startSoon = ProgramMessageHandler.scheduleStartSoon(
                startSoonBreak.startDate, startSoonBreak.endDate.adding(days = -1), packaged, clientName,
            )
            content.mergeKeepCurrent(startSoon) // main wins any shared day
        }
        program.contentInfo = content
        finish(program)
        return null
    }

    private suspend fun startWithCore(program: Program, clientName: String): String? {
        val packaged = ProgramMessageHandler.getMessages() ?: return "Could not get core messages."
        val messages = ProgramMessageHandler.schedule(now(), packaged, clientName = clientName)
        val content = program.contentInfo.toMutableMap()
        content.mergeKeepCurrent(messages) // don't clobber anything already scheduled
        program.contentInfo = content
        finish(program)
        return null
    }

    // ─────────────────────────── newClear30() ───────────────────────────

    /**
     * Start a brand-new Clear30 from Life (iOS `newClear30`) — the Profile
     * "New Break" flow. Merges optional start-soon content (−5s nudge), removes
     * existing content from the new Day 0 (or today) forward, and merges the new
     * break's topics on top.
     */
    suspend fun newClear30(
        program: Program,
        mainBreak: ProgramBreak,
        startSoonBreak: ProgramBreak?,
        clientName: String = "",
    ): String? {
        if (program.breaks.none { it === mainBreak }) program.breaks.add(mainBreak)
        if (startSoonBreak != null && program.breaks.none { it === startSoonBreak }) program.breaks.add(startSoonBreak)

        val todayPlain = PlainDate.from(now())
        val packaged = ProgramMessageHandler.getMessages() ?: return "Could not get Clear30 messages."
        val newMessages = ProgramMessageHandler.schedule(mainBreak.startDate, packaged, clientName = clientName)
        var content = program.contentInfo.toMutableMap()

        if (startSoonBreak != null) {
            val startSoon = ProgramMessageHandler.scheduleStartSoon(
                startSoonBreak.startDate, startSoonBreak.endDate.adding(days = -1), packaged, clientName,
            )
            startSoon.values.forEach { info -> info.messages.forEach { it.unlockOn = it.unlockOn.adding(seconds = -5L) } }
            content.mergeStartSoon(startSoon)
            content = content.filterKeys { it < PlainDate.from(mainBreak.startDate) }.toMutableMap()
            content.mergeNewWins(newMessages)
        } else {
            content = content.filterKeys { it < todayPlain }.toMutableMap()
            content.mergeNewWins(newMessages)
        }

        program.contentInfo = content
        program.updateHealthSetbackDays() // iOS resetHealthProgress(on: breakDay1)
        finish(program)
        return null
    }

    // ─────────────────────────── Life (core) mode ───────────────────────────

    /**
     * Re-fetch the Life stream for a (possibly toggled) moderation mode and splice
     * it in from [startOn] forward (iOS `switchCore`). [newModeration] is the target
     * mode — the mode-toggle caller passes `!coreModeration`; `endBreak` passes true.
     */
    suspend fun switchCore(
        program: Program,
        startOn: Instant = now().justDay.adding(days = 1),
        newModeration: Boolean = !program.coreModeration,
        clientName: String = "",
    ): String? {
        // Submit the `life-short` assessment carrying LO-Use-State FIRST (iOS
        // `updateLifeProgram`) — program_get_messages gates the topic set by the
        // user's latest assessment, so the re-pull below returns the new mode's
        // Life topics only after this lands.
        val modAbsResponse = ProgramAssessmentResponse(
            question = org.clear30.data.model.AssessmentQuestions.modAbs,
            responses = listOf(if (newModeration) 1 else 0),
        )
        val (_, submitError) = SupabaseController.submitAssessment(
            assessmentID = org.clear30.data.model.AssessmentType.LifeShort.string,
            responses = listOf(modAbsResponse),
        )
        if (submitError != null) return submitError.message

        val numExisting = program.getCoreContentInfo().current.values.count { it.messages.isNotEmpty() }
        val packaged = ProgramMessageHandler.getMessages() ?: return "Could not get core messages."
        val messages = ProgramMessageHandler.schedule(startOn, packaged, removePrefix = numExisting, clientName = clientName)
        val content = program.contentInfo.filterKeys { it < PlainDate.from(startOn) }.toMutableMap()
        content.mergeNewWins(messages)
        program.contentInfo = content
        program.coreModeration = newModeration
        finish(program)
        return null
    }

    /**
     * Re-add Life topics after a break WITHOUT changing the mode (iOS `resumeCore`).
     * Uses `removePrefix` so already-seen Life topics aren't duplicated.
     */
    suspend fun resumeCore(program: Program, clientName: String = ""): String? {
        val numExisting = program.getCoreContentInfo().current.values.count { it.messages.isNotEmpty() }
        val startOn = maxOf(program.lastBreak?.endDate ?: now(), now())
        val packaged = ProgramMessageHandler.getMessages() ?: return "Could not get core messages."
        val messages = ProgramMessageHandler.schedule(startOn, packaged, removePrefix = numExisting, clientName = clientName)
        val content = program.contentInfo.toMutableMap()
        content.mergeNewWins(messages)
        program.contentInfo = content
        finish(program)
        return null
    }

    // ─────────────────────────── Timeline mutators ───────────────────────────

    /**
     * "Start my upcoming break now" from the Day-0 countdown (iOS `day0StartNow`).
     * Shifts content backward so Day 0 = today and collapses the start-soon bridge.
     */
    suspend fun day0StartNow(program: Program) {
        val today = now().justDay
        val todayPlain = PlainDate.from(today)
        val lastBreak = program.lastBreak
        val daysToShift = today.daysTo(lastBreak?.startDate ?: today) + 1

        var content = program.contentInfo.toMutableMap()
        // Drop the buckets the shift would overwrite.
        if (daysToShift >= 1) for (d in 1..daysToShift) content.remove(todayPlain.adding(days = -d))
        // Shift everything from today forward backward by daysToShift.
        val result = content.filterKeys { it < todayPlain }.toMutableMap()
        content.filterKeys { it >= todayPlain }.forEach { (key, info) ->
            info.messages.forEach { it.unlockOn = it.unlockOn.adding(days = -daysToShift) }
            result[key.adding(days = -daysToShift)] = info
        }
        program.contentInfo = result

        var mainBreakDay1: Instant? = null
        if (lastBreak != null) {
            mainBreakDay1 = lastBreak.startDate
            lastBreak.startDate = lastBreak.startDate.adding(days = -daysToShift)
        }
        // Collapse the associated start-soon bridge.
        val startSoon = program.breaks.filter { it.isStartSoon }.sorted.lastOrNull()
        if (mainBreakDay1 != null && startSoon != null && startSoon.endDate.justDay == mainBreakDay1.justDay) {
            startSoon.startDate = startSoon.startDate.adding(days = -daysToShift)
            startSoon.overrideEndDate(startSoon.endDate.adding(days = -daysToShift - 1))
        }
        if (program.breaks.size == 1) program.lastSmoked = now()
        program.breaks.minByOrNull { it.startDate }?.let { program.startDate = minOf(it.startDate, program.startDate) }
        program.updateHealthSetbackDays()
        finish(program)
    }

    /**
     * "I slipped — reset me to Day 1 = today" (iOS `restartBreak`). Shifts the
     * break's content forward so today becomes Day 1, resets future progress and
     * money, marks today's pop-in, and resets last-smoked.
     */
    suspend fun restartBreak(program: Program, breakToRestart: ProgramBreak? = null) {
        val target = breakToRestart ?: program.currentBreak ?: return
        val today = now().justDay
        val nowPlain = PlainDate.from(today)
        val startDate = target.startDate
        val startPlain = PlainDate.from(startDate)
        val endPlain = PlainDate.from(target.endDate)
        val daysFromStart = startDate.daysTo(today) - 1

        val updated = shiftContent(program.contentInfo, daysFromStart) { startPlain <= it && it < endPlain }
        updated.resetFutureProgress(nowPlain, includeToday = true)
        program.contentInfo = updated

        target.startDate = startDate.adding(days = daysFromStart)
        target.moneySavedAdjustment = 0
        program.updateHealthSetbackDays()

        val info = program.dayInfo[nowPlain]
        program.dayInfo[nowPlain] = (info ?: ProgramDayInfo()).copy(popInType = PopInType.RestartedBreak)
        program.lastSmoked = now()
        finish(program)
    }

    /**
     * End the current break (iOS `endBreak`): set its end to yesterday, delete
     * content from today forward, and drop into the WEED-FREE Life program
     * (decision §17-Q5). iOS sets `coreModeration = true` and then `switchCore`
     * toggles it — the net effect is `coreModeration == false` with
     * `LO-Use-State = 0` submitted; we pass the net target directly.
     */
    suspend fun endBreak(program: Program): String? {
        val current = program.currentBreak ?: return null
        val today = now().justDay
        val todayPlain = PlainDate.from(today)
        current.overrideEndDate(today.adding(days = -1)) // last day in = yesterday
        program.contentInfo = program.contentInfo.filterKeys { it < todayPlain }.toMutableMap()
        return switchCore(program, startOn = today, newModeration = false)
    }

    /**
     * General ± N-day mover (iOS `adjustBreakTime`) — the Tutorial start-date picker
     * and the Profile "Change start date". Moving forward into the future delegates
     * to [handleForwardToFutureMove] (which builds a start-soon bridge). Otherwise
     * shifts content + breaks, resets future progress, and truncates overlapping
     * earlier breaks on a backward move.
     */
    suspend fun adjustBreakTime(program: Program, days: Int) {
        val current = program.currentBreak ?: return
        val today = now().justDay
        val todayPlain = PlainDate.from(today)
        val originalStart = current.startDate.justDay
        val originalStartPlain = PlainDate.from(originalStart)
        val newStart = current.startDate.adding(days = days)
        val newEnd = current.endDate.adding(days = days)

        val isForwardToFuture = days > 0 && PlainDate.from(newStart) > PlainDate.from(today.adding(days = 1))
        if (isForwardToFuture) {
            handleForwardToFutureMove(program, current, newStart, days)
            return
        }

        val updated = shiftContent(program.contentInfo, days) { it >= originalStartPlain }
        updated.resetFutureProgress(todayPlain)
        program.contentInfo = updated

        // Move every break at/after the original start.
        program.breaks.filter { it.startDate >= originalStart }.forEach { b ->
            b.startDate = b.startDate.adding(days = days)
            b.endDateOverride?.let { b.overrideEndDate(it.adding(days = days - 1)) }
        }

        // Backward move: truncate any earlier break we now overlap.
        if (days < 0) {
            program.breaks.filter { it !== current }.forEach { other ->
                if (other.startDate < newEnd && other.endDate > newStart) {
                    other.overrideEndDate(newStart.adding(days = -1))
                }
            }
        }

        // Pull the program start back if a break now precedes it.
        program.breaks.minByOrNull { it.startDate }?.let { earliest ->
            if (earliest.startDate.justDay < program.startDate.justDay) program.startDate = earliest.startDate.justDay
        }
        program.updateHealthSetbackDays()
        finish(program)
    }

    /** Build a start-soon bridge (today … newStart−1) and shift the main break into the future. */
    private suspend fun handleForwardToFutureMove(
        program: Program,
        current: ProgramBreak,
        newStart: Instant,
        days: Int,
    ) {
        val today = now().justDay
        val todayPlain = PlainDate.from(today)
        val startSoonLastDay = newStart.adding(days = -1).justDay
        val packaged = ProgramMessageHandler.getMessages() ?: return
        val startSoonContent = ProgramMessageHandler.scheduleStartSoon(today, startSoonLastDay, packaged)

        // Replace any old start-soon break that abutted this break's old start.
        program.breaks.firstOrNull { it.isStartSoon && it.endDate.justDay == current.startDate.justDay }
            ?.let { program.breaks.remove(it) }
        val startSoon = ProgramBreak.create(
            "${current.name} ${ProgramBreakType.CLEAR30_START_SOON.typeName}",
            ProgramBreakType.CLEAR30_START_SOON, today, current.assessmentResponses,
        )
        startSoon.overrideEndDate(startSoonLastDay)
        program.breaks.add(startSoon)

        reorganizeContentForForwardMove(program, startSoonContent, current, todayPlain, days)

        current.startDate = newStart
        current.moneySavedAdjustment = 0
        program.updateHealthSetbackDays()
        program.lastSmoked = current.startDate.adding(days = 1) // iOS resetLastSmoked(setTo: breakDay1)
        finish(program)
    }

    /** Merge start-soon content (−5s nudge) and shift the main break forward (iOS `reorganizeContentForForwardMove`). */
    private fun reorganizeContentForForwardMove(
        program: Program,
        startSoonContent: Map<PlainDate, ContentInfo>,
        current: ProgramBreak,
        todayPlain: PlainDate,
        days: Int,
    ) {
        val originalStartPlain = PlainDate.from(current.startDate.justDay)
        val merged = program.contentInfo.filterKeys { it < originalStartPlain }.toMutableMap()
        val future = program.contentInfo.filterKeys { it >= originalStartPlain }

        startSoonContent.values.forEach { info -> info.messages.forEach { it.unlockOn = it.unlockOn.adding(seconds = -5L) } }
        merged.mergeStartSoon(startSoonContent)

        future.forEach { (date, info) ->
            info.messages.forEach { it.unlockOn = it.unlockOn.adding(days = days) }
            merged[date.adding(days = days)] = info
        }
        merged.resetFutureProgress(todayPlain)
        program.contentInfo = merged
    }
}

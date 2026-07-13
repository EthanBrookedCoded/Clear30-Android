package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.justDay
import org.clear30.util.now
import kotlin.math.ceil

/**
 * Program — ported from Program.swift (`@Model`) and its extensions in
 * ProgramBreak.swift.
 *
 * SwiftData's `@Transient` contentInfo/dayInfo caches (an iOS-17 deserialization
 * perf hack) are dropped — Kotlin maps don't pay that cost, so reads/writes go
 * straight to the maps. The many feature-specific Program extensions
 * (ProgramContent core-program props, ProgramCheckIns consecutive-days,
 * timeline/sync handlers) are ported with their features; the core date/break
 * logic is here.
 */
@Serializable
class Program(
    var startDate: Instant = now(),
    var lastSmoked: Instant = now(),
    var lastSmokedSpans: MutableList<DateSpan>? = mutableListOf(),
    var latestCalendarViewMode: CalendarViewMode? = null,

    // Check-in
    var dayInfo: MutableMap<PlainDate, ProgramDayInfo> = mutableMapOf(),
    var customCheckIns: MutableList<CustomCheckIn> = mutableListOf(),
    var latestCheckInMethod: CheckInMethod? = null,

    // Content
    var breaks: MutableList<ProgramBreak> = mutableListOf(),
    var contentInfo: MutableMap<PlainDate, ContentInfo> = mutableMapOf(),
    var coreModeration: Boolean = true,
    var schoolMessages: MutableList<ProgramMessage> = mutableListOf(),
    var popUps: MutableList<ProgramPopUp>? = mutableListOf(),

    // Achievements
    var healthProgress: MutableList<ProgramHealthProgress> = mutableListOf(),
    var latestUpdate: Instant? = null,

    // Deprecated
    var coreMessages: MutableList<ProgramMessage> = mutableListOf(),
    var stageMap: MutableMap<PlainDate, Stage> = mutableMapOf(),
) {
    // MARK: - DayInfo / ContentInfo writes (cache machinery dropped; direct map ops)
    fun setDayInfo(value: PlainDate, dayInfo: ProgramDayInfo?) {
        if (dayInfo != null) this.dayInfo[value] = dayInfo else this.dayInfo.remove(value)
    }

    fun updateDayInfo(value: PlainDate, dayInfo: ProgramDayInfo?) = setDayInfo(value, dayInfo)

    fun updateContentInfo(at: PlainDate, value: ContentInfo?) {
        if (value != null) contentInfo[at] = value else contentInfo.remove(at)
    }

    // MARK: - Dates
    val currentDay: Int get() = getDay(now())
    fun getDay(date: Instant): Int = startDate.daysTo(date)

    // MARK: - Breaks (ProgramBreak.swift extension)
    val currentBreak: ProgramBreak? get() = getBreak(now())
    val lastBreak: ProgramBreak? get() = breaks.sorted.lastOrNull()

    fun getBreak(date: Instant): ProgramBreak? = breaks.sorted.firstOrNull {
        it.startDate.justDay <= date.justDay && date.justDay <= it.endDate.adding(days = -1)
    }

    /** The onboarding feedback payload (iOS `Program.initialFeedback`):
     *  the first real (non-start-soon) break's normative feedback. */
    val initialFeedback: ProgramNormativeFeedback?
        get() = breaks.firstOrNull { !it.isStartSoon }?.normativeFeedback

    val currentBreakNotStartSoon: ProgramBreak?
        get() {
            val today = now().justDay
            return breaks.sorted.firstOrNull {
                !it.isStartSoon && (
                    it.startDate.justDay >= today ||
                        (it.startDate <= today && today <= it.endDate.adding(days = -1))
                    )
            }
        }

    /** whatBringsYouHere choice index (0=Quit,1=Break,2=Moderate,3=Other). */
    val whatBringsYouHereIndex: Int
        get() {
            (currentBreak ?: lastBreak)
                ?.getAssessmentResponse(AssessmentQuestionID.WHAT_BRINGS_YOU_HERE.raw)
                ?.responses?.firstOrNull()?.let { return it }
            return if (coreModeration) 2 else 1
        }

    // MARK: - Badge / detail sheet
    /** (subtitle, title) for the home badge (ported from ProgramMessage.swift). */
    fun getBadgeInfo(date: Instant): Pair<String, String> {
        val b = getBreak(date)
        if (b != null && b.isStartSoon) {
            val daysUntilStart = date.daysTo(b.endDate) + 1
            return "Break starts in" to "$daysUntilStart Day${if (daysUntilStart == 1) "" else "s"}"
        }
        return if (b != null) b.type.typeName to "Day ${b.getBreakDay(date)}"
        else (if (coreModeration) "Moderation" else "Weed free") to "Life"
    }

    val programDescription: String?
        get() = currentBreak?.breakDescription ?: "Your long term support program."

    val detailSheetInfo: ProgramDetailSheetInfo?
        get() = currentBreak?.detailSheetInfo ?: ProgramDetailSheetInfo(
            title = "The Life Program is your ongoing guide for living more intentionally - with or without weed.",
            description = """
                Whether you're quitting, moderating, or still figuring it out, you'll get daily insights and tools for as long as you need them.

                Whenever you're ready for a break, you can always start a new Clear30.
            """.trimIndent(),
        )

    val coreProgramName: String get() = "Life ${if (coreModeration) "(Moderation)" else "(Abstinence)"}"
    fun getCoreProgramString(moderation: Boolean): String = if (moderation) "🍃 Moderation" else "😁 Weed free"

    /** Total days checked in as sober (iOS `numDaysSober`) — the Profile counter. */
    val numDaysSober: Int get() = dayInfo.values.count { it.sober == true }

    /** Days checked in as smoked (iOS `numDaysSmoked`). */
    val numDaysSmoked: Int get() = dayInfo.values.count { it.sober == false }

    /**
     * Recompute each health milestone's setback (iOS `updateProgramHealthSetbackDays`).
     * The health timeline renders `currentDay - setbackDays`, so each logged slip pushes
     * the whole timeline back by a day. Android's ProgramHealthProgress has no per-entry
     * start date, so every entry shares the program-wide smoked-day count.
     */
    fun updateHealthSetbackDays() {
        val smoked = numDaysSmoked
        healthProgress.forEach { it.setbackDays = smoked }
    }

    /** Auto-calculated $ saved over a break (iOS `getAutoCalculatedMoneySavedOverBreak`):
     *  cost-per-session × sober days in the break window. */
    fun getAutoCalculatedMoneySavedOverBreak(programBreak: ProgramBreak): Int? {
        val initialSpend = programBreak.getInitialWeeklySpend() ?: return null
        val initialWeeklyUse = programBreak.getInitialWeeklyUsage() ?: return null
        val expectedSmokingDays = ceil(initialWeeklyUse).toInt()
        if (expectedSmokingDays <= 0) return null
        val costPerSession = initialSpend.toFloat() / expectedSmokingDays.toFloat()
        val startPlain = PlainDate.from(programBreak.startDate)
        val todayPlain = PlainDate.from(now())
        if (startPlain > todayPlain) return null
        val soberDays = dayInfo.entries.count { (d, info) -> d in startPlain..todayPlain && info.sober == true }
        return (soberDays.toFloat() * costPerSession).toInt()
    }

    /** $ saved incl. the user's manual adjustment (iOS `getTotalMoneySavedOverBreak`). */
    fun getTotalMoneySavedOverBreak(programBreak: ProgramBreak): Int? {
        val auto = getAutoCalculatedMoneySavedOverBreak(programBreak) ?: return null
        return auto + programBreak.moneySavedAdjustment
    }

    // MARK: - Core (Life) program (ProgramContent.swift)
    /**
     * The "Life" timeline — every `contentInfo` entry whose date falls OUTSIDE
     * every break's `[startDate, endDate)` window (iOS `getCoreContentInfo`).
     * `endDate` is the first day *out* of the break, so the window is half-open.
     */
    fun getCoreContentInfo(): Map<PlainDate, ContentInfo> {
        val spans = breaks.map { PlainDate.from(it.startDate) to PlainDate.from(it.endDate) }
        return contentInfo.filterKeys { date -> spans.none { (start, end) -> start <= date && date < end } }
    }

    /** Any Life content exists at all. */
    val hasCoreMessages: Boolean get() = getCoreContentInfo().values.any { it.messages.isNotEmpty() }

    /** Life content is (or will be) scheduled ahead of today. */
    val willBeInCoreProgram: Boolean
        get() = hasCoreMessages && getCoreContentInfo().future.values.any { it.messages.isNotEmpty() }

    /** Currently living in the Life program (upcoming Life content + no active break). */
    val inCoreProgram: Boolean get() = willBeInCoreProgram && currentBreak == null

    /** Earliest Life-content unlock date (iOS `minCoreProgramUnlockOn`). */
    val minCoreProgramUnlockOn: Instant?
        get() = getCoreContentInfo().filterValues { it.messages.isNotEmpty() }.keys.minOrNull()?.dateObject

    /** Days since the first Life message (iOS `coreProgramDay`). */
    val coreProgramDay: Int get() = minCoreProgramUnlockOn?.daysTo(now()) ?: currentDay

    /** Prompt shown once a break's 30 days are up (resume/summary card copy). */
    val postAssessmentCardText: String?
        get() {
            val mostRecent = lastBreak ?: return null
            if (!mostRecent.postAssessmentCompleted && mostRecent.currentBreakDay >= mostRecent.type.raw) {
                if (!willBeInCoreProgram) return "${if (hasCoreMessages) "Resume" else "Start"} the Life program 💬"
                if (mostRecent.currentBreakDay <= mostRecent.type.raw + 3) return "${mostRecent.name} breakdown 📦"
            }
            return null
        }

    companion object {
        const val STORE_KEY = "program"
        const val coreBreakName = "Life"
        const val symptomsFilterName = "Symptoms"
        val defaultCheckIn: CustomCheckIn get() = CheckInDefaults.weed
    }
}

// MARK: - Timeline map slices (iOS `[PlainDate: ContentInfo]` extensions)
/** Entries up to and including today (Swift `.current`). */
val Map<PlainDate, ContentInfo>.current: Map<PlainDate, ContentInfo>
    get() = PlainDate.from(now()).let { today -> filterKeys { it <= today } }

/** Entries strictly after today (Swift `.future`). */
val Map<PlainDate, ContentInfo>.future: Map<PlainDate, ContentInfo>
    get() = PlainDate.from(now()).let { today -> filterKeys { it > today } }

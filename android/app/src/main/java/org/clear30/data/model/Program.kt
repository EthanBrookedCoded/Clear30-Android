package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.util.adding
import org.clear30.util.daysTo
import org.clear30.util.justDay
import org.clear30.util.now

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

    // TODO(port, content segment): core-program props (hasCoreMessages, willBeInCoreProgram,
    //   inCoreProgram, minCoreProgramUnlockOn, coreProgramDay, postAssessmentCardText) — need
    //   getCoreContentInfo() from ProgramContent.swift.

    companion object {
        const val STORE_KEY = "program"
        const val coreBreakName = "Life"
        const val symptomsFilterName = "Symptoms"
        val defaultCheckIn: CustomCheckIn get() = CheckInDefaults.weed
    }
}

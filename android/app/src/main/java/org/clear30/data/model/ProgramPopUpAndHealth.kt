package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.util.adding
import org.clear30.util.now

/**
 * Pop-ups remain a stub until PopIns.swift / ProgramPopUp.swift are ported
 * (deferred entirely per PARITY §17-Q13).
 */
@Serializable
class ProgramPopUp(
    var id: String = "",
    // TODO(port): full ProgramPopUp fields (ProgramPopUp.swift)
)

/**
 * ProgramHealthProgress — ported 1:1 from ProgramHealthProgress.swift.
 *
 * One entry per health category (Brain / Lungs / Heart in prod), holding the
 * category's backend definition ([category], [steps]) plus the per-user state:
 * [startDate] anchors the step dates (`startDate + setbackDays + step.days`),
 * [setbackDays] pushes the whole timeline back after slips, [personalBestStepID]
 * marks the furthest step ever reached (the ghost ring on the gauge), and
 * [lastVisited] drives the `hasNew` highlight on the profile card.
 */
@Serializable
class ProgramHealthProgress(
    var category: HealthCategory = HealthCategory(),
    var steps: MutableList<HealthStep> = mutableListOf(),
    var startDate: Instant = now(),
    var setbackDays: Int = 0,
    var personalBestStepID: Long? = null,
    var lastVisited: Instant = now(),
    var lastUpdated: Instant? = now(),
) {
    // MARK: - Step variables (iOS computed properties)

    val stepsWithDates: List<Pair<HealthStep, Instant>>
        get() {
            val adjustedStart = startDate.adding(days = setbackDays)
            return steps.map { it to adjustedStart.adding(days = it.daysWithoutWeed, seconds = it.minuteOffset * 60L) }
        }

    val currentStep: HealthStep? get() = stepsWithDates.lastOrNull { it.second <= now() }?.first
    val currentStepDate: Instant? get() = stepsWithDates.lastOrNull { it.second <= now() }?.second
    val nextStep: HealthStep? get() = stepsWithDates.firstOrNull { now() < it.second }?.first
    val nextStepDate: Instant? get() = stepsWithDates.firstOrNull { now() < it.second }?.second

    val previousStep: HealthStep?
        get() {
            val current = currentStepDate ?: return null
            return stepsWithDates.lastOrNull { it.second < current }?.first
        }

    val bestStep: HealthStep? get() = personalBestStepID?.let { id -> steps.firstOrNull { it.id == id } }
    val bestStepDate: Instant?
        get() = personalBestStepID?.let { id -> stepsWithDates.firstOrNull { it.first.id == id }?.second }

    // MARK: - State variables

    /** A new step unlocked since the user last opened this category's timeline. */
    val hasNew: Boolean get() = currentStepDate?.let { lastVisited < it } ?: false

    /** Percentage gained by the latest unlock (vs the previous step). */
    val newDelta: Int?
        get() {
            val current = currentStep ?: return null
            return previousStep?.let { current.percentage - it.percentage } ?: current.percentage
        }

    /** iOS `shouldUpdateHealthData` — refetch when never fetched or >24h stale. */
    val shouldUpdateHealthData: Boolean
        get() = lastUpdated?.let { (now() - it).inWholeHours >= 24 } ?: true

    // MARK: - Check-in handling (iOS `updateSetbackDays`)

    fun updateSetbackDays(days: Int) {
        // 1. Promote the personal best if the current step passed it.
        currentStep?.let { current ->
            val best = bestStep
            if (best == null) {
                personalBestStepID = current.id
            } else {
                val currentDate = startDate.adding(days = current.daysWithoutWeed, seconds = current.minuteOffset * 60L)
                val bestDate = startDate.adding(days = best.daysWithoutWeed, seconds = best.minuteOffset * 60L)
                if (bestDate <= currentDate) personalBestStepID = current.id
            }
        }
        // 2. Set setback days.
        setbackDays = days
    }

    companion object {
        /** iOS `updateHealthProgress` — merge fetched categories/steps with existing per-category state. */
        fun updateHealthProgress(
            existing: List<ProgramHealthProgress>,
            categories: List<HealthCategory>,
            steps: List<HealthStep>,
            startDate: Instant = now(),
        ): List<ProgramHealthProgress> {
            val stepsByCategory = steps.groupBy { it.categoryName }
            val existingByCategory = existing.associateBy { it.category.name }
            return categories.map { category ->
                val sortedSteps = (stepsByCategory[category.name] ?: emptyList())
                    .sortedBy { it.daysWithoutWeed }
                    .toMutableList()
                val prior = existingByCategory[category.name]
                if (prior != null) {
                    ProgramHealthProgress(
                        category = category,
                        steps = sortedSteps,
                        startDate = prior.startDate,
                        setbackDays = prior.setbackDays,
                        personalBestStepID = prior.personalBestStepID,
                        lastVisited = prior.lastVisited,
                        lastUpdated = now(),
                    )
                } else {
                    ProgramHealthProgress(
                        category = category,
                        steps = sortedSteps,
                        startDate = startDate,
                        lastUpdated = now(),
                    )
                }
            }
        }
    }
}

/** The soonest-updating category — gets the "in X hours" badge (iOS `nextIncreaseCategory`). */
val List<ProgramHealthProgress>.nextIncreaseCategory: ProgramHealthProgress?
    get() = minByOrNull { it.nextStepDate ?: Instant.DISTANT_FUTURE }

/**
 * HealthCategory — backend category definition (`library.health_categories`,
 * iOS `HealthCategory` struct). Decoded straight off the table row.
 */
@Serializable
class HealthCategory(
    var order: Int = 0,
    var name: String = "",
    @SerialName("sf_symbol") var sfSymbol: String = "heart.fill",
    var color1: String = "F65555",
    var color2: String = "F63E3E",
    @SerialName("long_name") var longName: String? = null,
    @SerialName("intro_content") var introContent: List<HealthIntroContentItem> = emptyList(),
    @SerialName("fda_disclaimer") var fdaDisclaimer: String = "",
)

/** One intro item on a category's pre-unlock timeline (iOS `HealthIntroContentItem`). */
@Serializable
class HealthIntroContentItem(
    /** "card" or "text" (iOS `HealthIntroContentType`). */
    var type: String = "card",
    var title: String = "",
    var body: String = "",
    // The jsonb uses camelCase for this one key (matches iOS Codable's default).
    var sfSymbol: String? = null,
)

/**
 * HealthStep — one milestone row (`library.health_steps`, iOS `HealthStep`).
 * NOTE: iOS declares notificationTitle/Body as `let … = nil` so its decoder
 * never reads them — iOS health pushes silently never fire. Android decodes
 * them properly (intended behavior per PARITY N2); all 41 prod steps carry copy.
 */
@Serializable
class HealthStep(
    var id: Long = 0,
    @SerialName("category") var categoryName: String = "",
    @SerialName("days_without_weed") var daysWithoutWeed: Int = 0,
    @SerialName("minute_offset") var minuteOffset: Int = 0,
    var percentage: Int = 0,
    var text: String = "",
    var citation: String = "",
    @SerialName("notification_title") var notificationTitle: String? = null,
    @SerialName("notification_body") var notificationBody: String? = null,
)

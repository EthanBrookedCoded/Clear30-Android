package org.clear30.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal placeholders so [Program] compiles. Pop-ups remain a stub until
 * PopIns.swift / ProgramPopUp.swift are ported; the health timeline has its
 * core fields below — enough for the Profile timeline section to render.
 */
@Serializable
class ProgramPopUp(
    var id: String = "",
    // TODO(port): full ProgramPopUp fields (ProgramPopUp.swift)
)

/**
 * ProgramHealthProgress — ported from ProgramHealthProgress.swift.
 *
 * Each entry is a per-category milestone the user is making progress against
 * (e.g. "lung capacity", "sleep quality"). [unlockedOnDay] is the program day
 * number when the milestone unlocks; [setbackDays] accumulates after a
 * slip-up — the timeline UI shows it as elapsed days minus setback days.
 * Cumulative progress is computed off the live [Program]; this just stores
 * the static milestone definitions + the per-user setback.
 */
@Serializable
class ProgramHealthProgress(
    var id: String = "",
    var category: HealthCategory = HealthCategory.OTHER,
    var title: String = "",
    var description: String = "",
    @SerialName("unlocked_on_day") var unlockedOnDay: Int = 0,
    @SerialName("setback_days") var setbackDays: Int = 0,
    /** SF Symbol name (mapped through sfSymbol() to a Material icon at render time). */
    var icon: String = "heart.fill",
)

/** Health categories — iOS rawValues kept for analytics compat. */
@Serializable
enum class HealthCategory(val rawValue: String) {
    @SerialName("brain") BRAIN("brain"),
    @SerialName("lungs") LUNGS("lungs"),
    @SerialName("sleep") SLEEP("sleep"),
    @SerialName("mood") MOOD("mood"),
    @SerialName("energy") ENERGY("energy"),
    @SerialName("memory") MEMORY("memory"),
    @SerialName("other") OTHER("other");
}

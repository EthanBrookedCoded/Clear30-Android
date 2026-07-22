package org.clear30.data.model

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.clear30.views.theme.Clear30Gradients
import org.clear30.util.nearestHour
import org.clear30.util.now
import java.util.UUID

/**
 * Check-in domain — ported from ProgramCheckIns.swift.
 *
 * Covers ProgramDayInfo, CustomCheckIn(+Option), LoggedCheckIn, CheckInMethod,
 * LoggedSymptom and CalendarViewMode. The `extension Program { ... }` blocks in
 * that Swift file (consecutive-day calculations, etc.) are ported with Program.
 *
 * Note: the iOS custom Codable handled legacy keys (`customCheckIn`, top-level
 * `sober`) for SwiftData migration. Android installs start fresh, so the
 * straightforward serializer below is sufficient.
 */

@Serializable
data class CustomCheckInOption(
    val emoji: String,
    val name: String,
)

@Serializable
data class CustomCheckIn(
    val id: String,
    val name: String,
    val incompleteOption: CustomCheckInOption,
    val completeOption: CustomCheckInOption,
    val hue: Double,
) {
    val gradient: Brush get() = Clear30Gradients.hue(hue, SLIDER_SATURATION, SLIDER_BRIGHTNESS, HUE_OFFSET)

    companion object {
        const val SLIDER_SATURATION = 0.6
        const val SLIDER_BRIGHTNESS = 0.9
        const val HUE_OFFSET = 0.07
    }
}

@Serializable
data class LoggedCheckIn(
    val id: String,
    val amount: Int? = null,
    val completion: Boolean? = null,
    // Lenient parse — iOS writes check-in timestamps offset-less (see
    // LenientInstantSerializer); a strict Instant would fail the whole day_info decode.
    @Serializable(with = LenientInstantSerializer::class)
    val timestamp: Instant? = now().nearestHour,
    val distinctID: String? = UUID.randomUUID().toString(),
) {
    val method: CheckInMethod? get() = CheckInMethod.fromID(id)

    companion object {
        fun genericWeedCheckIn(sober: Boolean?): LoggedCheckIn =
            LoggedCheckIn(id = CheckInDefaults.weed.id, completion = sober)
    }
}

// extension [LoggedCheckIn]
val List<LoggedCheckIn>.allWeedCheckIns: List<LoggedCheckIn>
    get() = filter { CheckInMethod.isWeedCheckIn(it.id) }

val List<LoggedCheckIn>.weedCheckIn: LoggedCheckIn?
    get() = allWeedCheckIns.firstOrNull()

val List<LoggedCheckIn>.smokingSessions: List<LoggedCheckIn>
    get() = allWeedCheckIns
        .filter { it.completion == false && it.timestamp != null }
        .sortedBy { it.timestamp ?: Instant.DISTANT_PAST }

fun List<LoggedCheckIn>.checkIns(method: CheckInMethod): List<LoggedCheckIn> =
    allWeedCheckIns.filter { it.id == method.id }.sortedBy { it.timestamp ?: Instant.DISTANT_PAST }

@Serializable
enum class CheckInMethod(val raw: String) {
    @SerialName("bud") BUD("bud"),
    @SerialName("pen") PEN("pen"),
    @SerialName("edible") EDIBLE("edible"),
    @SerialName("dab") DAB("dab");

    val id: String get() = if (this == BUD) "sober" else "sober-$raw"

    val customCheckIn: CustomCheckIn
        get() {
            val name = when (this) {
                BUD -> "Didn't smoke"; PEN -> "Didn't rip"; EDIBLE -> "Didn't eat"; DAB -> "Didn't dab"
            }
            val incomplete = when (this) {
                BUD -> "Smoked" to "💨"; PEN -> "Ripped" to "🖊️"; EDIBLE -> "Ate" to "🍫"; DAB -> "Dabbed" to "🎛️"
            }
            val complete = when (this) {
                BUD -> "Didn't smoke" to "🤩"; PEN -> "Didn't rip" to "🤩"
                EDIBLE -> "Didn't eat" to "🤩"; DAB -> "Didn't dab" to "🤩"
            }
            return CustomCheckIn(
                id = id,
                name = name,
                incompleteOption = CustomCheckInOption(emoji = incomplete.second, name = incomplete.first),
                completeOption = CustomCheckInOption(emoji = complete.second, name = complete.first),
                hue = 0.0,
            )
        }

    val amountIncrement: Int get() = if (this == EDIBLE) 5 else 1

    val amountLabel: String get() = when (this) {
        BUD -> "hit"; PEN -> "rip"; EDIBLE -> "mg"; DAB -> "dab"
    }

    // NOTE: must call the Int? overload via the named `amount` param — a plain
    // Int argument resolves to THIS overload and recurses forever (the shipped
    // smoked-check-in StackOverflowError).
    fun getAmountString(index: Int): String = getAmountString(amount = getAmount(index))!!

    fun getAmountString(amount: Int?): String? {
        if (amount == null) return null
        val label = amountLabel + (if (amount == 1) "" else "s")
        return "$amount $label"
    }

    fun getAmount(index: Int): Int = (index + 1) * amountIncrement

    fun getAmountIndex(amount: Int?): Int? = amount?.let { (it / amountIncrement) - 1 }

    companion object {
        fun fromID(id: String): CheckInMethod? = entries.firstOrNull { it.id == id }
        fun isWeedCheckIn(id: String): Boolean = entries.any { it.id == id }
    }
}

/** Program.defaultCheckIn — the standard weed check-in (CheckInMethod.bud). */
object CheckInDefaults {
    val weed: CustomCheckIn = CheckInMethod.BUD.customCheckIn
}

@Serializable
data class LoggedSymptom(
    val name: String,
    val selected: Boolean,
)

@Serializable
data class ProgramDayInfo(
    var loggedCheckIns: List<LoggedCheckIn> = emptyList(),
    var loggedSymptoms: List<LoggedSymptom> = emptyList(),
    var popInType: PopInType? = null,
    var variableRewardType: VariableRewardType? = null,
    var progress: Double? = null, // deprecated
) {
    val sober: Boolean? get() = loggedCheckIns.weedCheckIn?.completion

    companion object {
        /** Mirrors the iOS init(sober:...) that injects a generic weed check-in. */
        fun withSober(
            sober: Boolean? = null,
            loggedCheckIns: List<LoggedCheckIn> = emptyList(),
            loggedSymptoms: List<LoggedSymptom> = emptyList(),
            popInType: PopInType? = null,
        ): ProgramDayInfo {
            val checkIns = loggedCheckIns.toMutableList()
            if (checkIns.weedCheckIn == null) checkIns.add(LoggedCheckIn.genericWeedCheckIn(sober))
            return ProgramDayInfo(checkIns, loggedSymptoms, popInType)
        }
    }
}

/** CalendarViewMode — ported from the enum with associated CustomCheckIn values. */
@Serializable
sealed class CalendarViewMode {
    @Serializable @SerialName("weed")
    data object Weed : CalendarViewMode()

    @Serializable @SerialName("customCheckIn")
    data class Custom(val customCheckIn: CustomCheckIn) : CalendarViewMode()

    @Serializable @SerialName("split")
    data class Split(val customCheckIn: CustomCheckIn) : CalendarViewMode()

    val label: String
        get() = when (this) {
            is Weed -> "${CheckInDefaults.weed.completeOption.emoji} ${CheckInDefaults.weed.completeOption.name}"
            is Custom -> "${customCheckIn.completeOption.emoji} ${customCheckIn.name}"
            is Split -> Weed.label + " & " + Custom(customCheckIn).label
        }
}

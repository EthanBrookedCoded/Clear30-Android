package org.clear30.data.model

import java.util.UUID
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.clear30.data.supabase.SupabaseTriggerResponse
import org.clear30.util.now

/**
 * SlipPlan — port of the iOS `SlipPlan` (SlippedContent.swift): an if-then slip
 * plan created in the slipped sheet's "Make a plan" activity. Cached locally on
 * [UserInfo] under [CACHE_KEY]; synced to the `users.trigger_responses` jsonb
 * column via `SupabaseController.updateTriggerResponses` and restored from it
 * on sign-in (ProgramRestoreHandler).
 */
@Serializable
data class SlipPlan(
    val id: String = UUID.randomUUID().toString(),
    val ifText: String,
    /** The feeling behind the moment — optional, plans predating the field decode to null. */
    val feelingText: String? = null,
    val thenText: String,
    /** Short display date, e.g. "Apr 12". */
    val date: String,
    /** Real creation timestamp, used when syncing to the backend. */
    val createdAt: Instant? = null,
) {
    /** Backend wire model (`SupabaseTriggerResponse`). */
    fun toData(): SupabaseTriggerResponse = SupabaseTriggerResponse(
        id = id,
        if_text = ifText,
        feeling_text = feelingText,
        then_text = thenText,
        created_at = createdAt ?: now(),
    )

    companion object {
        /** UserInfo cache key for the locally stored if-then plans. */
        const val CACHE_KEY = "slipped_if_then_plans"

        private val SHORT_MONTHS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )

        /** iOS shows "MMM d" (e.g. "Apr 12") as the plan's display date. */
        fun displayDate(instant: Instant): String {
            val d = PlainDate.from(instant)
            return "${SHORT_MONTHS[d.month - 1]} ${d.day}"
        }
    }
}

/** Restore mapping (iOS ProgramRestoreHandler.swift:103-115). */
fun SupabaseTriggerResponse.toSlipPlan(): SlipPlan = SlipPlan(
    id = id,
    ifText = if_text,
    feelingText = feeling_text,
    thenText = then_text,
    date = SlipPlan.displayDate(created_at),
    createdAt = created_at,
)

package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

/**
 * Health timeline content — ported from the iOS `getHealthData` fetch
 * (`library.health_categories` + `library.health_steps`; SupabaseTables.swift).
 * Column shapes verified against prod 2026-07-13.
 */

@Serializable
data class SupabaseHealthCategory(
    val name: String,
    val sf_symbol: String,
    val color1: String? = null,
    val color2: String? = null,
    val order: Int = 0,
    val long_name: String? = null,
)

@Serializable
data class SupabaseHealthStep(
    val id: Long,
    val category: String,
    val days_without_weed: Int,
    val percentage: Int = 0,
    val text: String,
    val citation: String? = null,
    val minute_offset: Int = 0,
    // NOTE: iOS declares these `let … = nil` so its decoder never reads them —
    // iOS health pushes silently never fire. Android decodes them properly
    // (the intended behavior per PARITY N2); all 41 prod steps carry copy.
    val notification_title: String? = null,
    val notification_body: String? = null,
)

/** Fetch the health categories + steps (Swift `getHealthData`). */
suspend fun SupabaseController.getHealthData(): Pair<List<SupabaseHealthCategory>?, List<SupabaseHealthStep>?> {
    val categories = runCatching {
        client.postgrest.from("library", "health_categories").select().decodeList<SupabaseHealthCategory>()
    }.onFailure { android.util.Log.w("SupabaseHealth", "health_categories fetch failed: ${it.message}") }
        .getOrNull()
    val steps = runCatching {
        client.postgrest.from("library", "health_steps").select().decodeList<SupabaseHealthStep>()
    }.onFailure { android.util.Log.w("SupabaseHealth", "health_steps fetch failed: ${it.message}") }
        .getOrNull()
    return categories to steps
}

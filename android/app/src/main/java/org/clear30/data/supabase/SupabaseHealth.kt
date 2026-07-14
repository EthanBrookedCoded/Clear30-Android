package org.clear30.data.supabase

import io.github.jan.supabase.postgrest.postgrest
import org.clear30.data.model.HealthCategory
import org.clear30.data.model.HealthStep

/**
 * Health timeline content — ported from the iOS `getHealthData` fetch
 * (`library.health_categories` + `library.health_steps`; SupabaseTables.swift).
 * Rows decode straight into the model classes ([HealthCategory]/[HealthStep]),
 * whose @SerialNames match the column shapes (verified against prod 2026-07-13;
 * intro_content/fda_disclaimer confirmed in the local DB too).
 */
suspend fun SupabaseController.getHealthData(): Pair<List<HealthCategory>?, List<HealthStep>?> {
    val categories = runCatching {
        client.postgrest.from("library", "health_categories").select().decodeList<HealthCategory>()
    }.onFailure { android.util.Log.w("SupabaseHealth", "health_categories fetch failed: ${it.message}") }
        .getOrNull()
    val steps = runCatching {
        client.postgrest.from("library", "health_steps").select().decodeList<HealthStep>()
    }.onFailure { android.util.Log.w("SupabaseHealth", "health_steps fetch failed: ${it.message}") }
        .getOrNull()
    return categories to steps
}

package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * SchoolData — ported 1:1 from SchoolData.swift. Field names kept in snake_case
 * to stay byte-compatible with the Supabase payloads the iOS app decoded.
 * (The `updateSchoolData` fetch logic lives in SchoolDataAbstracted — ported
 * with the services segment.)
 */
@Serializable
data class SchoolData(
    val school_id: String,
    val short_name: String,
    val long_name: String,
    val color1: String,
    val color2: String,
    val activities: List<SchoolDataActivity> = emptyList(),
    val resources: Map<String, List<SchoolDataResource>> = emptyMap(),
    val messages: List<SchoolDataMessage> = emptyList(),
)

@Serializable
data class SchoolDataActivity(
    val org_name: String,
    val title: String,
    var date_time: Instant,
    val link: String,
    val subtitle: String? = null,
    val description: String? = null,
    val facilitated_by: String? = null,
    val location: String? = null,
    val phone_number: String? = null,
    val email: String? = null,
    val repeats: String? = null,
    val sub_links: Map<String, String>? = null,
) {
    val repeatsWeekly: Boolean get() = repeats == "weekly"
}

@Serializable
data class SchoolDataResource(
    val title: String,
    val subtitle: String,
    val link: String,
    val description: String? = null,
    val badge: String? = null,
    val phone_number: String? = null,
    val location: String? = null,
)

@Serializable
data class SchoolDataMessage(
    val day: Int,
    val title: String,
    val subtitle: String,
    val message: String,
    val prompts: Map<String, String>? = null,
    val journal_prompts: List<String>? = null,
    val resources: Map<String, String>? = null,
    val meditation_name: String? = null,
    val meditation_link: String? = null,
)

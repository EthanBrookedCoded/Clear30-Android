package org.clear30.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * SupabasePackagedMessages — ported from SupabaseModels.swift. The payload of
 * `program_get_messages`: the day-indexed messages + their stages.
 */
@Serializable
data class SupabasePackagedMessages(
    val stages: List<SupabaseStage> = emptyList(),
    val messages: List<SupabaseMessage> = emptyList(),
    val start_soon_messages: List<SupabaseMessage>? = null,
)

@Serializable
data class SupabaseStage(
    val id: String,
    val title: String,
    val subtitle: String,
    val body: String,
    val color1: String,
    val color2: String,
    val fred_experience: String? = null,
) {
    fun toStage() = Stage(title = title, subtitle = subtitle, body = body, color1 = color1, color2 = color2)
}

@Serializable
data class SupabaseMessage(
    val id: Int,
    val question_id: String? = null,
    val question_response: String? = null,
    val day: Int,
    val title: String,
    val subtitle: String,
    val body: String,
    val stage: String? = null,
    val meditation: ProgramMeditation? = null,
    val page_info: List<ProgramPageInfo>? = null,
    val resources: List<ProgramResource>? = null,
    val claire_prompts: List<ProgramClairePrompt>? = null,
    val claire_prompts_json: List<ProgramClairePrompt>? = null,
    val journal_prompts: List<String>? = null,
    val notification_title: String? = null,
    val notification_body: String? = null,
    val video_url: String? = null,
    val instagram_videos: List<ProgramVideo>? = null,
    val thumbnail_url: String? = null,
    val carousel_images: List<String>? = null,
    val member_perks: List<ProgramMemberPerk>? = null,
) {
    /** Build the local ProgramMessage, unlocking [unlockOn]. */
    fun toProgramMessage(unlockOn: Instant): ProgramMessage = ProgramMessage(
        unlockOn = unlockOn,
        messageID = id,
        questionID = question_id,
        questionResponse = question_response,
        title = title,
        subtitle = subtitle,
        message = body,
        pageInfo = (page_info ?: emptyList()).map { listOf(it.title, it.body) },
        meditation = meditation,
        allResources = resources ?: emptyList(),
        clairePrompts = claire_prompts_json ?: claire_prompts ?: emptyList(),
        journalPrompts = journal_prompts,
        notificationTitle = notification_title,
        notificationBody = notification_body,
        videoURL = video_url,
        instagramVideos = instagram_videos,
        thumbnailURL = thumbnail_url,
        carouselImages = carousel_images,
        memberPerks = member_perks,
    )
}

/**
 * SupabaseMessageWithStage — the row shape of `programs.fetch_messages_by_ids`
 * (Swift `SupabaseMessageWithStage`). The stage is LEFT-JOINed and flattened into
 * `stage_*` columns, used by the account-restore path to rebuild content from the
 * message ids stored in `users.content_info`.
 */
@Serializable
data class SupabaseMessageWithStage(
    val id: Int,
    val day: Int,
    val stage: String? = null,
    val title: String,
    val subtitle: String,
    val body: String,
    val page_info: List<ProgramPageInfo>? = null,
    val meditation: ProgramMeditation? = null,
    val resources: List<ProgramResource>? = null,
    val claire_prompts: List<ProgramClairePrompt>? = null,
    val claire_prompts_json: List<ProgramClairePrompt>? = null,
    val journal_prompts: List<String>? = null,
    val notification_title: String? = null,
    val notification_body: String? = null,
    val thumbnail_url: String? = null,
    val video_url: String? = null,
    val instagram_videos: List<ProgramVideo>? = null,
    val carousel_images: List<String>? = null,
    val member_perks: List<ProgramMemberPerk>? = null,
    val question_id: String? = null,
    val question_response: String? = null,
    val stage_title: String? = null,
    val stage_subtitle: String? = null,
    val stage_body: String? = null,
    val stage_color1: String? = null,
    val stage_color2: String? = null,
    val stage_fred_experience: String? = null,
) {
    /** Reconstruct the [Stage] from the flattened `stage_*` columns, if present. */
    fun toStage(): Stage? =
        if (stage_title != null && stage_color1 != null && stage_color2 != null) {
            Stage(title = stage_title, subtitle = stage_subtitle ?: "", body = stage_body ?: "", color1 = stage_color1, color2 = stage_color2)
        } else {
            null
        }

    /** Build the local ProgramMessage, unlocking [unlockOn] (mirrors SupabaseMessage). */
    fun toProgramMessage(unlockOn: Instant): ProgramMessage = ProgramMessage(
        unlockOn = unlockOn,
        messageID = id,
        questionID = question_id,
        questionResponse = question_response,
        title = title,
        subtitle = subtitle,
        message = body,
        pageInfo = (page_info ?: emptyList()).map { listOf(it.title, it.body) },
        meditation = meditation,
        allResources = resources ?: emptyList(),
        clairePrompts = claire_prompts_json ?: claire_prompts ?: emptyList(),
        journalPrompts = journal_prompts,
        notificationTitle = notification_title,
        notificationBody = notification_body,
        videoURL = video_url,
        instagramVideos = instagram_videos,
        thumbnailURL = thumbnail_url,
        carouselImages = carousel_images,
        memberPerks = member_perks,
    )
}

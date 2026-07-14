package org.clear30.data.supabase

import io.github.jan.supabase.storage.storage

/**
 * Supabase Storage helpers — ported from `uploadFileToSupabase` /
 * `getFilePublicURL` in SupabaseFunctions.swift. Community video posts upload the
 * clip + thumbnail to the public `community` bucket and store the resulting
 * public URLs on the post (see iOS CreatePostView).
 */
const val COMMUNITY_BUCKET = "community"

/**
 * Upload [bytes] to [bucket] at [path] and return the public URL, or a failure.
 * Uses upsert so a retried upload with the same generated path won't 409.
 */
suspend fun SupabaseController.uploadPublicFile(
    bucket: String,
    path: String,
    bytes: ByteArray,
): Result<String> = runCatching {
    val api = client.storage.from(bucket)
    api.upload(path, bytes) { upsert = true }
    api.publicUrl(path)
}.onFailure { android.util.Log.w("SupabaseStorage", "upload $bucket/$path failed: ${it.message}") }

/**
 * Delete a file given its public URL (Swift `removeFileFromSupabase`): the
 * bucket-relative path is whatever follows "<bucket>/" in the URL. Used when a
 * video post is deleted so its clip + thumbnail don't orphan in storage.
 */
suspend fun SupabaseController.removePublicFile(bucket: String, publicUrl: String) {
    val path = publicUrl.substringAfter("$bucket/", missingDelimiterValue = "")
    if (path.isEmpty()) return
    runCatching { client.storage.from(bucket).delete(listOf(path)) }
        .onFailure { android.util.Log.w("SupabaseStorage", "remove $bucket/$path failed: ${it.message}") }
}

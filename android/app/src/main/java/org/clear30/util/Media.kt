package org.clear30.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Video media helpers. Mirrors iOS's `AVAssetImageGenerator` thumbnail grab in
 * NewVideoEntryViewModel — pulls a poster frame from a recorded clip so the
 * community feed can show a still + play badge.
 */

/** Grab a poster frame (~first frame) from a local video [uri], or null on failure. */
fun extractVideoThumbnail(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        // 0µs = first frame (iOS used CMTime 1/60s); OPTION_CLOSEST_SYNC is fast.
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (e: Exception) {
        android.util.Log.w("Media", "thumbnail extract failed: ${e.message}")
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** Encode a bitmap to PNG bytes (matches the iOS `.png` thumbnail upload). */
fun Bitmap.toPngBytes(): ByteArray =
    ByteArrayOutputStream().use { out ->
        compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.exoplayer.offline.Download

/** A completed file wins over stream authentication, quality lookup and server availability. */
internal fun completedDownloadIsCached(cache: Cache, spec: DataSpec, download: Download?): Boolean {
    val key = spec.key ?: return false
    if (download?.state != Download.STATE_COMPLETED) return false
    val total = download.contentLength.takeIf { it > 0 }
        ?: ContentMetadata.getContentLength(cache.getContentMetadata(key)).takeIf { it > 0 }
        ?: return false
    if (spec.position >= total) return false
    val remaining = total - spec.position
    val length = if (spec.length >= 0) minOf(spec.length, remaining) else remaining
    return length > 0 && cache.isCached(key, spec.position, length)
}

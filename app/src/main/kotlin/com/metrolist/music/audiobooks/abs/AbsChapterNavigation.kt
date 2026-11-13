/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.audiobooks.abs

import com.metrolist.music.audiobooks.AudiobookChapter

internal fun absChapterNavigation(trackId: String?, tracks: Map<String, AbsTrackInfo>): List<AudiobookChapter> {
    val info = trackId?.let(tracks::get) ?: return emptyList()
    val bookTracks = tracks.entries.filter { it.value.itemId == info.itemId }.sortedBy { it.value.startOffsetSec }
    val chapters = bookTracks.firstNotNullOfOrNull { it.value.chapters.takeIf { list -> list.isNotEmpty() } }.orEmpty()
    return chapters.filter { it.startSec.isFinite() && it.startSec >= 0 && it.startSec < info.bookDurationSec }
        .sortedBy { it.startSec }.mapNotNull { chapter ->
            val track = bookTracks.lastOrNull { it.value.startOffsetSec <= chapter.startSec } ?: return@mapNotNull null
            AudiobookChapter(track.key, chapter.title, ((chapter.startSec - track.value.startOffsetSec) * 1000).toLong())
        }
}

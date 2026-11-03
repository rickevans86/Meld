/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

const val AUDIOBOOK_ID_PREFIX = "audiobook:"

/** Tracks streamed from an Audiobookshelf server: "audiobook:abs:<itemId>:<trackIndex>". */
const val ABS_TRACK_ID_PREFIX = "audiobook:abs:"

fun isAbsTrackId(mediaId: String?): Boolean = mediaId?.startsWith(ABS_TRACK_ID_PREFIX) == true

fun isAudiobookId(mediaId: String?): Boolean = mediaId?.startsWith(AUDIOBOOK_ID_PREFIX) == true

/** Media played straight from a file on the device rather than streamed. */
fun isOnDeviceMediaId(mediaId: String): Boolean =
    mediaId.startsWith("local:") || mediaId.startsWith(AUDIOBOOK_ID_PREFIX)

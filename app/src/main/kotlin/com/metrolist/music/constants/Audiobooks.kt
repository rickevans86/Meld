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

/** Podcast episodes played from their public feed: "podcast:<episode hash>". */
const val PODCAST_EPISODE_ID_PREFIX = "podcast:"

/**
 * Media that isn't resolved through YouTube: a file on the device, an Audiobookshelf file,
 * a podcast feed's audio URL, a radio stream or a Subsonic server song.
 */
fun isOnDeviceMediaId(mediaId: String): Boolean =
    mediaId.startsWith("local:") ||
        mediaId.startsWith(AUDIOBOOK_ID_PREFIX) ||
        mediaId.startsWith(PODCAST_EPISODE_ID_PREFIX) ||
        mediaId.startsWith(RADIO_ID_PREFIX) ||
        mediaId.startsWith(SUBSONIC_ID_PREFIX) ||
        mediaId.startsWith(MEDIA_SERVER_ID_PREFIX)

/** Live internet radio stations: "radio:<Radio Browser station id>". Never cached or downloaded as a file. */
const val RADIO_ID_PREFIX = "radio:"

fun isRadioId(mediaId: String?): Boolean = mediaId?.startsWith(RADIO_ID_PREFIX) == true

/** Songs on a Navidrome / Subsonic server: "subsonic:<song id>". The stream URL is built when played. */
const val SUBSONIC_ID_PREFIX = "subsonic:"

fun isSubsonicId(mediaId: String?): Boolean = mediaId?.startsWith(SUBSONIC_ID_PREFIX) == true

/** Jellyfin / Plex songs: "mediaserver:<item id>" (see mediaserver/MediaServerRepository). */
const val MEDIA_SERVER_ID_PREFIX = "mediaserver:"

fun isMediaServerId(mediaId: String?): Boolean = mediaId?.startsWith(MEDIA_SERVER_ID_PREFIX) == true

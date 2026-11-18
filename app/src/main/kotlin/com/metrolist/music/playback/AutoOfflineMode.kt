package com.metrolist.music.playback

/** Only local browse nodes remain valid after the phone switches to Offline mode. */
internal fun autoOfflineBrowseAllowed(id: String): Boolean =
    id in setOf(MusicService.ROOT, AutoLibraryExtras.PODCASTS, AutoLibraryExtras.AUDIOBOOKS,
        AutoLibraryExtras.LOCAL_FILES, "podcasts/downloaded") ||
        id.startsWith("local_files/folder/") || id == AutoAudiobookLibrary.DOWNLOADED ||
        (id.startsWith(AutoAudiobookLibrary.DOWNLOADED + "/") && autoPageNumber(id.substringAfterLast('/')) != null)

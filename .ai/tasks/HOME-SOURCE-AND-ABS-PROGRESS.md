# HOME-SOURCE-AND-ABS-PROGRESS

AI: Claude
Status: implemented; owner authorized build + release on 2026-11-14. Temporary task ID because GitHub Issues are disabled.
Branch: fix/abs-percent (from main; branch name predates the wider scope)

Bundle of fixes the owner reported in one session (all decisions confirmed by the owner):

1. Audiobookshelf percent showed 0% while position/time-left were correct. AbsProgress.fractionComplete(bookDuration) now derives the fraction from currentTime/duration (book duration preferred, server duration fallback, server progress field last). Used for the detail %, progress bars, grid badges and the Continue card.
2. Finished audiobooks stayed "in progress". AbsProgress.isComplete(bookDuration) treats the last 60 s (ABS_FINISHED_TAIL_SEC) as finished. AbsRepository.reportPosition now marks the book finished on the server once playback reaches that tail, so it syncs and leaves the in-progress list without the manual Mark as finished. The detail play button ("Listen again"), the mark-finished toggle, grid badges, the Listening filter and chapter highlighting all use isComplete.
3. YouTube Speed dial showed a pinned live radio station. Home rows and Speed dial are now source-aware: isCurrentSourceId keeps only YouTube items on the YouTube home and only Spotify items on the Spotify home. Podcasts remain excluded (isPodcastLike / isEpisode).
4. Spotify home showed YouTube rows (mixed mode). Option A (owner's choice): the Spotify home is now always Spotify-only — the YouTube-sourced sections are gated by !isSpotifyHome, and the play-history rows filter to Spotify ids (so they are empty there). The spotifyHomeOnly setting is now redundant but left in place.
5. Podcast playlists (e.g. the New Episodes auto-playlist) leaked into Mixes. loadAccountPlaylists now drops PlaylistItem.isPodcast.
6. The YouTube home repeated feed sections ("New releases" x3, "Featured playlists" x2) when scrolling. loadMoreYouTubeItems de-dupes continuation sections by HomePage.Section.sectionKey() (title + first item id).

Tests: AbsProgressTest (5) covers position-based percent, duration fallback, the 60 s tail, the server finished flag and clamping. YouTubeMediaIdTest (existing) covers source ids.

Verification: AbsProgressTest passed locally (standalone Kotlin compiler). Resource check clean. PR #8 CI run 43527390708 passed: unit tests, FOSS debug build, APK upload and lint.

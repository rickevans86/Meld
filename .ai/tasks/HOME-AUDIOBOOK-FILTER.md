# HOME-AUDIOBOOK-FILTER

AI: Claude
Status: released in Wavelength 0.9.2.85. PR #7 was folded into PR #6 (the new-app-ID build) and closed; its commits (87e65747, e8b57a44) are in main via merge 0a221b60. Temporary task ID because GitHub Issues are disabled.
Branch: fix/home-recently-audiobooks (from main)

Reported (owner screenshots): an audiobook ("Exoplanet") showed in the YouTube home's Recently Played. The owner then asked that each home show only its own source — the YouTube home only YouTube items, the Spotify home only Spotify items.

Cause: the YouTube home's play-history rows (quickPicks, recentlyPlayed, forgottenFavorites, keepListening in HomeScreen.kt) filtered out podcast episodes via `song.isEpisode` only. Play history also holds audiobooks, radio, local files and Subsonic/media-server songs, which have their own tabs but leaked into these rows. Recently Played was also added to the Spotify-only home.

Fix:
- constants/Audiobooks.kt: new `isYouTubeMediaId(mediaId)` = not `isOnDeviceMediaId` (local/audiobook/podcast/radio/subsonic/mediaserver) and not a `spotify:` id. (The earlier, narrower `isSpokenWordId` was replaced by this.)
- HomeScreen.kt: the four rows now keep only `isYouTubeMediaId(it.id)` items, and still drop YouTube-native podcast episodes (`song.isEpisode`, which have plain YouTube ids).
- HomeScreen.kt: Recently Played is no longer added on the Spotify-only home (`!isSpotifyHomeOnly`). The mixed Spotify home still shows YouTube rows, as before. The Spotify home's own sections already come only from the Spotify API.
- YouTubeMediaIdTest covers YouTube vs audiobook/podcast/radio/local/subsonic/mediaserver/spotify ids.

Tests: YouTubeMediaIdTest passed locally (standalone Kotlin compiler). Resource check clean (no new resources).

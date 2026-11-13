# HOME-AUDIOBOOK-FILTER

AI: Claude
Status: implemented; CI-verified on PR. Not yet released (coordinating with the app-ID switch). Temporary task ID because GitHub Issues are disabled.
Branch: fix/home-recently-audiobooks (from main)

Bug (owner screenshot): an audiobook ("Exoplanet", 4:08:18) appeared in the YouTube home's Recently Played. The home rows (quickPicks, recentlyPlayed, forgottenFavorites, keepListening in HomeScreen.kt) filtered on `song.isEpisode`, which catches podcast episodes but not audiobook chapters (those aren't episodes; their ids start with `audiobook:`).

Fix:
- constants/Audiobooks.kt: new `isSpokenWordId(mediaId)` — true for `audiobook:` or `podcast:` ids.
- HomeScreen.kt: the four home rows now also drop items where `isSpokenWordId(it.id)`.
- SpokenWordIdTest covers audiobook/podcast ids as spoken-word and YouTube/local/subsonic/radio/null as not.

Tests: SpokenWordIdTest passed locally (standalone Kotlin compiler). Resource check clean (no new resources).

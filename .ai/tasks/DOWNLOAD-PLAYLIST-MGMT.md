# DOWNLOAD-PLAYLIST-MGMT

## Implementation — DOWNLOAD-PLAYLIST-MGMT, Claude, 2026-11-27

Owner asked to expand the downloads section to allow management of currently-downloading playlists and downloaded playlists. Builds on DOWNLOAD-QUEUE-SETTINGS (0.9.2.118). Owner choices (AskUserQuestion): expand the existing Manage download queue screen into sections (not separate screens); downloaded-playlist actions = Remove download + Re-download + Open playlist; scope = all playlists incl. pinned YouTube/Spotify.

Delivered:
- db/DatabaseDao.kt: reused the existing read-only `playlistSongIds(playlistId): List<String>` (no DAO change needed; an added duplicate was removed after CI flagged the overload clash).
- viewmodels/DownloadManagementViewModel.kt (new, @HiltViewModel, injects MusicDatabase + DownloadUtil): combines database.playlists(NAME,false) with downloadUtil.downloads; per playlist aggregates member-song states into PlaylistDownload(total/completed/active/paused). Emits downloadingPlaylists (active>0 or paused>0) and downloadedPlaylists (completed==total). Whole-playlist actions fan out to member songs: pausePlaylist/resumePlaylist/cancelPlaylist (DownloadUtil.pause/resume/cancelDownload), removePlaylistDownload (cancelDownload = removeDownload, deletes files), redownloadPlaylist (DownloadUtil.download(songId)).
- ui/screens/settings/DownloadQueueScreen.kt: now sectioned — Downloading playlists (artwork, "N of M downloaded · K downloading" / paused status, progress, pause-resume + cancel, tap to open), Downloaded playlists (artwork, "M songs downloaded", re-download + remove with confirm dialog, tap to open), and the existing per-song queue under an Individual songs header (header only shown when playlist sections exist). Open routes: online_playlist/browseId (YouTube) else local_playlist/id.
- res/values/metrolist_strings.xml: downloading_playlists, downloaded_playlists, individual_songs, playlist_downloading_status (3 args), playlist_download_paused_status (2), playlist_downloaded_status (1), redownload_playlist, remove_playlist_download_confirm (1). Reuses remove_download, pause/resume/cancel_download, download_state_*, pause/resume_all_downloads.

Scope note / known limitation: covers playlists in the `playlist` table (local + pinned/synced YouTube). Spotify-pinned playlists live in a separate table and download as mapped YouTube songs, so there is no reliable offline Spotify-playlist→songs map to aggregate; excluded for now (flagged to owner). Liked songs is not a `playlist`-table row, so also not listed yet.

Validation: no local Android SDK in Claude's workspace, so no Gradle build here; relying on GitHub PR CI (unit tests, FOSS debug build, lint). Callable refs downloadUtil::download resolve to download(String). No schema/app-version/signing/dependency change. Physical phone check: start downloading a playlist and watch it in Downloading playlists with pause/resume/cancel; a fully-downloaded playlist in Downloaded playlists with re-download/remove/open; individual-song queue still works.

Branch `download-playlist-mgmt` off main 2ad79cc0. PR opened for CI. Not merged/released — awaiting owner. AI: Claude. Issues disabled → temporary task ID.

## Follow-up — Liked songs + Spotify, Claude, 2026-11-27

Owner asked to also cover Spotify and Liked songs, then publish. Added on the same branch/PR #39:
- Liked songs: aggregated from database.likedSongs(CREATE_DATE, true); open route auto_playlist/liked; label R.string.liked. Reliable/offline.
- Spotify playlists: Spotify track lists aren't stored locally, so grouping comes from a new per-download source tag. DownloadExporter.request now persists downloadSource:<songId> = sourcePlaylistId and downloadSourceName:<sourceId> = folder when a sourcePlaylistId is supplied (auto-download already supplies "spotify:<id>"; added sourcePlaylistId = "spotify:<playlist.id>" to the manual Spotify-playlist download in SpotifyPlaylistScreen). The ViewModel reads the dataStore prefs, groups songIds whose source starts "spotify:", resolves the display name from downloadSourceName (stripping the "Spotify - " folder prefix) else R.string.spotify_playlist_label, and opens via spotify_playlist/<id>.
- ViewModel refactor: PlaylistDownload now carries memberIds + openRoute, so all actions (pause/resume/cancel/remove/redownload) fan out uniformly over memberIds regardless of kind; the screen passes the item.

Limitation (owner informed): Spotify grouping is forward-looking — only downloads tagged after this build appear; pre-existing Spotify downloads show after a re-download. Membership reflects only tagged/downloaded songs (remote full track count isn't known offline). Local/YouTube still use DB membership (works for existing downloads); Liked uses the library. No Room schema change (tags are DataStore). New string spotify_playlist_label.

Files added to the PR: playback/DownloadExporter.kt (source-tag persistence), ui/screens/playlist/SpotifyPlaylistScreen.kt (tag manual download), viewmodels/DownloadManagementViewModel.kt (liked + spotify + memberIds refactor), ui/screens/settings/DownloadQueueScreen.kt (openRoute + item actions), res strings.

## Released — 0.9.2.119 / DOWNLOAD-PLAYLIST-MGMT, Claude, 2026-11-27

Owner authorized "then publish the release" (after adding Spotify + Liked). PR #39 CI history: three real compile errors caught and fixed before release — duplicate DAO playlistSongIds (43e8be64), PlaylistDownload.id→key list-key refs (29423b0d), and the Media3 setStopReason note belongs to the prior PR; final build green on 29423b0d. Merged to main as 3637de10284da8dc9264a9842e0df95dcb55fa42 (merge commit). Signed release run 45886452385 (run number 119) succeeded, published v0.9.2.119 (non-draft/non-prerelease, target 3637de10, asset Wavelength.apk).

Verified public APK: application id com.rickevans86.wavelength, versionName 0.9.2.119, versionCode 280119 (previous 280118, +1), signing certificate SHA-256 9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8 (unchanged). APK SHA-256 1fe3908cfa718b635ead97d16d294ebedcdd0460eed3370bc808ebb2bfebca30. No manual version/schema/signing/dependency change (Spotify source tags are DataStore). Physical phone check remains an owner follow-up: downloading/downloaded playlist sections for local, pinned YouTube, Liked and Spotify; whole-playlist pause/resume/cancel/re-download/remove/open; note Spotify is forward-looking (only downloads tagged on 0.9.2.119+ group). AI: Claude.

## Fix — Downloading-section over-listing, Claude, 2026-11-27 (post-0.9.2.119)

Owner reported the Downloading playlists section listing playlists that aren't downloading (screenshot: '90s Dance "1 of 105 downloaded · 48 downloading" while its own ⋮ menu still offered "Download"). Cause: the ViewModel classified a playlist as downloading when ANY member song was active, but members are shared across playlists and the queue is large/partial, so playlists that only shared queued songs (or were partly enqueued) showed up. The app's own rule (LocalPlaylistScreen.downloadState) treats a playlist as DOWNLOADING only when ALL songs are QUEUED/DOWNLOADING/COMPLETED.

Fix (DownloadManagementViewModel): a playlist is "downloading" only when completed+active+paused == total (every song accounted for — none un-queued), completed < total, and active>0||paused>0. "Downloaded" unchanged (all songs completed). One-file change; no schema/version/dependency change. Branch fix-downloading-playlist-filter off main 0f47beb1; PR for CI; not merged/released until owner says.

## Fix v2 — source-tag grouping + auto-download section, Claude, 2026-11-27

Owner clarified: a song in multiple playlists made ALL those playlists show, not just the one set to download; and asked for a section showing playlists set to auto-download. Superseded the fully-enqueued heuristic with intent-based grouping:
- DownloadManagementViewModel now groups downloading/downloaded by the per-download source tag (downloadSource/downloadSourceName written by DownloadExporter) instead of DB playlist membership. A shared song carries the source of the playlist it was downloaded from, so it only counts toward that playlist. resolveSource() maps a source id (liked / spotify:<id> / youtube:<remote> / local id) to name+thumbnail+open route via DB where possible. Dropped the database.playlists()/likedSongs() enumeration.
- Tag coverage: the main playlist download paths already pass sourcePlaylistId (PlaylistMenu local/YouTube-bookmarked = playlist.id; YouTubePlaylistMenu = youtube:<id>; auto-download; Spotify manual added earlier). Added it to the Liked/auto-playlist downloads (AutoPlaylistScreen, sourcePlaylistId = playlistId) and Spotify liked (SpotifyLikedSongsScreen, "spotify:liked").
- New Auto-download playlists section from PlaylistAutoDownload.AutoDownloadPlaylistsKey (reactive via dataStore flow): tap-to-open + a turn-off button (PlaylistAutoDownload.setEnabled false). New strings auto_download_playlists/auto_download_on/turn_off_auto_download.
Limitation: tag-based grouping is forward-looking (a playlist appears once downloaded on 0.9.2.120+; older downloads group after re-download). No schema change (DataStore tags). Branch fix-downloading-playlist-filter; publishing per owner.

## Released — 0.9.2.120 / fix v2 + auto-download section, Claude, 2026-11-27

Owner authorized "make changes and publish the release". PR #40 CI green on 4f98f4f2 (an earlier commit build showed cancelled only because the newer push superseded it). Merged to main as 289a530bed94c095b6dc3a9cba1f0394638981b9 (merge commit). Signed release run 45911330597 (run number 120) succeeded, published v0.9.2.120 (non-draft, target 289a530b, asset Wavelength.apk).

Verified public APK: application id com.rickevans86.wavelength, versionName 0.9.2.120, versionCode 280120 (previous 280119, +1), signing certificate SHA-256 9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8 (unchanged). APK SHA-256 9e388973b0b844208c2d3d79132d60149a275107708232c34e6e57cf7fba1f4e. No schema/version/signing/dependency change (source tags are DataStore). Covers the shared-song fix (source-tag grouping), Liked/Spotify-liked tagging, and the Auto-download playlists section. Forward-looking (tag-based; older downloads group after re-download). Physical phone check remains an owner follow-up. AI: Claude.

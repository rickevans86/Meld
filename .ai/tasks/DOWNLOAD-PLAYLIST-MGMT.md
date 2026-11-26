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

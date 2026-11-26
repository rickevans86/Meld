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

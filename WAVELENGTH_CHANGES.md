# Wavelength changes

Wavelength is a fork of [Meld](https://github.com/FrancescoGrazioso/Meld), which itself builds on Metrolist.
This file records everything Wavelength adds or changes. When Meld releases an update, use it to merge
the update in or to rebuild these features on top of the new code.

- **Fork:** `rickevans86/Meld`, branch `feat/local-folders`
- **Upstream:** `FrancescoGrazioso/Meld`, branch `main`
- **Last upstream commit this is based on:** `7a31ba6e4ca6b2956ad00354b320eee7dcc5fafc`

> Keep this file up to date: every change to Wavelength should update the matching section below
> and add a line to the change log at the end.

---

## How to pull in a new Meld update

1. Fetch the update:
   ```
   git remote add upstream https://github.com/FrancescoGrazioso/Meld.git   # once
   git fetch upstream
   git checkout feat/local-folders
   git merge upstream/main
   ```
2. Most Wavelength code is in **new files** (listed under each feature), so it won't conflict.
   Conflicts will be in the **upstream files Wavelength edits**; see
   [Upstream files Wavelength edits](#upstream-files-wavelength-edits) for exactly what to re-apply in each.
3. Strings: all Wavelength strings are added to `app/src/main/res/values/metrolist_strings.xml` only
   (no translations). If upstream also edits that file, keep both sides.
4. Push. The GitHub Action builds, signs and publishes a release, and the in-app updater picks it up.
5. Update the "Last upstream commit" line above and add a change-log entry.

If a merge is too messy, start a fresh branch from `upstream/main`, copy the new files across as they are,
and re-apply the small edits listed for each upstream file.

## Rules this fork follows

- No Room database schema changes, because they would need migrations that upstream doesn't know about.
  New DAO queries are fine.
- Don't bump the base version in source. CI appends a build number (`0.9.2.<n>`).
- Strings go only in `metrolist_strings.xml`.
- The signing key is never committed. It lives in the `DEBUG_KEYSTORE_BASE64` repository secret, and
  the owner holds a backup. Every release must be signed with this same key, or the in-app update fails.

---

## Build and release

| What | Where |
|---|---|
| Workflow "Build and Release" | `.github/workflows/build_local_folders.yml` (new) |
| Triggers | Push to `feat/local-folders`, or a manual run |
| App name | Set by the `METROLIST_APP_NAME: Wavelength` env in the workflow |
| Version | `app/build.gradle.kts`: the `FORK_BUILD_NUMBER` env sets versionCode to `versionCode * 10000 + n` and versionName to `0.9.2.n` |
| Signing | `DEBUG_KEYSTORE_BASE64` secret, validated with `keytool`. If it's invalid, the workflow falls back to a cached key |
| Output | `Meld.apk` attached to the GitHub release `v<version>` |

The build task is `:app:assembleFossDebug` with `PULL_REQUEST=false`, and it retries once with
`--refresh-dependencies`.

## App icon

The red music-note icon is in `res/mipmap-*/ic_launcher*.png`: the background, foreground, monochrome and
legacy versions at every density. Upstream icon changes will conflict here; keep the Wavelength versions.

## Features

### 1. Local files: folder view and music folders
- The Local files tab can switch between a song list and a folder view.
- **Settings → Storage → Music folders** (`settings/local_folders`) can include or exclude MediaStore folders,
  or add any folder with the system picker.
- Songs are de-duplicated by file path, keeping the newest MediaStore id.
- IDs: MediaStore songs use `local:<id>`. Songs from picked folders use `local:doc:<uri>`.
- New files: `localfiles/LocalMusicFolders.kt`, `ui/screens/settings/LocalMusicFoldersSettings.kt`
- Edited: `LocalFilesScreen.kt`, `LocalFilesViewModel.kt`, `PreferenceKeys.kt` (`LocalFilesFolderViewKey`,
  `LocalMusicIncluded/Excluded/AddedFoldersKey`)

### 2. Downloads saved as real files (M4A), sorted into playlist folders
- **Settings → Storage → Download folder** copies every finished download into a folder you choose (SAF).
- Audio is saved as `.m4a`. Opus/WebM streams are re-encoded on the device to AAC 256 kbps
  (`AacTranscoder`), and AAC streams are copied as they are.
- With **Playlist folders** on, a song is copied into one folder per playlist it's in:
  - YouTube playlists: `YouTube - <playlist>`
  - Spotify playlists: `Spotify - <playlist>`
  - Liked songs: `YouTube - Liked songs` and `Spotify - Liked songs`
- Folders re-sync automatically 15 s after playlist or liked-songs membership changes. Files are not deleted
  when a song leaves a playlist.
- New files: `playback/DownloadExporter.kt`, `playback/AacTranscoder.kt`, `db/entities/PlaylistFolderInfo.kt`
- Edited: `DownloadUtil.kt` (exporter hook and the auto re-export flow), `DatabaseDao.kt`
  (`playlistFoldersForSong`, `playlistSongMapCount`), `StorageSettings.kt`, `PreferenceKeys.kt`
  (`DownloadExportFolderKey`, `DownloadPlaylistFoldersKey`)

### 3. Audiobooks (phone folder and Audiobookshelf)
- New **Audiobooks** library filter.
- **Phone folder:** each sub-folder is a book. IDs are `audiobook:<...>`.
- **Audiobookshelf:** server login (tokens encrypted with Android Keystore AES-GCM, refreshed via
  `/auth/refresh`), library browsing, streaming with `?token=`, progress sync, and offline downloads to
  `filesDir/audiobookshelf/<id>`. IDs are `audiobook:abs:<itemId>:<index>`.
- Playback position is remembered for audiobooks and episodes (`remembersPosition()` in MusicService).
- New files: `audiobooks/AudiobookRepository.kt`, `audiobooks/abs/AbsAuthStore.kt`, `AbsClient.kt`,
  `AbsRepository.kt`, `constants/Audiobooks.kt`, `viewmodels/AudiobooksViewModel.kt`,
  `viewmodels/AudiobookshelfViewModel.kt`, `ui/screens/library/audiobooks/AudiobooksScreen.kt`,
  `AudiobookshelfContent.kt`, `res/drawable/audiobook.xml`
- Edited: `LibraryFilter.kt` (`AUDIOBOOKS`), `LibraryScreen.kt`, `AppearanceSettings.kt`, `MusicService.kt`,
  `PreferenceKeys.kt` (`AudiobooksFolderKey`, `AudiobooksSourceKey`, `AbsLibraryKey`)

### 4. Podcasts: search, follow, Spotify shows, full episode menu
- Podcast search uses Apple's free directory (iTunes Search API). Shows are read from their public RSS feeds.
- Followed shows are saved in `filesDir/podcast_follows.json` and appear in **Library → Podcasts → Episodes**.
- Your saved Spotify podcasts (`Spotify.myShows()`, libraryV3 "Podcasts" filter) are matched to their
  public feeds, because Spotify can't be streamed by other apps.
- Feed episodes become songs with id `podcast:<hash>`, `isEpisode = true`, and `localPath` set to the audio URL.
- Episodes use the normal `SongListItem` and the full `SongMenu`. YouTube-only actions (Start radio,
  Spotify playlist, YouTube sync and share) are hidden for non-YouTube songs.
- New files: `podcasts/PodcastDirectory.kt`, `viewmodels/PodcastDirectoryViewModel.kt`,
  `ui/screens/podcast/PodcastDirectoryScreen.kt` (route `podcast_directory?feed={feed}`),
  `spotify/models/SpotifyShow.kt`
- Edited: `LibraryPodcastsScreen.kt`, `LibraryViewModels.kt`, `Spotify.kt`, `SongMenu.kt`
  (`isFromYouTube` guards), `DownloadUtil.kt` (the `podcast:` branch resolves the feed URL),
  `MusicService.kt` (`isOnDeviceMediaId` covers `local:`, `audiobook:` and `podcast:`, plus extra extractors:
  Matroska, FragmentedMp4, Mp4, Mp3, Adts, Ogg, Flac, Wav)

### 5. News briefing (like Google News on Android Auto)
- Plays the newest bulletin from each chosen outlet back to back. Bulletins older than 2 days are skipped,
  and each one starts from the beginning.
- Source keys are `apple:<id>`, `search:<name>::<publisher>` and `feed:<url>` (followed podcasts).
- Built-in sources are listed in `NewsBriefing.BUILT_IN`. Google News' audio line-up is in `CATALOGUE_NAMES`,
  with categories. A `search:` source is checked for a public feed when it's ticked; Google-only feeds are
  reported as unavailable.
- **News sources** screen (`news_sources`) has search, category chips and reordering.
- New files: `podcasts/NewsBriefing.kt`, `viewmodels/NewsViewModel.kt`,
  `ui/screens/podcast/NewsBriefingCard.kt`, `NewsSourcesScreen.kt`
- Edited: `PreferenceKeys.kt` (`NewsSourcesKey`), `LibraryPodcastsScreen.kt` (card at the top of Episodes)

### 6. Android Auto
- New browse sections: Local files, Audiobooks (device and Audiobookshelf), Podcasts (saved, channels,
  followed feeds `podcasts/feed/<key>`) and News (`news/__all__`, `news/<index>`).
- Voice and text search of YouTube songs (the `android.media.browse.SEARCH_SUPPORTED` root extra).
- New file: `playback/AutoLibraryExtras.kt`
- Edited: `MediaLibrarySessionCallback.kt`, `AndroidAutoSettings.kt` (enum entries LOCAL_FILES, AUDIOBOOKS,
  PODCASTS and NEWS, with labels and icons)

### 7. In-app updater from this fork
- `Updater.kt`: `GITHUB_API_BASE` points at `rickevans86/Meld`.
- `UpdateInstaller.kt` (new) downloads the APK to `cacheDir/updates/update.apk` and installs it through
  FileProvider. It asks once for "install unknown apps" permission.
- Edited: `UpdaterSettings.kt` ("Download and install"), `SettingsScreen.kt`, `AccountSettings.kt`,
  `MainActivity.kt` (all call `UpdateInstaller.start`), `AndroidManifest.xml` (`REQUEST_INSTALL_PACKAGES`)

---

## Upstream files Wavelength edits

These are the files that can conflict during a merge, and what to re-apply in each.

| File | Wavelength edit |
|---|---|
| `app/build.gradle.kts` | `FORK_BUILD_NUMBER` versioning |
| `AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES` permission |
| `MainActivity.kt` | Update button calls `UpdateInstaller.start` |
| `constants/LibraryFilter.kt` | `AUDIOBOOKS` entry |
| `constants/PreferenceKeys.kt` | Keys listed under each feature |
| `db/DatabaseDao.kt` | `playlistFoldersForSong`, `playlistSongMapCount` |
| `playback/DownloadUtil.kt` | `DownloadExporter` hook, auto re-export, `podcast:` URL branch |
| `playback/MediaLibrarySessionCallback.kt` | `autoExtras` injection, search-supported extra, new section routing |
| `playback/MusicService.kt` | Audiobook and ABS repos, `remembersPosition()`, ABS headers, `isOnDeviceMediaId` in resolver and precache, extra extractors |
| `ui/menu/SongMenu.kt` | `isFromYouTube` guards |
| `ui/screens/NavigationBuilder.kt` | Routes `settings/local_folders`, `news_sources`, `podcast_directory?feed={feed}`, audiobook routes |
| `ui/screens/library/LibraryPodcastsScreen.kt` | Search icon, news card, followed feeds |
| `ui/screens/library/LibraryScreen.kt` | Audiobooks filter, which opens `AudiobooksScreen` |
| `ui/screens/library/local/LocalFilesScreen.kt` | Folder view and folder settings button (large change) |
| `ui/screens/settings/AccountSettings.kt`, `SettingsScreen.kt`, `UpdaterSettings.kt` | In-app install |
| `ui/screens/settings/AndroidAutoSettings.kt` | New Auto sections |
| `ui/screens/settings/AppearanceSettings.kt` | Audiobooks filter label |
| `ui/screens/settings/StorageSettings.kt` | Download folder, playlist folders and music folders settings |
| `utils/Updater.kt` | Fork repo URL |
| `viewmodels/LibraryViewModels.kt` | `followedFeeds` in `LibraryPodcastsViewModel` |
| `viewmodels/LocalFilesViewModel.kt` | Folder filter, de-duplication, picked folders (large change) |
| `spotify/Spotify.kt` | `myShows()` |
| `res/values/metrolist_strings.xml` | All Wavelength strings, appended |
| `res/mipmap-*/ic_launcher*.png` | Wavelength icon |

To see the exact diff at any time:
`git diff $(git merge-base HEAD upstream/main) HEAD -- <file>`

---

## Known limitations
- Spotify-exclusive podcasts can't be played, because they have no public feed.
- Some Google News sources (for example, the ABC NEWS city briefings) are Google-only and can't be added.
- Covers for audiobooks from a phone folder may not show in Android Auto.
- Exported download files aren't deleted when a song is removed from a playlist.

## Change log
- 0.9.2.x: Folder view for local files; Music folders setting.
- 0.9.2.x: Download folder setting with M4A export; playlist folders with YouTube and Spotify prefixes;
  Liked songs folders.
- 0.9.2.x: Audiobooks from a phone folder and Audiobookshelf (streaming, progress sync, downloads).
- 0.9.2.x: Android Auto sections (Local files, Audiobooks, Podcasts, News) and YouTube search.
- 0.9.2.x: Updater points at this fork, with in-app download and install. App renamed to Wavelength.
- 0.9.2.x: Spotify podcasts, podcast search, followed podcasts in the Episodes tab.
- 0.9.2.x: News briefing; fix for playing MP3 podcasts.
- 2026-11-03: Full episode menu for searched and followed podcast episodes.
- 2026-11-03: Google News audio source catalogue with search and categories.
- 2026-11-03: New red music-note app icon.
- 2026-11-03: Added this changes file.

# Wavelength changes

Wavelength is a fork of [Meld](https://github.com/FrancescoGrazioso/Meld), which itself builds on Metrolist.
This file records everything Wavelength adds or changes. When Meld releases an update, use it to merge
the update in or to rebuild these features on top of the new code.

- **Fork:** `rickevans86/Meld`, branch `feat/local-folders`
- **Upstream:** `FrancescoGrazioso/Meld`, branch `main`
- **Last upstream commit this is based on:** `371ffc90968bae1abd92a344bd30586ddabae16d`

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

The purple-to-orange headphones icon (an original design) is in `res/mipmap-*/ic_launcher*.png`: the background, foreground, monochrome and
legacy versions at every density. Upstream icon changes will conflict here; keep the Wavelength versions.

## Features

### 0. Navigation: bottom tabs and side menu
- Bottom tabs: Home, Search, Podcasts, News, Library (`Screens.MainScreens`). Together is no longer a bottom tab.
- Side menu (☰ in the top bar): every section (Home, Search, Library, Podcasts, News, Audiobooks, Radio,
  Navidrome, Local files, Together, History, Stats, Settings). The menu only opens from the button, and swiping
  only closes it, so it doesn't fight Android's back gesture. Drawer-only screens (`WavelengthDrawer.drawerOnlyRoutes`)
  still show the top and bottom bars.
- Podcasts and Audiobooks aren't Library chips any more; an old saved chip falls back to the main Library view.
- New files: `ui/component/WavelengthDrawer.kt`, `res/drawable/podcasts.xml`, `menu.xml`, `dns.xml`
- Edited: `ui/screens/Screens.kt` (Podcasts, News, Audiobooks screens; `MainScreens`), `NavigationBuilder.kt`
  (routes `podcasts`, `news`, `audiobooks`, `local_files`, `radio`, `subsonic`, `new_episodes`),
  `MainActivity.kt` (Scaffold wrapped in `ModalNavigationDrawer`, ☰ `navigationIcon`, `topLevelScreens`, titles,
  bottom bar on drawer-only routes), `LibraryScreen.kt`, `LibraryPodcastsScreen.kt` (`onDeselect` nullable),
  `AppearanceSettings.kt`

### 0b. Separate YouTube and Spotify homes
- Home is split: the default source is the Home tab (`home`); the other is in the side menu as "YouTube" or
  "Spotify" (`home_alt`, a second `HomeScreen` with nav argument `alt = true`).
- Setting: Settings → Integrations → Spotify → **Spotify as default home** (`DefaultHomeSourceKey`, values
  `youtube` / `spotify`). It replaces Meld's "Use Spotify for home" and "Spotify home only" switches; people who
  had "Use Spotify for home" on start with Spotify as default (`constants/HomeSource.kt`).
- The Spotify page shows only Spotify sections (or a sign-in hint); the YouTube page shows no Spotify sections.
  `HomeViewModel` reads `alt` from its SavedStateHandle and reloads when the default changes.
- New file: `constants/HomeSource.kt`
- Edited: `HomeViewModel.kt` (`useSpotifyHome` / `spotifyHomeOnly` / `load()` gating), `HomeScreen.kt` (sign-in
  hint), `SpotifySettings.kt`, `WavelengthDrawer.kt`, `NavigationBuilder.kt`, `MainActivity.kt` (title)

### 0c. Features settings
- The Features page starts with **Default home** (YouTube / Spotify), the same setting as in Spotify settings.
- Settings → Wavelength → **Features** (`settings/features`, `FeaturesSettings.kt`) turns off: the second music
  source (whichever of YouTube/Spotify isn't the default home), News, Navidrome, Audiobooks, Podcasts, Local files
  and Radio. Stored as `DisabledFeaturesKey` (set of ids); see `constants/Features.kt` (`WavelengthFeature`).
- Turned-off features are hidden from the bottom bar (`navigationItems` in `MainActivity`), the side menu
  (`WavelengthDrawerSheet`), Android Auto (`hiddenAutoSections()` filter on the root in
  `MediaLibrarySessionCallback`), and for Local files the Library chip. Turning off Spotify as the second source
  also hides the Spotify Android Auto sections.
- New files: `constants/Features.kt`, `ui/screens/settings/FeaturesSettings.kt`
- Edited: `MainActivity.kt`, `WavelengthDrawer.kt`, `MediaLibrarySessionCallback.kt`, `LibraryScreen.kt`,
  `SettingsScreen.kt`, `NavigationBuilder.kt`

### 0d. Removed integrations: Discord, Last.fm, Listen Together
- Switched off and hidden rather than deleted, so Meld updates still merge cleanly. Flags live in
  `RemovedIntegrations` (`constants/Features.kt`); set one to `false` to bring it back.
- `MusicService`: Discord RPC isn't initialised and its enabled flag is forced off; Last.fm scrobbling is forced off;
  `RemovedIntegrations.clearSavedState` runs on start and signs out of Discord and Last.fm and clears any Listen
  Together session so it never rejoins.
- Hidden: Discord and Last.fm on the Integrations screen (`IntegrationScreen.kt`), Listen Together in the side menu,
  the top bar button (`MainActivity`) and the player menu (`PlayerMenu.kt`).
- To delete them for real later: remove the `discord/`, `listentogether/` and `lastfm` modules, their settings screens,
  routes in `NavigationBuilder.kt`, and the guarded blocks above.

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

### 2b. Storage/Download settings and Wi-Fi only downloads
- The Storage settings page and its entry are titled "Storage/Download" (`storage_and_downloads`).
- **Download on Wi-Fi only** (`DownloadWifiOnlyKey`): `DownloadUtil` sets `downloadManager.requirements` to
  `NETWORK_UNMETERED` (else `NETWORK`), so downloads pause off Wi-Fi and resume automatically on Wi-Fi; the
  download notification shows that it's waiting. Metered Wi-Fi (e.g. a phone hotspot) counts as not Wi-Fi.
- Edited: `PreferenceKeys.kt`, `DownloadUtil.kt`, `StorageSettings.kt`, `SettingsScreen.kt`

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
- The search box has a paste button. A pasted link opens that show directly. Supported links: RSS feeds,
  Apple Podcasts, Pocket Casts (via its public API, then the page), Spotify, and any page with a feed link
  or `og:title` (`PodcastDirectory.resolveLink`).
- Followed shows are saved in `filesDir/podcast_follows.json` and appear in **Library → Podcasts → Episodes**.
- Your saved Spotify podcasts (`Spotify.myShows()`, libraryV3 "Podcasts" filter) are matched to their
  public feeds, because Spotify can't be streamed by other apps.
- Feed episodes become songs with id `podcast:<hash>`, `isEpisode = true`, and `localPath` set to the audio URL.
- Episodes use the normal `SongListItem` and the full `SongMenu`. YouTube-only actions (Start radio,
  Spotify playlist, YouTube sync and share) are hidden for non-YouTube songs.
- New files: `podcasts/PodcastDirectory.kt`, `viewmodels/PodcastDirectoryViewModel.kt`,
  `ui/screens/podcast/PodcastDirectoryScreen.kt` (route `podcast_directory?feed={feed}`),
  `spotify/models/SpotifyShow.kt`, `res/drawable/content_paste.xml`
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
- **News tab** (`news`, `NewsScreen.kt`): play-all card, then each source's newest bulletin (loaded in parallel,
  tap to play one). The card was removed from the Podcasts tab.
- Edited: `PreferenceKeys.kt` (`NewsSourcesKey`), `NewsViewModel.kt` (`refresh`, `bulletins`, `playOne`)

### 5a. Sorting podcasts and news
- Podcasts: sort button on the Podcasts tab (`SortMenuButton`, `constants/PodcastSort.kt`): Name, Latest episode,
  Recently followed, Publisher, plus Reverse. Keys `PodcastShowSortKey` / `PodcastShowSortReversedKey`. Applies to
  followed shows, the Channels tab (name sorts only; YouTube channels only carry a name) and Android Auto.
  `PodcastShow` gained `followedAt` and `latestEpisodeAt` (filled when a feed loads).
- News: sort button on the News tab: Your order, Latest bulletin, Name, Region, plus Reverse (`NewsSort`,
  `NewsSortKey`, `NewsSortReversedKey` in `NewsBriefing.kt`). Applies to the News tab, Android Auto and Play the news.
  Last publish times are remembered in `filesDir/news_latest.json`.

### 5e. Played state, per-show options and auto-download
- **Played / unplayed**: `podcasts/PlayedEpisodes.kt` keeps played episode ids in `filesDir/played_episodes.json`
  (no database change). Episodes are marked played when they finish (`MusicService` transition and STATE_ENDED).
  Buttons: a circle/tick on each show-page and New episodes row, and "Mark as played" in `SongMenu` for episodes.
  **Hide played** (`HidePlayedEpisodesKey`) applies to show pages, New episodes and Android Auto's New episodes.
- **Audiobooks**: "Mark as finished / not finished" on the book page. Phone books: `AudiobookRepository.setFinished`;
  Audiobookshelf: `AbsRepository.setFinished` updates the server progress.
- **Per-show speed**: chip on the show page; stored per feed URL in `filesDir/podcast_speeds.json`. `MusicService`
  applies it when an episode of that show starts and puts the earlier speed back afterwards.
- **Auto-download**: chip on the show page with "Keep N" (1–10). `PodcastAutoDownload` (WorkManager, new
  `work-runtime-ktx` dependency) runs every 6 hours on unmetered Wi-Fi: downloads the newest N unplayed episodes of
  each such show and removes only auto-downloaded ones that dropped out. Downloads go straight to the download
  manager (`DownloadUtil.downloadInBackground`) because Android blocks starting the download service from the
  background; the worker waits up to ~9 minutes for them. Turning it on also follows the show.
- New files: `podcasts/PlayedEpisodes.kt`, `podcasts/PodcastAutoDownload.kt`, `res/drawable/check_circle.xml`
- Edited: `PodcastDirectory.kt` (`PodcastShow.autoDownload`, `autoDownloadKeep`, `autoDownloadedIds`, speeds,
  episode→feed map), `PodcastDirectoryScreen.kt`, `NewEpisodesScreen.kt`, `SongMenu.kt`, `AudiobooksScreen.kt`,
  `AudiobookshelfContent.kt`, their view models, `DownloadUtil.kt`, `MusicService.kt`

### 5b. New episodes (combined)
- The "New episodes" card (Podcasts tab and Account screen) opens `new_episodes` instead of YouTube's RDPN list:
  recent episodes from followed feeds (last 30 days, up to 5 per show, newest first) followed by YouTube's
  new episodes from subscriptions. Play all queues both.
- New files: `viewmodels/NewEpisodesViewModel.kt`, `ui/screens/podcast/NewEpisodesScreen.kt`
- Edited: `PodcastDirectory.recentFromFollowed`, `LibraryPodcastsScreen.kt`, `AccountScreen.kt`

### 5c. Internet radio
- Stations from the free Radio Browser directory (radio-browser.info, several mirrors tried in turn):
  favourites (`filesDir/radio_favourites.json`), popular in the phone's country (default AU), genres, search.
- IDs: `radio:<stationuuid>`; songs saved with `localPath` = stream URL, album "Internet radio", artist = country.
- Playback: `MusicService.RadioAwareMediaSourceFactory` sends radio items to a separate, **uncached**
  `DefaultMediaSourceFactory` with the real stream URL (HLS via the new `media3-exoplayer-hls` dependency).
  Everything else uses the normal cached factory. ICY "now playing" titles come in through `onMetadata` and show
  on the Radio screen (the player notification shows them automatically).
- Radio can't be downloaded (SongMenu hides it; DownloadUtil refuses it). Plays are reported to Radio Browser.
- Search covers several sources at once (`RadioDirectory.search`): Radio Browser by name, region (`state`) and
  genre tag, plus TuneIn's public OPML search (`opml.radiotime.com/Search.ashx`, ids `radio:tunein-<guide_id>`).
  Results are merged with name matches first and duplicates dropped. TuneIn and playlist links are turned into
  the real stream (`resolveStream`): the chosen station before playing (so HLS is detected), the others by a
  `ResolvingDataSource` in the live player factory.
- In-app station finder (`radio_web?url=`, `RadioWebScreen.kt`): StreamURL.link and FMSTREAM open in a WebView;
  tapped stream links, background player requests, audio downloads and long-pressed links are caught
  (`looksLikeStream`) and offered as Play / Add station.
- Add station: paste a stream link (`RadioDirectory.addCustom`, id `radio:custom-<hash>`); .pls/.m3u playlist links
  are opened to find the stream. The screen links out to StreamURL.link and FMSTREAM for finding links (neither
  has a public API, and FMSTREAM blocks automated access, so they aren't searched in-app).
- New files: `radio/RadioDirectory.kt`, `viewmodels/RadioViewModel.kt`, `ui/screens/radio/RadioScreen.kt`
- Edited: `MusicService.kt`, `DownloadUtil.kt`, `SongMenu.kt`, `constants/Audiobooks.kt` (`RADIO_ID_PREFIX`),
  `gradle/libs.versions.toml` + `app/build.gradle.kts` (`media3-hls`)

### 5d. Navidrome / Subsonic
- Sign in with server address, username and password (password encrypted with an Android Keystore key,
  `SubsonicAuthStore`). Token auth (`t = md5(password + salt)`), API 1.16.1, client "Wavelength".
- Browse albums (recently added / played / most played / A–Z / random), artists, playlists, starred; search.
- IDs: `subsonic:<songId>`. The stream URL is built at play time in the MusicService resolver (no stored
  credentials in the song). Cover URLs use a fixed salt so images cache.
- Downloads use `format=raw` (the original file), so the M4A export and playlist folders apply.
- Album, playlist and Starred pages have a **Download** button for the whole list.
- Export folders follow the YouTube/Spotify pattern with a `Navidrome - ` prefix: `Navidrome - <album>` and
  `Navidrome - <playlist>` (membership recorded when an album/playlist is opened, saved in
  `filesDir/subsonic_folders.json`), `Navidrome - Starred` for starred songs, otherwise the song's album.
  See `DownloadExporter.folderNamesFor` and `SubsonicRepository.exportFolders`.
- "Now playing" is reported on start; a scrobble is sent after half the song or 4 minutes of listening.
- Liking a server song stars it on the server; starred state is read back when songs are loaded.
- New files: `subsonic/SubsonicAuthStore.kt`, `subsonic/SubsonicRepository.kt`, `viewmodels/SubsonicViewModel.kt`,
  `ui/screens/subsonic/SubsonicScreen.kt`
- Edited: `MusicService.kt`, `DownloadUtil.kt`, `SongMenu.kt` (like → star), `constants/Audiobooks.kt`
  (`SUBSONIC_ID_PREFIX`, both new prefixes in `isOnDeviceMediaId`)

### 6. Android Auto
- New browse sections: Local files, Audiobooks (device and Audiobookshelf), Podcasts (saved, channels,
  followed feeds `podcasts/feed/<key>`), News (`news/__all__`, `news/<index>`), Radio (`radio/<station>`:
  favourites, or popular stations when there are none) and Navidrome (`subsonic/starred`, `subsonic/playlists`,
  `subsonic/albums`, `subsonic/album/<id>/<song>`, `subsonic/playlist/<id>/<song>`).
- Voice and text search of YouTube songs (the `android.media.browse.SEARCH_SUPPORTED` root extra).
- Local files is a folder view: Shuffle all, then one folder per music folder (`local_files/folder/<key>`), each with
  Shuffle.
- Songs follows the Music folders settings (`AutoLibraryExtras.visibleLibrarySongs`), so excluded folders such as
  WhatsApp stay out; copies of the same song collapse (`LocalMusicFolders.uniqueCopies`: same title, artists and
  length). The app's Local files all-songs list uses the same rule (`LocalFilesViewModel.uniqueSongs`).
- Albums and Artists list saved ones first, then ones built from downloaded and local songs
  (`poolAlbums` / `poolArtists`, ids `lib_album/<key>`, `lib_artist/<key>`).
- **Continue listening** section (`continue`, `AndroidAutoSection.CONTINUE`, placed first): half-played podcast
  episodes (`DatabaseDao.inProgressEpisodes`) and unfinished phone and Audiobookshelf books, most recent first.
- **Voice** ("Hey Google, play … on Wavelength", `AutoLibraryExtras.voicePlay`, hooked in before YouTube search in
  `MediaLibrarySessionCallback.onSetMediaItems`): "the news"; a favourite station by name, or a radio search when
  the request says radio / station / FM; the newest episode of a followed podcast; a phone audiobook; Navidrome
  (exact album or song name, or anything when "Navidrome" / "my server" is said). Otherwise YouTube as before.
- Radio "now playing": the media session gets `RadioMetadataPlayer` (a `ForwardingPlayer`), which shows the
  station's song as the title and the station as artist for the notification, lock screen and Android Auto. The app's
  player and mini player use `withRadioNowPlaying()` (`radio/RadioNowPlaying.kt`).
- Podcasts page: New episodes on top (`podcasts/new`, the combined list, cached 5 minutes), then Saved episodes,
  then channels and followed feeds in the chosen podcast sort.
- New file: `playback/AutoLibraryExtras.kt`
- Edited: `MediaLibrarySessionCallback.kt` (`autoArtists`, `autoAlbums`, Songs filtering), `AndroidAutoSettings.kt` (enum entries LOCAL_FILES, AUDIOBOOKS,
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
| `viewmodels/HomeViewModel.kt`, `ui/screens/HomeScreen.kt` | YouTube/Spotify home split (see 0b) |
| `ui/screens/settings/integrations/SpotifySettings.kt` | "Spotify as default home" switch replaces Meld's two home switches |
| `ui/screens/Screens.kt` | Podcasts, News and Audiobooks screens; bottom tab list |
| `ui/screens/AccountScreen.kt` | New episodes card opens `new_episodes` |
| `playback/AutoLibraryExtras.kt` (Wavelength file) | Radio and Navidrome sections |
| `gradle/libs.versions.toml` | `media3-hls`, `work-runtime` |
| `db/DatabaseDao.kt` | `inProgressEpisodes` (also `playlistFoldersForSong`, `playlistSongMapCount`) |
| `ui/player/Player.kt`, `ui/player/MiniPlayer.kt` | Radio now-playing via `withRadioNowPlaying()` |
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
- 2026-11-03: Fixed a crash when podcast search returns the same show twice (results, episodes, Spotify shows and news sources are de-duplicated).
- 2026-11-03: Paste button and pasted-link support in podcast search (Pocket Casts, Apple, Spotify, RSS).
- 2026-11-03: Replaced the app icon with an original headphones and sound-bars design.
- 2026-11-03: Podcasts and Audiobooks moved from Library chips to their own bottom tabs.
- 2026-11-03: Side menu; News as its own tab; internet radio; Navidrome / Subsonic; combined New episodes.
- 2026-11-03: Radio: add stations from pasted stream links; links to StreamURL.link and FMSTREAM.
- 2026-11-03: Radio: TuneIn and region/genre search; in-app station finder for StreamURL.link and FMSTREAM.
- 2026-11-03: Navidrome: download whole albums/playlists/starred; exports into `Navidrome - ` folders.
- 2026-11-04: Separate YouTube and Spotify homes, with a setting for which one is the Home tab.
- 2026-11-04: Storage settings renamed Storage/Download; Wi-Fi only downloads that pause off Wi-Fi.
- 2026-11-04: Features settings to turn off the second source, News, Navidrome, Audiobooks, Podcasts, Local files, Radio.
- 2026-11-04: Android Auto: Local files folders, Songs follows Music folders, Albums/Artists from downloads and local songs, New episodes; podcast and news sorting; Default home on Features page; Discord, Last.fm and Listen Together switched off and hidden; duplicate copies collapsed in song lists.
- 2026-11-04: Voice commands, Continue listening, podcast auto-download on Wi-Fi, played/unplayed for podcasts and audiobooks, hide played, per-show speed, radio now-playing on the player and Android Auto.
- 2026-11-04: Merged Meld upstream (Spotify GQL hash update).

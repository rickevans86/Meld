# Planned file modifications

## In progress — PODCAST-STARTUP-CRASH, ChatGPT Codex, 2026-11-19

User supplied crash report for 0.9.2.99 on Samsung Android 17: MusicService creation fails with LoadControl.getBackBufferDurationUs not implemented. Prior policy-only tests missed real ExoPlayer construction. Replace Kotlin interface delegation with explicit forwarding of allocator, back-buffer and preloading callbacks; add actual player construction/release regression. Branch fix/podcast-load-control-startup from main e22f2fd7; no open PR overlap; Issues disabled, temporary task ID. Scope PodcastLoadControl, its tests and AI records. Buffer setting retained. No schema/version/signing changes. Six targeted tests passed, including actual ExoPlayer construction/release; FOSS debug APK built and diff checked. Full CI/release pending; physical device untested. Standing fix/main/build/release workflow continues.


## Current — 0.9.2.99, ChatGPT Codex, 2026-11-19

PODCAST-STARTUP-BUFFER completed in [PR #20](https://github.com/rickevans86/Meld/pull/20), final feature 993e463c, main/tag `086edc460b89e4c2a6f95824388a8eda43bd8216`. [Wavelength 0.9.2.99](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.99) published by signed run `44506134232`. Final full PR CI `44503794400` passed app/innertube tests, APK build/upload and lint. Five targeted local regression tests and FOSS debug build passed; diff and English XML/name checks passed. Superseded checks were replaced after correcting the startup watchdog; final code passed.

Settings → Features → Podcasts → Podcast startup buffer: default 2 minutes; choices 2, 4, 6, …, 20 minutes stored in DataStore. PodcastLoadControl uses the relevant period's media item and a shared allocator with separate normal/podcast policies. Podcast loading prioritizes time over the audio byte cap and continues beyond the previous 50-second limit, maintaining the chosen requirement plus 30 seconds. Startup waits for the chosen media duration or the known remaining episode duration, whichever is shorter; completed downloads/local files bypass the requirement. Normal music startup and the 2-second stall-recovery threshold are retained. Initial recovery no longer clears a buffer that is still growing.

Classification includes RSS/native episode metadata, selected YouTube podcast playlist origin (including VL IDs), local/cached playlist membership and a conservative initial-membership window. Ordinary YouTube videos in marked podcast playlists therefore use the podcast policy without permanently rewriting song metadata. The same player policy serves phone and Android Auto. Setting changes apply to subsequent buffering decisions; existing ready playback is not forcibly paused.

Public APK downloaded/verified: `com.rickevans86.wavelength`, non-debuggable, `0.9.2.99`, code `280099` greater than 280098, same certificate SHA-256 `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Release tag matches the tested merge. Scope: MusicService, PodcastLoadControl, PodcastBuffer preference/classification, YouTubePlaylistQueue origin accessor, FeaturesSettings, default English resources, PodcastLoadControlTest and shared AI records. No Room schema/manual version/signing changes.

AI: ChatGPT Codex. Issues disabled; temporary task ID, no remote claim. Standing implementation/main/build/release authorization used. All six shared records and task updated while preserving prior AI history; no active reservation or implementation/release work remains. Next: owner validates actual streamed RSS and YouTube-playlist buffering at 2/4 minutes, short clips, downloaded files and the head unit. Tests cover policy/timeline decisions; real network buffering/audio decoding and physical phone/head-unit validation remain outstanding.


## Previous release — 0.9.2.98, ChatGPT Codex, 2026-11-18

AUTO-OFFLINE-MODE completed in [PR #19](https://github.com/rickevans86/Meld/pull/19), feature 677ec709, main/tag `341620f41a3223e90a53f75fd031ab3c825afa74`. [Wavelength 0.9.2.98](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.98) published by signed workflow 44281137089. Full CI 44279339922 passed app/innertube tests, APK build/upload and lint. Two targeted local regression tests and FOSS debug build passed; diff checked.

Android Auto reads the phone's existing OfflineModeKey. Offline root exposes Podcasts, Audiobooks and Local files; podcasts offer Downloaded only, books show paginated downloaded device/ABS books without requiring an authenticated server session. Stale online browse nodes and online search results are blocked before catalogue requests. Mode changes notify root/standard nodes and tracked subscribed parents, including already-open pages. Turning the switch off restores ordinary car browsing. Playback restrictions and progress reconnect sync from 0.9.2.97 remain intact. Android Auto owns rendering/refresh timing; no physical head-unit success claimed.

Public APK downloaded and verified: `com.rickevans86.wavelength`, non-debuggable, `0.9.2.98` / code `280098`, higher than 280097, same certificate SHA-256 `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Tag matches tested merge. No schema/manual version/signing changes. Scope: MediaLibrarySessionCallback, AutoLibraryExtras, AutoAudiobookLibrary, MusicService, AutoOfflineMode helper/test and AI records.

AI: ChatGPT Codex. Issues disabled; temporary task ID, no remote claim. Standing main/build/release authorization used. All six shared records and task updated; earlier agents' history preserved. No active file reservation or implementation/release work remains. Next: owner checks toggling phone Offline mode while Auto is connected, downloaded playback and normal-menu restoration on the actual head unit.



## Previous release — 0.9.2.97, ChatGPT Codex, 2026-11-17 (Melbourne)

MANUAL-OFFLINE-MODE completed and [released as Wavelength 0.9.2.97](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.97). Main implementation [PR #17](https://github.com/rickevans86/Meld/pull/17), feature a2d10b1a, merge cb80fb0a; final RSS guard [PR #18](https://github.com/rickevans86/Meld/pull/18), feature a9bb6e70, main/tag `2b3a87e379dc62463c79b86cf00eaf1aea727eb4`. Full PR CI 44014179903 and 44016930349 passed app/innertube tests, FOSS debug build/upload and lint. Signed release run `44018249348` succeeded. Earlier automatic run 96 (44015546132) was cancelled before publication to include the RSS guard; no 0.9.2.96 release was published.

Phone drawer switch below Wavelength persists in DataStore. It hides online sources/search/mixed history from drawer and bottom navigation, redirects hidden pages to Podcasts, and restricts podcast/audiobook views to Downloaded. Local files, settings and local statistics remain reachable; saved navigation configuration is retained for normal mode. The effective connectivity observer respects manual Offline mode. Podcast catalog refreshes and ABS authenticated calls are skipped; new stream resolution is blocked while completed downloads and actual local files remain playable. RSS IDs must not be treated as local files merely because isOnDeviceMediaId includes them. ABS direct paths still require an existing downloaded file.

ABS local positions persist pending-sync state and duration in the existing JSON file, including finished/reset edits. Effective reconnection retries pending progress immediately and every minute while the service runs; failures remain queued and app restarts recover the queue. Newer server progress wins conflicts, and compare-and-replace prevents a request in flight from clearing a newer phone edit. Offline mode must be off and network/server/authentication available for upload; if the app/service is stopped, retry resumes on next launch. This is an app content/playback mode, not an OS-wide network firewall.

Local verification: initial full 276 app + 10 innertube tests and FOSS debug build passed; final guard's 9 targeted tests and debug build passed. Full final-code CI also passed. Diff/default English XML/name checks passed. Restart persistence is tested with explicit test sign-in because Robolectric cannot restore the Android Keystore token. Public APK downloaded and verified: application ID `com.rickevans86.wavelength`, version `0.9.2.97`, code `280097` greater than 280095, non-debuggable, same signing SHA-256 `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Tag matches the tested merge. No Room schema, manual version, signing or app-ID changes.

AI: ChatGPT Codex. Continuing the user's standing main/build/release/AI-record authorization. Issues disabled; temporary task ID used, no remote Issue claim asserted. All six shared records and [task](tasks/MANUAL-OFFLINE-MODE.md) updated while preserving earlier AI history. No implementation/release work or active file reservation remains. Next: owner checks switch/navigation, downloaded playback and reconnect progress on a physical phone; physical head-unit/live-server and real decoder validation remain outstanding.

Task AUDIOBOOK-PHONE / PR #1, Codex, completed and released as v0.9.2.81 on main. No active file reservation. Remote Issue claim was unavailable.

- ui/player/Player.kt and AudiobookPlayerControls.kt
- audiobooks/AudiobookPlaybackSettings.kt and AudiobookBookmarks.kt
- ui/player/MiniPlayer.kt and ui/screens/settings/FeaturesSettings.kt
- audiobooks/abs/AbsRepository.kt and AbsChapterNavigation.kt
- .github/workflows/build_local_folders.yml (enable the existing signed release pipeline on main)
- playback/MusicService.kt
- default English values/metrolist_strings.xml
- relevant audiobook tests, .ai/* and additive AGENTS.md instructions

Other agents may work on these files after checking current main and active PRs. The list above records the completed scope, not an active lock.

## Coordination audit — Codex

Task COORDINATION-AUDIT-2026-11-13; branch chore/ai-coordination-audit. Completed file scope: .ai/PROJECT_STATE.md, TASK_BOARD.md, AI_ACTIVITY_LOG.md, HANDOFF.md and FILE_OWNERSHIP.md. Documentation review pending; no application/workflow files reserved.

Proposed CI-NIGHTLY-SIGNING follow-up: .github/workflows/build.yml only after access/ownership is established. No AI currently claims that implementation. The working FOSS release workflow is outside the proposed change scope.

## Historical review reservation — AUTO-AUDIOBOOKS, Codex, PR #3 (released)

Branch feat/auto-audiobook-sections, code 11965b3f. Implemented files: AutoAudiobookLibrary, AutoLibraryExtras, MediaLibrarySessionCallback, MusicService; AutoAudiobookSections and AutoAudiobookSectionSettings; AndroidAutoSettings; AbsClient/AbsRepository; default English strings, four auto_audiobook_* drawables, three focused tests and .ai/tasks/AUTO-AUDIOBOOKS.md. Keep these changes intact while the feature PR is reviewed. GitHub PRs remain authoritative; no Issue claim succeeded.

Root .ai coordination files are updated through this documentation PR #2, not the code PR, so the two PRs do not edit the same files. No signing workflow or credential file is reserved by this feature.

Pagination follow-up (Claude, PR #3, completed; review reservation only): playback/AutoBrowsePaging.kt (new), playback/AutoAudiobookLibrary.kt, playback/AutoLibraryExtras.kt (Downloaded paging), app/src/test/.../playback/AutoBrowsePagingTest.kt (new), .ai/tasks/AUTO-AUDIOBOOKS.md and one WAVELENGTH_CHANGES.md line on the feature branch.

PHONE-AUDIOBOOK-TABS (Claude, PR #4, completed and released; no active lock): audiobooks/AudiobookTabs.kt (new), ui/screens/library/audiobooks/AudiobookTabsUi.kt (new), AudiobooksScreen.kt, AudiobookshelfContent.kt, ui/screens/settings/AudiobookTabSettings.kt (new), FeaturesSettings.kt, AudiobooksViewModel.kt, AudiobookshelfViewModel.kt, AbsClient.kt/AbsRepository.kt (optional sort), default English strings, AudiobookTabsTest, README, WAVELENGTH_CHANGES and the task record.

WAVELENGTH-APP-ID + HOME-AUDIOBOOK-FILTER (Claude, released 0.9.2.85; no active lock): .github/workflows/build_local_folders.yml (app id), BackupRestoreViewModel.kt + WavelengthBackupFiles, ui/component/WavelengthMove.kt, UpdaterSettings.kt, MainActivity.kt, AndroidManifest.xml (<queries>), constants/Audiobooks.kt (isYouTubeMediaId), ui/screens/HomeScreen.kt, tests, prompt.md, README/WAVELENGTH_CHANGES and task records.
## Completed — AUTO-DOWNLOADS-FAVOURITES, Codex, 2026-11-17 (Melbourne)

Branch fix/auto-downloads-podcast-favourites from main 442ed05a. User authorized implementation, main publication, build/release and AI-record updates. Temporary task: Issues disabled; only open PR #2 contains coordination documents, no application overlap. Planned scope: MediaLibrarySessionCallback, AutoLibraryExtras, MusicService, AndroidAutoSettings, podcast favourite preferences/sorting and phone show list, default English strings, focused tests, .ai records. No schema or manual version changes. Incorporates the newer shared records from PR #2 without discarding Claude's history.

AUTO-DOWNLOADS-FAVOURITES scope addition (Codex): AutoAudiobookLibrary, to retain a dedicated paginated Downloaded tile inside Audiobooks after removing the combined root. Existing download/scan/session refreshes include this node.

AUTO-DOWNLOADS-FAVOURITES: completed/released 0.9.2.93, main 4bc049dfa230c1c805f63502a538aebf75586866; no active lock. Final scope also includes AutoDownloadedSongs, AutoPodcastFavourites, PodcastFavourites, two star vectors, two test classes and all six shared root AI records.

## Completed — PHONE-PODCAST-FAVOURITES, Codex, 2026-11-17 (Sydney)

Branch fix/shared-podcast-favourites from main f76f8a49. User authorized implementation/main/release/AI records. No open PR or active application overlap. Scope: PodcastFavourites + migration/filter helpers; App startup if needed; LibraryPodcastsScreen; AutoLibraryExtras; HomeViewModel/HomeScreen; lightweight DAO queries (no schema); tests; .ai records. Task record .ai/tasks/PHONE-PODCAST-FAVOURITES.md.

PHONE-PODCAST-FAVOURITES completed/released 0.9.2.94, PR #15 / main 650649af7175bcd072bec9b24d5d3c493216bbb7; no active reservation. Final file scope is recorded in the task; all shared records updated.

## Completed — OFFLINE-LIBRARY-ACCESS, Codex, 2026-11-17

Branch fix/offline-library-access from main 93204481. Local implementation of user-reported ABS offline bug and broader airplane-mode download access. Scope: AbsRepository; AudiobookshelfViewModel/Content; MusicService/cache helper; offline downloads UI; HomeScreen/SubsonicScreen/MediaServerScreen; default English strings; tests; AI records. Continuing the user’s standing main/build/release workflow. No overlapping PR found; Issues disabled.

OFFLINE-LIBRARY-ACCESS completed/released 0.9.2.95, PR #16 / main b3c1911d, release run 43999649417. No active reservation remains. Final scope and verification are in the task record.

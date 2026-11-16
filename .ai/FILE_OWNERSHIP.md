# Planned file modifications

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

## Active — PHONE-PODCAST-FAVOURITES, Codex, 2026-11-17 (Sydney)

Branch fix/shared-podcast-favourites from main f76f8a49. User authorized implementation/main/release/AI records. No open PR or active application overlap. Scope: PodcastFavourites + migration/filter helpers; App startup if needed; LibraryPodcastsScreen; AutoLibraryExtras; HomeViewModel/HomeScreen; lightweight DAO queries (no schema); tests; .ai records. Task record .ai/tasks/PHONE-PODCAST-FAVOURITES.md.

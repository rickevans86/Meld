# Handoff

## Current — 0.9.2.101, ChatGPT Codex, 2026-11-19

PODCAST-PLAYLIST-ROWS completed in [PR #22](https://github.com/rickevans86/Meld/pull/22), feature c5130beda66f24b6e69c726b1b9fba8ca250dca2, main/tag `b02af0eba20a9c60f1ee3e3990a455d453a77a9c`. [Wavelength 0.9.2.101](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.101) published by signed workflow `44528629336`. Full CI `44526413518` passed app/innertube tests, APK build/upload and lint. Two targeted local regression tests and FOSS debug APK passed; diff checked. Release audiobook playback regression also passed.

Marked YouTube podcast playlists now use the common show row with a three-dot Favourite channel menu. Exact episode totals are fetched and cached on refresh, retaining the previous successful total on failure. A complete nonempty first page can supply a fallback; partial pages and abbreviated totals are not treated as exact counts. Unknown totals display YouTube without a false zero. Local marked playlists also use episode labels and the common menu. Rows combine with native channels/feeds before sorting and favourite-first placement, so favourites appear below Episodes for later. Phone and Android Auto use the same normalized online/local playlist favourite IDs and saved preference; Auto validates configured playlist folders and exposes its native favourite actions. Existing navigation and playback routes remain intact.

Public APK downloaded and verified: `com.rickevans86.wavelength`, non-debuggable, `0.9.2.101`, code `280101` greater than 280100, same signing certificate SHA-256 `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Tag matches the tested merge. No playback/load-control, schema, manual version or signing changes. Scope: LibraryPodcastsScreen, LibraryPodcastsViewModel, PodcastFavourites, AutoLibraryExtras, count helper/tests and AI records.

AI: ChatGPT Codex. Issues disabled; temporary task ID, no remote claim. No overlapping PR at task start. Standing main/build/release authorization used. All six shared records and task updated; earlier history preserved. No active file reservation or implementation/release work remains. Next owner check: install the update, open Podcasts with authenticated YouTube connectivity, confirm the actual count after refresh, use the playlist three-dot Favourite channel action and confirm placement below Episodes for later; verify the same saved favourite on physical Android Auto. Live authenticated playlist, physical phone/head-unit tests remain outstanding; unit tests verify count parsing, shared identity and favourite partitioning, not the actual user account or device UI.


## Previous release — 0.9.2.100, ChatGPT Codex, 2026-11-19

PODCAST-STARTUP-CRASH completed in [PR #21](https://github.com/rickevans86/Meld/pull/21), feature c5241578, main/tag `f3defc7095e7689ddb852b22e4f7c04774350733`. [Wavelength 0.9.2.100](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.100) published by signed workflow `44513054423`. Full CI `44510223393` passed app/innertube tests, APK build/upload and lint. Six targeted local tests, including real ExoPlayer construction/release, and FOSS debug APK passed; diff checked.

User's 0.9.2.99 Samsung Android 17 crash report identified MusicService creation failing with IllegalStateException: getBackBufferDurationUs not implemented. The podcast LoadControl wrapper used Kotlin interface delegation, which did not forward Java default callbacks. Prior tests covered buffering decisions but missed actual ExoPlayer creation; 0.9.2.99 was therefore broken at service startup despite passing those checks. The fix implements LoadControl explicitly and forwards allocator, back-buffer duration, keyframe retention and preloading to the normal policy. Existing lifecycle and buffering logic remains. The new regression checks both back-buffer callbacks and constructs/releases a real ExoPlayer for both podcast classifications.

Public APK downloaded and verified: `com.rickevans86.wavelength`, non-debuggable, `0.9.2.100`, code `280100` greater than 280099, same signing certificate SHA-256 `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Tag matches tested merge. Install over the existing app; no uninstall/data reset is needed. Podcast buffer settings, downloads, positions and other data are retained. Scope: PodcastLoadControl, PodcastLoadControlTest and AI records only. No schema/manual version/signing changes.

AI: ChatGPT Codex. Issues disabled; temporary task ID, no remote claim. Standing fix/main/build/release authorization used. All six shared records and task updated, earlier history preserved. No active reservation or implementation/release work remains. Next: owner installs the replacement and confirms startup on the affected Samsung device; physical Android 17/head-unit and live streaming validation remain outstanding. Robolectric tests exercise actual player creation but are not device instrumentation tests.


## Previous release — 0.9.2.99 (startup crash; superseded), ChatGPT Codex, 2026-11-19

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

## Previous release — 0.9.2.95, Codex, 2026-11-17 (Melbourne)

OFFLINE-LIBRARY-ACCESS completed by ChatGPT Codex in [PR #16](https://github.com/rickevans86/Meld/pull/16), feature `fb7e0e5c`, main merge `b3c1911d3f3433eee648ed4698d23a003b338496`. [Wavelength 0.9.2.95](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.95) published by signed release run `43999649417`. PR CI `43998334078` passed full tests, FOSS debug build/upload and lint. Local verification: 270 app + 10 innertube tests passed, FOSS debug build, diff/resource checks passed.

Downloaded ABS metadata/audio/covers/resume now use local data before server/auth; missing completed-book files fail locally, failed details stop spinning. Completed Media3 downloads bypass remote stream auth/quality resolution for phone and Android Auto. Airplane mode exposes source-specific downloaded music and podcast downloads; music-server roots also offer a Downloads entry when the network is present but the server is unavailable. Existing local-file playback retained. Streaming/discovery/live radio still require connectivity.

Public APK downloaded and verified: `com.rickevans86.wavelength`, `0.9.2.95`, code `280095` (higher than 280094), non-debuggable, same SHA-256 signing certificate `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Tag points to the tested main merge. No schema/manual version/signing/app-ID changes. Continuing the user's standing main/build/release/AI-record workflow. Issues disabled; temporary task ID used without asserting an Issue claim.

No implementation/release work or active reservation remains. Physical phone/head-unit airplane-mode playback, actual audio decoding and live authenticated server reconnection remain untested; owner device validation is the next action. All six shared AI records and [task record](tasks/OFFLINE-LIBRARY-ACCESS.md) updated; prior AI history retained.

## Previous release — 0.9.2.94, Codex, 2026-11-17 (Sydney)

Agent: ChatGPT Codex. Task: PHONE-PODCAST-FAVOURITES / PR #15. Status: merged, released 0.9.2.94 and public APK verified. PR #15, main merge 650649af7175bcd072bec9b24d5d3c493216bbb7, signed release run 43985275212; final PR CI 43983342034 on 567eeb20 passed full app/innertube tests, FOSS APK build/upload and lint. Nine selected local tests and FOSS debug APK passed.

Completed: shared phone/Auto favourite actions and ordering, safe podcast pin migration, explicit podcast membership exclusions on YouTube music home. All shared AI records synchronized on main; no implementation/release work or file reservation remains. Next: owner tests phone favourite menus, existing-pin migration, car favourites and playlist filtering with authenticated YouTube. Network-first uncached membership and host action placement remain runtime limits. Issues disabled; temporary task/PR used without claiming an Issue assignment.

## Previous release — 0.9.2.93, Codex, 2026-11-17 (Melbourne)

Agent: ChatGPT Codex. Task: AUTO-DOWNLOADS-FAVOURITES / PR #14. Status: merged into main as 4bc049dfa230c1c805f63502a538aebf75586866; signed release 0.9.2.93, run 43951768865, published and APK verified (same app ID/cert as 0.9.2.92; higher version code). User authorized all publication steps.

Completed: YouTube music-only downloads/count/queues; podcast-show favourite browse actions and persisted favourite-first ranking; duplicate Downloaded root removal; paginated offline-books entry inside Audiobooks. Fourteen selected local tests/build and full PR CI 43950033049 passed. No implementation or release work remains. Next: owner checks favourites and source-specific downloads on the head-unit; Android Auto owns action placement/support. Shared records synchronized on main, PR #2 superseded/closed, no active file reservation. Issues disabled; no Issue claim asserted. Full evidence in [task record](tasks/AUTO-DOWNLOADS-FAVOURITES.md).

## Historical records

Agent: Codex
Task: AUDIOBOOK-PHONE / PR #1 (Issues disabled; no successful Issue claim)
Completed: phone controls, configurable rewind/forward, chapter picker, device-local bookmarks, speed and sleep timer access; Audiobookshelf embedded chapter mapping; mini-player/media-session skips; shared records; merge to main and public release.
In progress: none for this implementation/release
Branch/PR: main; https://github.com/rickevans86/Meld/pull/1 (merged)
Code commit: d3b6df1e29f61bb2fa2ed7fc3d3e693957d4cd1c
Release: https://github.com/rickevans86/Meld/releases/tag/v0.9.2.81 (published, target main)
Tests: six focused audiobook tests and local FOSS debug build passed; full PR unit suite/build/lint passed in run 43359651609; signed release run 43361593932 succeeded. Downloaded APK verified against 0.9.2.80: same certificate and com.meld.app.debug application ID, higher version code 280081.
Limitations: not tested on a physical phone or Android Auto hardware; bookmarks are device-local; local embedded chapter extraction is not added.
Blockers: none for published FOSS release. GitHub Issues disabled. Separate legacy GMS nightly run 43361593972 lacks KEYSTORE and DEBUG_KEYSTORE signing secrets; release run uses DEBUG_KEYSTORE_BASE64 successfully. No secret values were accessed or changed.
Next action: install the released APK as an update and check chapter navigation, skip settings, bookmarks, speed and sleep on a phone. For future work, inspect current GitHub state and address the optional nightly signing configuration separately.
Shared records updated: yes, main (.ai and additive AGENTS instructions)
Remote synchronization: confirmed; main merge preserves feat/local-folders history. Release tag resolves to the tested main code commit. Final documentation-only follow-up uses [skip ci] to avoid rebuilding an unchanged APK.

## Latest session — coordination audit

Agent: Codex
Task: COORDINATION-AUDIT-2026-11-13 (temporary ID; Issues disabled)
Completed: fresh remote audit, verification of other agents' recorded work, identification and scoping of the next unclaimed follow-up, collaboration-record updates.
In progress: documentation PR review; no application implementation started.
Branch/PR: chore/ai-coordination-audit; https://github.com/rickevans86/Meld/pull/2 (open, documentation review).
Files: .ai/PROJECT_STATE.md, TASK_BOARD.md, AI_ACTIVITY_LOG.md, HANDOFF.md, FILE_OWNERSHIP.md.
Tests: read-only remote/ref/PR/release/workflow checks; documentation diff checks. No app build is necessary for documentation-only changes; historical build results above belong to PR #1, not a new build this session.
Blockers: Issues disabled; signing-secret metadata returned HTTP 403, so current credential readiness is unknown.
Next action: owner verifies the intended GMS nightly credentials in repository settings; then an agent claims CI-NIGHTLY-SIGNING, preserves signing identity, and validates that optional pipeline. Do not ask for secret values in chat. Audiobook device testing remains outstanding.
Shared records updated: on this documentation branch, pending review.
Remote synchronization: confirmed against main 4b1e9295 and all three current remote branch heads. No new remote work by other agents was found at audit start.

## Latest handoff — new app ID + YouTube-only home released (Claude, 2026-11-14)

Released Wavelength 0.9.2.85 (merge 0a221b60, run 43522774134): app id com.rickevans86.wavelength and YouTube-only home rows. APK verified — new id, version code 280085, same signing cert. Main head is a [skip ci] records commit.
On-device migration (owner): install 0.9.2.85 (it is a SECOND app, not an update) -> restore the backup made in 0.9.2.84 -> sign in to Audiobookshelf/media servers and reselect folders -> re-download offline media -> remove the old app (new app guides this). Updates after that are in-place.
Open items: PR #2 (records) still open for review; CI-NIGHTLY-SIGNING (old GMS "Build APKs") still fails at signing and is unassigned; physical car/phone testing of recent releases not yet done by the owner.

## Earlier handoff — PHONE-AUDIOBOOK-TABS released (Claude, 2026-11-13)

Released Wavelength 0.9.2.83 (PR #4, merge e6e32e0c, release run 43413832192). The APK was verified as an update: same certificate, version code 280083.
Next action: the owner updates via Settings → Updater and checks the phone tabs, Sort (including the Audiobookshelf server sort), Continue listening resume and the tab-order setting, plus the 0.9.2.82 car pages. Open items: PR #2 (records) awaits review, and CI-NIGHTLY-SIGNING is unassigned.

## Handoff — AUTO-AUDIOBOOKS released (Claude, 2026-11-13)

Released: Wavelength 0.9.2.82 (https://github.com/rickevans86/Meld/releases/tag/v0.9.2.82). The owner authorized the release. PR #3 was merged into main as 5e6a7b47, and the release run 43408784962 succeeded. The APK was verified as an update to 0.9.2.81: same certificate, com.meld.app.debug, version code 280082. Main is now cc955f6c ([skip ci] records).
Next action: the owner updates via Settings → Updater and tests the Audiobooks and Downloaded tabs, including the More pages, in the car with Audiobookshelf. PR #2, this records PR, is still open for review. CI-NIGHTLY-SIGNING remains unassigned.

## Feature handoff — AUTO-AUDIOBOOKS pagination (Claude)

Agent: Claude
Task: AUTO-AUDIOBOOKS (temporary ID; Issues disabled)
Completed: explicit Android Auto pagination under the 100-item cap for every audiobook list and the Downloaded root. That covers combined device + server Library pages, server groups + app playlists, group contents, app playlists and their contents, and Downloaded; each More folder stays inside the visible page. Older browse IDs and playback routes are preserved. Also added pagination tests, task-record and change-log updates, the PR #3 description and these shared records.
In progress: none. Owner review, car testing and live-server testing are pending.
Branch/PR: feat/auto-audiobook-sections / https://github.com/rickevans86/Meld/pull/3 (open). Code 640e3c95 and 23d4c232; docs 2d9255da [skip ci].
Tests: the 6 new AutoBrowsePagingTest tests passed locally on a standalone Kotlin compiler. CI run 43403522870 on 23d4c232 passed the full unit suite, FOSS debug build, review APK artifact and lint. No local Gradle build (no Android SDK in Claude's workspace).
Blockers: none for code. Issues disabled.
Next action: owner installs the PR #3 review APK (artifact app-universal-debug-pr-3) or waits for an authorized release. Then check the Audiobooks/Downloaded tabs and More pages on a car display with an Audiobookshelf server that has more than 98 books. Merge to main and any release need new explicit instructions.
Shared records updated: yes (this PR #2 branch, plus the task file in PR #3).
Remote synchronization: main 4b1e9295; PR #3 head is the docs commit 2d9255da on top of 23d4c232; release 0.9.2.81 is unchanged.

## Latest feature handoff — AUTO-AUDIOBOOKS

Agent: Codex
Completed: one flat Android Auto Audiobooks page with grouped grid rows (Continue Listening, Recently Added, Browse, Continue Series); Library/Series/Collections/Playlists tiles; separate Downloaded root for books/podcasts; saved section order/visibility in phone Android Auto settings; real Audiobookshelf browsing, app audiobook playlists, pagination and refresh notifications. Existing resume/playback code is reused.
In progress: feature PR review and CI verification; no main merge/release authorized for this new feature.
Branch/PR: feat/auto-audiobook-sections / https://github.com/rickevans86/Meld/pull/3; implementation 11965b3f.
Files: see FILE_OWNERSHIP.md and the feature's dedicated .ai/tasks/AUTO-AUDIOBOOKS.md record.
Tests: eight focused tests and local :app:assembleFossDebug passed; diff and edited XML validation passed. PR checks run separately. Car hardware and live-server validation remain outstanding.
Blockers: Issues disabled, so this task is identified by temporary ID/PR rather than a successful Issue claim. No coding blocker remains. Server-driven shelves require Audiobookshelf; empty/unavailable rows are omitted, and Android Auto owns the final geometry.
Next action: review/test PR #3 on a car display and connected Audiobookshelf instance; authorize merge/release when ready. PR #2 separately carries these shared-record updates. The optional nightly signing issue remains a separate follow-up.
Shared records updated: yes, through documentation PR #2; dedicated implementation task record is in PR #3.
Remote synchronization: main was 4b1e9295; release 0.9.2.81 remains the published baseline. No source work from another AI was overwritten.

## Implementation history — PHONE-PODCAST-FAVOURITES, Codex, 2026-11-17

Codex / PHONE-PODCAST-FAVOURITES: implementation and nine selected tests/FOSS debug build complete. Branch fix/shared-podcast-favourites. Next: full PR CI, authorized main merge/release, APK identity/cert/version comparison, final shared-record update. No physical phone/car or live authenticated YouTube test claimed.

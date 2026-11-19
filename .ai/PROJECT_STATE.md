# Project state

## Current — 0.9.2.100, ChatGPT Codex, 2026-11-19

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

Codex completed PHONE-PODCAST-FAVOURITES and published [Wavelength 0.9.2.94](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.94) from main. PR #15, main merge 650649af7175bcd072bec9b24d5d3c493216bbb7, signed release run 43985275212; final PR CI 43983342034 on 567eeb20 passed full app/innertube tests, FOSS APK build/upload and lint. Nine selected local tests and FOSS debug APK passed.

Phone show/RSS menus now use the same Favourite/Remove favourite preference and IDs as Android Auto. Both place favourites immediately after Episodes for later; phone combines RSS/YouTube shows under the selected sort before favouriting. Existing subscribed podcast-show playlist pins migrate to favourites (persist first, delete second), leaving music/song/artist pins untouched. Explicit podcast-playlist membership filters ordinary YouTube videos from music-home rows/Speed dial, with persisted offline membership and pull-to-refresh reload; song.isEpisode is not permanently changed.

The public APK was downloaded, signature verified against 0.9.2.93, and confirmed to use com.rickevans86.wavelength with the same signing certificate, higher version code and a non-debuggable release build. Exact package/version evidence is in the task record. User authorized main publication/build/release/AI records. No schema, manual version, signing or app-ID changes. No implementation/release work remains; phone/head-unit and live authenticated YouTube testing remain outstanding. A failed first remote membership lookup cannot classify uncached videos; last successful membership is retained offline.

## Previous release — 0.9.2.93, Codex, 2026-11-17 (Melbourne)

Codex completed AUTO-DOWNLOADS-FAVOURITES in PR #14, merged into main as 4bc049dfa230c1c805f63502a538aebf75586866. Released [Wavelength 0.9.2.93](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.93) through the existing signed FOSS workflow, run 43951768865. The owner explicitly authorized implementation, main publication, build/release and AI-record updates.

Changes: YouTube downloads/count/playback/Shuffle contain completed YouTube music only; podcast-show favourites persist, expose native Media3 Favourite/Remove favourite browse actions and rank favourites first (existing sort within groups retained); the duplicate combined Downloaded root/settings row is removed. Offline books have a paginated Downloaded tile inside Audiobooks. Legacy deep links remain valid. Phone show lists share favourite ranking.

Verification: 14 selected local tests passed (8 new regression tests + 6 existing pagination tests), local FOSS debug APK built; final PR CI 43950033049 on 66ff9368 passed full app/innertube unit suites, FOSS APK build/upload and lint. Release APK downloaded and verified against 0.9.2.92: application ID com.rickevans86.wavelength, same SHA-256 signing certificate 9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8, higher version code. Exact release package/version evidence is recorded in the task file. No schema, manual version, signing or application ID changes.

Physical head-unit testing of these changes remains outstanding. Android Auto controls browse-action placement and support. GitHub Issues remain disabled; task/PR identity is used without claiming an Issue assignment. Newer shared records from PR #2 were incorporated without discarding Claude's history; the superseded records PR was closed after PR #14 merged. No active file reservation remains for this task.

## Historical records

Verified 2026-11-13 by Codex. Wavelength/Meld is an Android Kotlin app.

Main now contains the existing feat/local-folders implementation and the audiobook phone-player improvements, merged in PR #1 at d3b6df1e29f61bb2fa2ed7fc3d3e693957d4cd1c. The 106 existing feature-branch commits and other agents' work were preserved.

Published release: [Wavelength 0.9.2.81](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.81), target main. Tag points to d3b6df1e. Wavelength.apk is public, signed, application ID com.meld.app.debug, version code 280081 (previous 280080). APK signature and certificate match release 0.9.2.80.

Phone player: separate chapter navigation, independently configurable rewind/forward under Settings > Features > Audiobooks (defaults 15/30 seconds), chapter picker, device-local bookmarks, speed and sleep timer access. Mini-player and media-session skips use the same saved preferences. Audiobookshelf chapter metadata is persisted and mapped across audio files. Local books use available file-based chapters.

Validation: six focused audiobook tests and local FOSS debug APK build passed. Full PR test suite, APK build and lint passed on a0d483fc; merged main has the identical code tree. Signed release workflow 43361593932 succeeded. Physical-device testing remains outstanding.

GitHub Issues are disabled (creating an issue returned HTTP 410). No remote Issue claim is asserted; PR #1 and .ai records identify Codex's completed task. Legacy GMS nightly workflow 43361593972 failed for missing signing secrets; the published FOSS release is unaffected.

## Fresh coordination audit — 2026-11-13, Codex

Remote main was verified at 4b1e92957d02fa505c5fe84974941c3921b27f7b. Latest release remained v0.9.2.81. No open PRs were present at audit start. Available branch heads remained main 4b1e9295, feat/local-folders e4ff3a6 and feat/audiobook-auto-controls a0d483fc. Claude's recorded podcast controls, Android Auto repair and upstream synchronization remain preserved; Codex's PR #1 release work is complete. Unpublished work in another agent's workspace cannot be inferred from this audit.

The next unclaimed follow-up is the optional GMS nightly signing configuration. The latest failure is verified; today's secret configuration is unverified because the integration received HTTP 403 while requesting metadata. Do not infer that credentials are still absent merely from an older failed run, substitute a new signing identity, or expose credentials while investigating. Issues are still disabled, so the follow-up remains explicitly unassigned/blocked rather than remotely claimed.

Pending feature: Codex's [PR #3](https://github.com/rickevans86/Meld/pull/3), AUTO-AUDIOBOOKS, adds the single scrolling Android Auto audiobook page and section controls. Code 11965b3f is locally verified (eight focused tests and FOSS debug build); application changes are not yet on main. Documentation PR #2 tracks this work separately, without application-file overlap. See the dedicated task file on the feature branch and latest HANDOFF section. Released baseline remains 0.9.2.81.

## AUTO-AUDIOBOOKS update — 2026-11-13, Claude

PR #3 is implementation-complete and still open; main is unchanged at 4b1e9295, and the released baseline is still 0.9.2.81. Codex's commit 25d9a88e passed all PR checks in run 43397446049.

Claude then added explicit pagination for Android Auto's 100-children limit in 640e3c95 and 23d4c232. Every audiobook and Downloaded list now returns at most 98 items plus a More folder. Older browse IDs and the playback routes are unchanged.

PR run 43403522870 on 23d4c232 passed: full unit suite (including 6 new pagination tests), FOSS debug build, review APK and lint. No local Gradle build was possible in Claude's workspace (no Android SDK). Physical Android Auto testing and live Audiobookshelf testing are still outstanding.

The phone audiobook Library / Downloaded / Listening tabs are a separate design request, not part of PR #3.

## Release 0.9.2.82 — 2026-11-13, Claude

The owner instructed "Publish to release". Claude merged PR #3 into main as 5e6a7b47 (merge commit; Codex's and Claude's commits preserved). Signed "Build and Release" run 43408784962 published [Wavelength 0.9.2.82](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.82), tagged at 5e6a7b47, with Wavelength.apk.

Claude downloaded the public APK and checked it: application ID com.meld.app.debug, version code 280082 (previous 280081), and the same signing certificate as 0.9.2.81. Main is now cc955f6c, a release-record commit marked [skip ci].

This is the latest released baseline. Still outstanding: physical Android Auto testing, live Audiobookshelf testing, and CI-NIGHTLY-SIGNING.

## Release 0.9.2.83 — 2026-11-13, Claude

The phone Audiobooks screen now has Library / Downloaded / Listening tabs (Continue listening card, cover grid, Sort) under the On this phone / Audiobookshelf switch. Tab order and library sort are in Settings → Features.

PR #4 was merged into main as e6e32e0c, and release run 43413832192 published Wavelength 0.9.2.83. The APK was verified: com.meld.app.debug, version code 280083, same certificate as before. This is the latest released baseline. Physical phone and car testing of 0.9.2.82–0.9.2.83 are still outstanding.

## Release 0.9.2.85 — 2026-11-14, Claude

Two changes shipped together as one new-app-ID build (owner backed up first):
1. WAVELENGTH-APP-ID: application id is now com.rickevans86.wavelength (was com.meld.app.debug, shared with Meld's developer builds → Play Protect warning). 0.9.2.84 first added backup coverage (podcast follows, My stations, starred, played episodes, podcast speeds) and an in-app move guide under the old id; 0.9.2.85 made the id switch. It installs as a SECOND app beside the old one — not an in-place update — so the user restores the backup, signs in to servers again, re-downloads offline media, then removes the old app (the new app guides this).
2. HOME-AUDIOBOOK-FILTER: the YouTube home's play-history rows (Recently Played, Quick picks, Keep listening, Forgotten favourites) now show only YouTube items. PR #7 was folded into PR #6 and closed.

Merge 0a221b60; release run 43522774134; APK verified id com.rickevans86.wavelength, version code 280085, same signing cert. Main head is the [skip ci] records commit. This is the latest released baseline. Outstanding: the owner's on-device migration, plus physical car/phone testing from earlier releases.

## Release 0.9.2.86 — 2026-11-14, Claude

Six owner-reported fixes (PR #8, merge c89e127f, run 43530050429), new-id build:
1. Audiobookshelf percent from position/duration (was 0% while time-left correct).
2. Audiobooks finished within the last 60s, marked finished on the server so they leave "in progress" automatically.
3. YouTube home shows only YouTube items and Spotify home only Spotify items, Speed dial included (pinned radio no longer in the YouTube Speed dial).
4. Spotify home no longer mixes in YouTube rows (option A; spotifyHomeOnly setting now redundant).
5. Podcast playlists (New Episodes auto-playlist) kept out of Mixes.
6. De-duped repeated YouTube feed sections on scroll.
APK verified: com.rickevans86.wavelength, version code 280086, same signing cert. Latest baseline. Outstanding: owner's on-device migration to the new app id (0.9.2.85+), and physical device/car testing.

## Release 0.9.2.88 — 2026-11-14, Claude

Recognition improvements + Updater download-button fix (PR #10, folding PR #9), plus a release-pipeline fix.
- Recognition: rolling re-query up to ~20s, UNPROCESSED mic with fallback, gain normalisation, and an optional user-token AudD fallback in Settings → Features (RECOGNITION-IMPROVE).
- Updater: the "Latest" row download icon now downloads.
- Pipeline: build_local_folders.yml now releases only from main. A scheduled build from the stale feat/local-folders branch had published 0.9.2.87 (old app id com.meld.app.debug, none of main's features), which leapfrogged main's version and rolled users back. main is now the sole release branch.
Merge 87684ccd; release run 43584647468; APK verified com.rickevans86.wavelength, version code 280088, same cert. Latest baseline.
OPEN ISSUE: upstream Spotify GQL-hash sync previously flowed via merges into feat/local-folders; it no longer reaches releases. Needs redirecting to main as a controlled, verified merge (not unattended) — flagged to owner.

## Release 0.9.2.89 — 2026-11-14, Claude

Audiobook detail page redesign (AUDIOBOOK-DETAIL-REDESIGN, PR #11, merge 632eefd0, run 43589020771). Both the Audiobookshelf and on-phone book pages now use a centred cover + Finished badge, play/continue button, Download + Finished action row, and collapsible Chapters. Audiobookshelf books also show Narrated By (See all), an expandable Overview, and Genre chips (AbsBook now parses narrators/description/genres). New shared AudiobookDetailUi.kt. APK verified: com.rickevans86.wavelength, version code 280089, same cert. Latest baseline.

## Release 0.9.2.91 — 2026-11-15, Claude

Podcast dedicated fast-forward / rewind buttons (PODCAST-FF-REWIND, PR #12, merge 05232f0b, run 43710728432). The podcast player now shows always-on rewind/forward seek buttons for episodes (like audiobooks), above the normal controls, with their own amounts in Settings → Features → Podcasts (separate from the existing repurpose-next/previous skip setting, which stays). APK verified: com.rickevans86.wavelength, version code 280091, same cert. Latest baseline.

## Release 0.9.2.92 — 2026-11-16, Claude

Podcast fast-forward/rewind now in Android Auto + the notification (PODCAST-FF-AUTO, PR #13, merge 5eaba9a6, run 43767999416). The dedicated seek buttons (0.9.2.91, phone-only) now also show in the car alongside previous/next, in the overflow slot, using the same separate amounts. New CommandSeekBack/Forward session commands, distinct from the old skip commands. APK verified: com.rickevans86.wavelength, version code 280092, same cert. Latest baseline.

Owner-verified in-car on 2026-11-16 (released 0.9.2.92 build): the 15/30 seek buttons show in the full Android Auto Now Playing player alongside previous/next — feature accepted. The Auto home-screen mini media card remains play-pause/previous/next only; that compact card's control set is fixed by Android Auto and cannot host app-supplied actions (those appear only in the full player and notification). Platform limitation, not a defect — PODCAST-FF-AUTO car testing is now cleared. Physical car testing of the earlier releases remains an open owner follow-up.
## Implementation history — PHONE-PODCAST-FAVOURITES, Codex, 2026-11-17

PHONE-PODCAST-FAVOURITES (Codex) is locally implemented on fix/shared-podcast-favourites; nine selected tests and FOSS debug APK passed. Full CI and authorized main/release publication pending. Baseline remains 0.9.2.93. See tasks/PHONE-PODCAST-FAVOURITES.md.

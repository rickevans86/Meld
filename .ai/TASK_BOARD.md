# Task board

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

## Previous release — 0.9.2.95, Codex, 2026-11-17 (Melbourne)

OFFLINE-LIBRARY-ACCESS completed by ChatGPT Codex in [PR #16](https://github.com/rickevans86/Meld/pull/16), feature `fb7e0e5c`, main merge `b3c1911d3f3433eee648ed4698d23a003b338496`. [Wavelength 0.9.2.95](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.95) published by signed release run `43999649417`. PR CI `43998334078` passed full tests, FOSS debug build/upload and lint. Local verification: 270 app + 10 innertube tests passed, FOSS debug build, diff/resource checks passed.

Downloaded ABS metadata/audio/covers/resume now use local data before server/auth; missing completed-book files fail locally, failed details stop spinning. Completed Media3 downloads bypass remote stream auth/quality resolution for phone and Android Auto. Airplane mode exposes source-specific downloaded music and podcast downloads; music-server roots also offer a Downloads entry when the network is present but the server is unavailable. Existing local-file playback retained. Streaming/discovery/live radio still require connectivity.

Public APK downloaded and verified: `com.rickevans86.wavelength`, `0.9.2.95`, code `280095` (higher than 280094), non-debuggable, same SHA-256 signing certificate `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Tag points to the tested main merge. No schema/manual version/signing/app-ID changes. Continuing the user's standing main/build/release/AI-record workflow. Issues disabled; temporary task ID used without asserting an Issue claim.

No implementation/release work or active reservation remains. Physical phone/head-unit airplane-mode playback, actual audio decoding and live authenticated server reconnection remain untested; owner device validation is the next action. All six shared AI records and [task record](tasks/OFFLINE-LIBRARY-ACCESS.md) updated; prior AI history retained.

## Previous release — 0.9.2.94, Codex, 2026-11-17 (Sydney)

PHONE-PODCAST-FAVOURITES — Codex — completed/released 0.9.2.94. PR #15, main merge 650649af7175bcd072bec9b24d5d3c493216bbb7, signed release run 43985275212; final PR CI 43983342034 on 567eeb20 passed full app/innertube tests, FOSS APK build/upload and lint. Nine selected local tests and FOSS debug APK passed. APK update identity/certificate verified. No active file lock. [Task record](tasks/PHONE-PODCAST-FAVOURITES.md). Physical phone/car and live YouTube checks remain outstanding.

## Previous release — 0.9.2.93, Codex, 2026-11-17 (Melbourne)

AUTO-DOWNLOADS-FAVOURITES — Codex — completed/released 0.9.2.93, PR #14, main 4bc049dfa230c1c805f63502a538aebf75586866, release run 43951768865. Fourteen selected local tests and FOSS debug build passed; final CI 43950033049 passed full tests/build/artifact/lint. Release APK identity/certificate/update compatibility verified. No active reservation. Physical head-unit test remains outstanding. Details: [task record](tasks/AUTO-DOWNLOADS-FAVOURITES.md). PR #2 summaries are now on main and that superseded records PR is closed.

## Historical records

GitHub Issues are authoritative when enabled; currently disabled (HTTP 410). GitHub PRs/commits/releases provide implementation evidence.

| Task | AI | Status | Branch / PR | Evidence |
| --- | --- | --- | --- | --- |
| AUDIOBOOK-PHONE (temporary ID) | Codex | Completed and released | main / [PR #1](https://github.com/rickevans86/Meld/pull/1) | d3b6df1e, v0.9.2.81; PR checks and signed release workflow passed |

AUTO-AUDIOBOOKS — Codex (implementation), Claude (pagination follow-up) — merged and released as [Wavelength 0.9.2.82](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.82) (owner-authorized; merge 5e6a7b47) from [PR #3](https://github.com/rickevans86/Meld/pull/3), branch feat/auto-audiobook-sections. Code: Codex 11965b3f, CI fixes 2a5c46a0/25d9a88e. Claude added Android Auto 100-item pagination in 640e3c95 and 23d4c232. All PR checks (unit tests, FOSS debug build, APK artifact, lint) passed on 23d4c232, run 43403522870. Release run 43408784962 succeeded. Car and live-server testing are still outstanding. Dedicated task/acceptance record: [.ai/tasks/AUTO-AUDIOBOOKS.md](https://github.com/rickevans86/Meld/blob/feat/auto-audiobook-sections/.ai/tasks/AUTO-AUDIOBOOKS.md). Issue claim unavailable (Issues disabled). Physical head-unit/live-server verification remains outstanding.

Acceptance met: separate phone chapter controls; configurable 15/30-second default skips; chapters, bookmarks, speed and sleep access; preserved progress/schema; shared coordination records; authorized publication to main and release. Hardware validation remains a manual follow-up.

## Coordination audit — 2026-11-13

Temporary audit ID: COORDINATION-AUDIT-2026-11-13. Responsible AI: Codex. Status: audit complete, documentation review pending. Branch: chore/ai-coordination-audit; [documentation PR #2](https://github.com/rickevans86/Meld/pull/2). GitHub Issue claim unavailable because Issues remain disabled; this is not a successful remote Issue assignment.

Next unclaimed follow-up: CI-NIGHTLY-SIGNING (proposed temporary ID).
- Scope: repair the optional Google Cast nightly signing configuration in .github/workflows/build.yml.
- Responsible AI: unassigned; no implementation claim made.
- Status: blocked on owner/integration access to signing configuration, not on the published FOSS release.
- Evidence: latest nightly run 43361593972 failed in GMS release signing and debug APK packaging. Its logs showed missing KEYSTORE and an empty DEBUG_KEYSTORE input. Current secret inventory could not be verified: metadata request returned HTTP 403, Resource not accessible by integration.
- Acceptance: preserve the existing GMS signing identity; validate the intended release/debug credentials; produce signed GMS artifacts and a successful nightly run; keep the working Wavelength FOSS release pipeline intact.
- Planned file scope after unblocking: .github/workflows/build.yml only if needed; signing secrets must be handled through repository settings, never pasted in chat or committed.
- Dependencies: owner verifies/configures the appropriate signing credentials and enables Issues if Issue-based claims are required.

No open PRs or current Issue claims were found at audit start. Physical-device validation of the audiobook release is another outstanding owner-assisted follow-up. No additional remote work by ChatGPT or Claude was observed on the available branches since the recorded commits.

The user selected AUTO-AUDIOBOOKS after the audit; CI-NIGHTLY-SIGNING remains an unassigned, blocked follow-up. PR #3 changes application code; PR #2 owns these root coordination-record updates, avoiding file conflicts.

PHONE-AUDIOBOOK-TABS — Claude — merged and released as [Wavelength 0.9.2.83](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.83) (owner-authorized) from [PR #4](https://github.com/rickevans86/Meld/pull/4), merge e6e32e0c. Delivers phone Library / Downloaded / Listening tabs under the source switch, plus tab order and library sort settings. PR CI run 43411817378 and release run 43413832192 passed. Phone testing is outstanding. Task record: .ai/tasks/PHONE-AUDIOBOOK-TABS.md on main.

WAVELENGTH-APP-ID — Claude — done, released in 0.9.2.85. Wavelength now builds as com.rickevans86.wavelength (was com.meld.app.debug, which tripped Play Protect). Two steps: 0.9.2.84 added backup coverage + a move guide under the old id; 0.9.2.85 (merge 0a221b60) switched the id and folded in HOME-AUDIOBOOK-FILTER. Installs as a second app; migration guide in-app. Owner still to migrate on-device.
HOME-AUDIOBOOK-FILTER — Claude — done, released in 0.9.2.85 (folded into PR #6; PR #7 closed). YouTube home rows now show only YouTube items.

HOME-SOURCE-AND-ABS-PROGRESS — Claude — released in 0.9.2.86 (merge c89e127f, run 43530050429). Six reported fixes: audiobook % from position, 60s auto-finish (server-synced), YouTube/Spotify home source purity incl. Speed dial, podcast playlists out of Mixes, de-duped repeated feed sections. AbsProgressTest added. APK verified (com.rickevans86.wavelength, vc 280086, same cert).

RECOGNITION-IMPROVE — Claude — released in 0.9.2.88 from main. Rolling recognition, UNPROCESSED mic, gain normalisation, optional AudD fallback (user token, Settings → Features). Includes Updater download-button fix (PR #9 folded) and the release-only-from-main pipeline fix (0.9.2.87 regression).

AUDIOBOOK-DETAIL-REDESIGN — Claude — released in 0.9.2.89 from main. Richer book detail (both ABS and on-phone): hero cover + Finished badge, play/continue, Download + Finished row, collapsible chapters; ABS also Narrated By / Overview / Genres. Add-to-Playlist left off per owner.

PODCAST-FF-REWIND — Claude — released in 0.9.2.91 from main. Dedicated podcast rewind/fast-forward buttons (always on for episodes), separate amounts in Settings; existing repurpose-next/previous skip setting kept.

PODCAST-FF-AUTO — Claude — released in 0.9.2.92 from main, owner-verified in-car 2026-11-16. Dedicated podcast seek buttons now in Android Auto + notification, alongside prev/next. Confirmed showing in the full Auto player; the Auto home mini media card stays prev/next only (Android Auto renders that card with a fixed control set, so app actions can't appear there). Accepted; no further code change.
## Implementation history — PHONE-PODCAST-FAVOURITES, Codex, 2026-11-17

PHONE-PODCAST-FAVOURITES — Codex — implemented/locally verified, PR CI pending; main/release authorized by owner. Scope: shared phone/Auto favourites, safe pin migration, podcast music-home exclusions. Nine selected tests/build passed. No Issue claim: Issues disabled.

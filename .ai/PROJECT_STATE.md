# Project state

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
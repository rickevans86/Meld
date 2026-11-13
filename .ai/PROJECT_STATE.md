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

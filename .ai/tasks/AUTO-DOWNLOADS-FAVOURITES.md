# AUTO-DOWNLOADS-FAVOURITES

AI: ChatGPT Codex
Status: completed, merged and released as 0.9.2.93
Branch: fix/auto-downloads-podcast-favourites, from main 442ed05a
Temporary task ID: GitHub Issues disabled; no remote Issue claim asserted. At task start, the only open PR (#2) was documentation; no application overlap. Its newer shared summaries are now on main and PR #2 is closed as superseded.

User authorized: fix YouTube downloaded songs (list/count/Shuffle restricted to completed YouTube music), favourite podcast channels and rank favourites first, remove duplicate More → Downloaded root, publish to main, build/release, update shared AI records.

Implementation: share filtered downloads across all Auto browse/playback paths. Use native Media3 per-item browse actions for favourite/unfavourite on YouTube podcast shows and RSS feeds, with persisted Preferences DataStore identities and existing sort order within each group. Refresh channel metadata/list on updates. Remove Downloaded enum so saved root layouts discard the old entry; retain legacy deep links.

Verification: all 14 selected local tests and FOSS debug build passed; full PR tests/build/lint and the signed release workflow passed. Initial compile found an unavailable legacy AndroidX import for the refresh-result key; replaced it with the documented constant without adding a dependency. No hardware testing claimed. No database schema, signing identity, application ID or manual version changes.

PR: https://github.com/rickevans86/Meld/pull/14; implementation b598af19. Local compilation passed after fixing the legacy import. First focused test run: seven passed; button registration needed a string-resource fixture because the project's JVM tests do not package app resources. Added that fixture using the existing TestResources pattern; rerun passed.

Navigation review: the car's Audiobooks page did not have a dedicated offline folder. Added a Downloaded browse tile inside Audiobooks, using the existing offline-book provider and bounded More pagination. Download/scan/account changes refresh this node. The removed combined root remains available only by legacy deep link. The final rerun including pagination passed (14 tests and FOSS debug APK).

## Final verification and release — Codex

Codex completed AUTO-DOWNLOADS-FAVOURITES in PR #14, merged into main as 4bc049dfa230c1c805f63502a538aebf75586866. Released [Wavelength 0.9.2.93](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.93) through the existing signed FOSS workflow, run 43951768865. The owner explicitly authorized implementation, main publication, build/release and AI-record updates.

Changes: YouTube downloads/count/playback/Shuffle contain completed YouTube music only; podcast-show favourites persist, expose native Media3 Favourite/Remove favourite browse actions and rank favourites first (existing sort within groups retained); the duplicate combined Downloaded root/settings row is removed. Offline books have a paginated Downloaded tile inside Audiobooks. Legacy deep links remain valid. Phone show lists share favourite ranking.

Verification: 14 selected local tests passed (8 new regression tests + 6 existing pagination tests), local FOSS debug APK built; final PR CI 43950033049 on 66ff9368 passed full app/innertube unit suites, FOSS APK build/upload and lint. Release APK downloaded and verified against 0.9.2.92: application ID com.rickevans86.wavelength, same SHA-256 signing certificate 9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8, higher version code. Exact release package/version evidence is recorded in the task file. No schema, manual version, signing or application ID changes.

Physical head-unit testing of these changes remains outstanding. Android Auto controls browse-action placement and support. GitHub Issues remain disabled; task/PR identity is used without claiming an Issue assignment. Newer shared records from PR #2 were incorporated without discarding Claude's history; the superseded records PR was closed after PR #14 merged. No active file reservation remains for this task.

Public APK evidence: Wavelength.apk from v0.9.2.93 is a verified non-debuggable release, application ID com.rickevans86.wavelength, version code 280093 (previous 280092), version name 0.9.2.93, same signing certificate as 0.9.2.92. Release tag resolves to main merge 4bc049dfa230c1c805f63502a538aebf75586866. The final records-only follow-up uses [skip ci] so no unchanged APK is rebuilt.

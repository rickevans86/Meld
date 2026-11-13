# AUTO-AUDIOBOOKS

AI: Codex (implementation, CI workflow fixes); Claude (Android Auto 100-item pagination, 2026-11-13)
Status: implementation complete; all PR checks pass on 23d4c232 (CI run 43403522870). Awaiting owner review, car testing and live Audiobookshelf testing in PR #3 (https://github.com/rickevans86/Meld/pull/3). Not merged to main; no release. Temporary task ID because GitHub Issues are disabled.
Branch: feat/auto-audiobook-sections (main 4b1e9295)
Overlap check: PR #2 is a separate documentation audit; no open feature PR or other agent claim overlaps this code.

Acceptance: a single vertically scrolling Android Auto Audiobooks page, grouped cover rows for Continue Listening, Recently Added and Continue Series, and Library/Series/Collections/Playlists browse tiles. A separate Downloaded root includes offline books and podcasts. Phone Android Auto settings persist shelf order/visibility. Preserve playback IDs/resume positions and existing music/podcast behavior. Use real Audiobookshelf metadata; omit empty or unavailable rows rather than inventing content.

Owned files: new AutoAudiobookLibrary and section preference/settings files; AutoLibraryExtras, MediaLibrarySessionCallback, AndroidAutoSettings, MusicService; AbsClient/AbsRepository browsing additions; default English metrolist_strings.xml; focused tests, the FOSS debug artifact path in build_pr.yml, and this task record. No database schema or version changes.

Verification: local :app:assembleFossDebug passed. Eight focused tests passed (AutoAudiobookSectionsTest: 3, AbsBrowseTest: 3, AutoAudiobookGridTest: 2); git diff --check and edited XML/resource validation passed. Tests cover saved order/visibility and malformed preferences, Audiobookshelf shelf/group/playlist parsing, grid/header metadata and preserved artwork, and resume-route normalization. Local test execution uses a workspace-only Gradle init script for writable Robolectric cache/proxy settings; it is not committed.

Implemented: flat grouped Audiobooks children with grid hints and unique shelf item IDs; Continue Listening, Recently Added, Browse and Continue Series in saved order; colored browse tiles; paginated Audiobookshelf library/groups; app audiobook playlists; separate offline books/podcast downloads root; refresh notifications on settings, scan/progress, download completion/removal and account changes. Existing playback preparation/resume logic is reused.

Data limits: server-driven rows and series/collection metadata require Audiobookshelf. Empty/unavailable rows are omitted. Local books still appear in Library, Continue Listening, applicable app playlists and Downloaded. Android Auto owns final card sizing/columns/control placement. A physical head-unit and live Audiobookshelf server were not tested.

Coordination: PR #2 owns the pending audit updates to the shared root .ai files. This feature uses a dedicated task record to avoid conflicting edits; PR #2 has been updated with the feature PR reference and current ownership/handoff. Main merge and release require separate authorization for this feature.

Review artifact: the existing PR workflow assembled FOSS Debug but uploaded from the GMS directory. Corrected its upload path to app/build/outputs/apk/foss/debug/*.apk, verified against the locally produced APK. CI builds use an isolated PR application ID and PR signing key. No signing credentials or release workflow changed.

CI run 43394406341: full unit tests passed and assembleFossDebug completed; the job reached its 20-minute limit during lint, so the later artifact upload was skipped. Split APK build/upload from lint and increased the job budget to 40 minutes. Lint remains enabled with the same PR environment; no source-code failure was observed. Final CI result remains pending.

CI result for Codex's final commit 25d9a88e: run 43397446049 passed (unit tests, FOSS debug build, APK upload, lint).

## Pagination follow-up — Claude, 2026-11-13

Problem: Android Auto shows at most 100 children per browse node. The Library page (device books plus a 200-book server page), app playlists inside Playlists, group contents, app playlist contents and the Downloaded root could go over that cap, and the More folder at the end was then hidden.

Change (commits 640e3c95 and 23d4c232):
- New playback/AutoBrowsePaging.kt: AUTO_BROWSE_LIMIT = 99 children per node, i.e. 98 items plus a More folder. Includes in-memory paging, page-number validation, and a combined device/app list plus server list pager that fetches at most two server pages per car page.
- Library: device books first, then Audiobookshelf books, on one continuous set of pages. If the server can't be reached, it pages device books plus downloaded books.
- Series/Collections/Playlists: server groups first, then app playlists, combined. Each group folder links to the 50-group server page that holds it, so `audiobooks/group/<kind>/<page>/<id>` keeps its meaning.
- Group contents, app playlists, app playlist contents and Downloaded accept an optional trailing page number (`.../<id>/<n>`, `audiobooks/local_playlists/<n>`, `downloaded/<n>`). IDs without one open page 0, so older browse paths and every playback route are unchanged. Home shelves stay at most 12 per row, which is already under the cap.
- AutoBrowsePagingTest (6 tests): every page stays under 100 with its More folder, and items are covered once and in order. Also covers device-first ordering across misaligned server pages, server-first ordering for playlists, an exactly-full device page still learning whether server items follow, server failure reported for the offline fallback, and invalid or overflowing page numbers.

Verification:
- The 6 new tests passed locally. This workspace has no Android SDK, so they ran with a standalone Kotlin 2.1.21 compiler and small local stand-ins for JUnit and runBlocking.
- The first CI run, 43403027779 on 640e3c95, failed to compile: a public function exposed the internal AutoPage type. Fixed in 23d4c232.
- CI run 43403522870 on 23d4c232 passed: full :app:testFossDebugUnitTest and :innertube:test (includes the new tests and the earlier eight), assembleFossDebug, review APK artifact app-universal-debug-pr-3, and :app:lintFossDebug.
- Not done: a local Gradle build (no SDK here), physical Android Auto head-unit testing, and live Audiobookshelf server testing.

Known limits: a refresh notification refreshes the first page of a list only. Pages are numbered by position, so if the server fails partway through, the offline fallback can repeat or skip items between pages. The older podcasts/downloaded and podcasts/saved lists (outside this feature) are unchanged.

Scope: the phone audiobook Library / Downloaded / Listening tabs and their order are a separate design request, not part of this task.

# AUTO-AUDIOBOOKS

AI: Codex
Status: implemented and locally verified; preparing feature PR for review. Temporary task ID because GitHub Issues are disabled.
Branch: feat/auto-audiobook-sections (main 4b1e9295)
Overlap check: PR #2 is a separate documentation audit; no open feature PR or other agent claim overlaps this code.

Acceptance: a single vertically scrolling Android Auto Audiobooks page, grouped cover rows for Continue Listening, Recently Added and Continue Series, and Library/Series/Collections/Playlists browse tiles. A separate Downloaded root includes offline books and podcasts. Phone Android Auto settings persist shelf order/visibility. Preserve playback IDs/resume positions and existing music/podcast behavior. Use real Audiobookshelf metadata; omit empty or unavailable rows rather than inventing content.

Owned files: new AutoAudiobookLibrary and section preference/settings files; AutoLibraryExtras, MediaLibrarySessionCallback, AndroidAutoSettings, MusicService; AbsClient/AbsRepository browsing additions; default English metrolist_strings.xml; focused tests and this task record. No database schema or version changes.

Verification: local :app:assembleFossDebug passed. Eight focused tests passed (AutoAudiobookSectionsTest: 3, AbsBrowseTest: 3, AutoAudiobookGridTest: 2); git diff --check and edited XML/resource validation passed. Tests cover saved order/visibility and malformed preferences, Audiobookshelf shelf/group/playlist parsing, grid/header metadata and preserved artwork, and resume-route normalization. Local test execution uses a workspace-only Gradle init script for writable Robolectric cache/proxy settings; it is not committed.

Implemented: flat grouped Audiobooks children with grid hints and unique shelf item IDs; Continue Listening, Recently Added, Browse and Continue Series in saved order; colored browse tiles; paginated Audiobookshelf library/groups; app audiobook playlists; separate offline books/podcast downloads root; refresh notifications on settings, scan/progress, download completion/removal and account changes. Existing playback preparation/resume logic is reused.

Data limits: server-driven rows and series/collection metadata require Audiobookshelf. Empty/unavailable rows are omitted. Local books still appear in Library, Continue Listening, applicable app playlists and Downloaded. Android Auto owns final card sizing/columns/control placement. A physical head-unit and live Audiobookshelf server were not tested.

Coordination: PR #2 owns the pending audit updates to the shared root .ai files. This feature uses a dedicated task record to avoid conflicting edits; PR #2 will be updated with the feature PR reference after creation. Main merge and release require separate authorization for this feature.

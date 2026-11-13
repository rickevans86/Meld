# Decisions

- Preserve Claude's existing audiobook/podcast implementation. Limit this feature to audiobook playback UI and preferences; no database schema change.
- Reuse existing sleep timer, queue/player, preference storage and skip icon conventions.
- Defaults are 15 seconds rewind and 30 seconds forward, independently configurable.
- Store bookmarks in app Preferences DataStore. They are device-local.
- GitHub Issues disabled: explicitly record temporary task identity instead of claiming a successful remote assignment.
- Publish from main using the existing Wavelength FOSS signing workflow and automatic run-number versioning; preserve app ID/signing certificate. Verified on downloaded release 0.9.2.81, tagged at d3b6df1e. Legacy optional GMS nightly credentials remain a separate follow-up.

- AUTO-AUDIOBOOKS (Codex, PR #3): implement a single Android Auto media-browser child list with standard content-style grid and group-title extras, not a custom car UI. Rows use stable shelf-specific IDs normalized to existing resume routes. Audiobookshelf personalized/category metadata drives server shelves; local-only sources omit unsupported/empty rows. Store row order/visibility in Preferences DataStore; preserve the database schema. Keep root coordination changes in documentation PR #2 and the feature task file in PR #3 to avoid overlapping edits.

- AUTO-AUDIOBOOKS pagination (Claude, PR #3): cap every Android Auto browse node at 99 children, 98 items plus More. Pages carry an optional trailing page number, so IDs without one open page 0 and existing paths stay valid. Combined lists page as one continuous sequence: device books before server books in Library, server playlists before app playlists. Server groups keep a 50-per-page reopen link. Downloaded is paged in memory.
- PHONE-AUDIOBOOK-TABS (Claude, PR #4): keep the source switch (owner's choice), with the tabs inside each source. On this phone, Downloaded lists every book. Tab order, open tab and library sort are stored in Preferences DataStore. Audiobookshelf sorts on the server (title, authorName, or addedAt newest first); phone books sort locally by title or author. Android Auto keeps title order through the default argument.

# AUTO-DOWNLOADS-FAVOURITES

AI: ChatGPT Codex
Status: implementation in progress
Branch: fix/auto-downloads-podcast-favourites, from main 442ed05a
Temporary task ID: GitHub Issues disabled; no remote Issue claim asserted. Only open PR #2 is documentation; no application overlap.

User authorized: fix YouTube downloaded songs (list/count/Shuffle restricted to completed YouTube music), favourite podcast channels and rank favourites first, remove duplicate More → Downloaded root, publish to main, build/release, update shared AI records.

Implementation plan: share filtered downloads across all Auto browse/playback paths. Use native Media3 per-item browse actions for favourite/unfavourite on YouTube podcast shows and RSS feeds, with persisted Preferences DataStore identities and existing sort order within each group. Refresh channel metadata/list on updates. Remove Downloaded enum so saved root layouts discard the old entry; retain legacy deep links.

Verification: running eight focused tests and FOSS debug build. Initial compile found an unavailable legacy AndroidX import for the refresh-result key; replaced it with the documented constant without adding a dependency. No hardware testing claimed. No database schema, signing identity, application ID or manual version changes.

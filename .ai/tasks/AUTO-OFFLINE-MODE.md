# AUTO-OFFLINE-MODE

## Current — 0.9.2.98, ChatGPT Codex, 2026-11-18

AUTO-OFFLINE-MODE completed in [PR #19](https://github.com/rickevans86/Meld/pull/19), feature 677ec709, main/tag `341620f41a3223e90a53f75fd031ab3c825afa74`. [Wavelength 0.9.2.98](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.98) published by signed workflow 44281137089. Full CI 44279339922 passed app/innertube tests, APK build/upload and lint. Two targeted local regression tests and FOSS debug build passed; diff checked.

Android Auto reads the phone's existing OfflineModeKey. Offline root exposes Podcasts, Audiobooks and Local files; podcasts offer Downloaded only, books show paginated downloaded device/ABS books without requiring an authenticated server session. Stale online browse nodes and online search results are blocked before catalogue requests. Mode changes notify root/standard nodes and tracked subscribed parents, including already-open pages. Turning the switch off restores ordinary car browsing. Playback restrictions and progress reconnect sync from 0.9.2.97 remain intact. Android Auto owns rendering/refresh timing; no physical head-unit success claimed.

Public APK downloaded and verified: `com.rickevans86.wavelength`, non-debuggable, `0.9.2.98` / code `280098`, higher than 280097, same certificate SHA-256 `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Tag matches tested merge. No schema/manual version/signing changes. Scope: MediaLibrarySessionCallback, AutoLibraryExtras, AutoAudiobookLibrary, MusicService, AutoOfflineMode helper/test and AI records.

AI: ChatGPT Codex. Issues disabled; temporary task ID, no remote claim. Standing main/build/release authorization used. All six shared records and task updated; earlier agents' history preserved. No active file reservation or implementation/release work remains. Next: owner checks toggling phone Offline mode while Auto is connected, downloaded playback and normal-menu restoration on the actual head unit.


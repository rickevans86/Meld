# PODCAST-STARTUP-CRASH

## Current — 0.9.2.100, ChatGPT Codex, 2026-11-19

PODCAST-STARTUP-CRASH completed in [PR #21](https://github.com/rickevans86/Meld/pull/21), feature c5241578, main/tag `f3defc7095e7689ddb852b22e4f7c04774350733`. [Wavelength 0.9.2.100](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.100) published by signed workflow `44513054423`. Full CI `44510223393` passed app/innertube tests, APK build/upload and lint. Six targeted local tests, including real ExoPlayer construction/release, and FOSS debug APK passed; diff checked.

User's 0.9.2.99 Samsung Android 17 crash report identified MusicService creation failing with IllegalStateException: getBackBufferDurationUs not implemented. The podcast LoadControl wrapper used Kotlin interface delegation, which did not forward Java default callbacks. Prior tests covered buffering decisions but missed actual ExoPlayer creation; 0.9.2.99 was therefore broken at service startup despite passing those checks. The fix implements LoadControl explicitly and forwards allocator, back-buffer duration, keyframe retention and preloading to the normal policy. Existing lifecycle and buffering logic remains. The new regression checks both back-buffer callbacks and constructs/releases a real ExoPlayer for both podcast classifications.

Public APK downloaded and verified: `com.rickevans86.wavelength`, non-debuggable, `0.9.2.100`, code `280100` greater than 280099, same signing certificate SHA-256 `9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8`. Tag matches tested merge. Install over the existing app; no uninstall/data reset is needed. Podcast buffer settings, downloads, positions and other data are retained. Scope: PodcastLoadControl, PodcastLoadControlTest and AI records only. No schema/manual version/signing changes.

AI: ChatGPT Codex. Issues disabled; temporary task ID, no remote claim. Standing fix/main/build/release authorization used. All six shared records and task updated, earlier history preserved. No active reservation or implementation/release work remains. Next: owner installs the replacement and confirms startup on the affected Samsung device; physical Android 17/head-unit and live streaming validation remain outstanding. Robolectric tests exercise actual player creation but are not device instrumentation tests.


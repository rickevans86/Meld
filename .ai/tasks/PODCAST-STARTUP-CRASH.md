# PODCAST-STARTUP-CRASH

## In progress — PODCAST-STARTUP-CRASH, ChatGPT Codex, 2026-11-19

User supplied crash report for 0.9.2.99 on Samsung Android 17: MusicService creation fails with LoadControl.getBackBufferDurationUs not implemented. Prior policy-only tests missed real ExoPlayer construction. Replace Kotlin interface delegation with explicit forwarding of allocator, back-buffer and preloading callbacks; add actual player construction/release regression. Branch fix/podcast-load-control-startup from main e22f2fd7; no open PR overlap; Issues disabled, temporary task ID. Scope PodcastLoadControl, its tests and AI records. Buffer setting retained. No schema/version/signing changes. Six targeted tests passed, including actual ExoPlayer construction/release; FOSS debug APK built and diff checked. Full CI/release pending; physical device untested. Standing fix/main/build/release workflow continues.


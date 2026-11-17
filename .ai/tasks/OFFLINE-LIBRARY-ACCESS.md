# OFFLINE-LIBRARY-ACCESS

AI: ChatGPT Codex
Status: implemented and locally verified; PR CI/release pending
Branch: fix/offline-library-access, baseline main 93204481 / release 0.9.2.94
Temporary task ID: Issues disabled; no open PR or overlapping claim found. User reports downloaded Audiobookshelf books unavailable with server offline, and requests equivalent offline access for everything in airplane mode. Continuing the user’s standing main/build/release/AI-record workflow for these updates.

Scope: make downloaded ABS metadata/audio/resume independent of server availability; clear failed detail spinner; completed-download cache before remote source/auth/quality resolution; offline downloaded-music access from online home/server sources. Verify existing podcast/local-file paths. Preserve normal streaming/account state and incomplete-download handling. No schema/version/signing changes.

Planned files: AbsRepository, AudiobookshelfViewModel/Content, MusicService and small cache helper, offline downloads UI, HomeScreen, SubsonicScreen/MediaServerScreen, default English strings, focused tests and AI records. All 270 app and 10 innertube tests passed; FOSS debug build, diff check and English resource uniqueness passed. No hardware airplane-mode success claimed.

## In progress — OFFLINE-LIBRARY-ACCESS, Codex, 2026-11-17 (Melbourne)

Completed local implementation on `fix/offline-library-access` from main `93204481`. Saved Audiobookshelf metadata, audio, covers and resume positions open without server/auth availability; failed details close instead of spinning. Completed Media3 downloads bypass remote authentication/quality lookup; airplane mode exposes source-specific saved music and podcast downloads. Server music roots also offer a local Downloads entry while online. Local files retain their direct playback path. Only completed downloads are covered; streaming/discovery/live radio still need a connection.

Validation: all 270 app and 10 innertube tests passed; FOSS debug APK built; diff whitespace and default English resource uniqueness passed. Regression tests cover unavailable server/no credentials, missing metadata/audio, resume persistence and completed/partial cache ranges. No physical phone/head-unit airplane-mode or real decoder test claimed. No schema, manual version, signing or app-ID changes. Continuing the user's standing main/build/release/AI-record workflow; PR CI and release remain pending. Issues disabled, so this is a temporary task ID, not a remote Issue claim. See [task](tasks/OFFLINE-LIBRARY-ACCESS.md).


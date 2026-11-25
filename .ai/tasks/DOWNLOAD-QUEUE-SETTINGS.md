# DOWNLOAD-QUEUE-SETTINGS

## Implementation — DOWNLOAD-QUEUE-SETTINGS, Claude, 2026-11-26

Owner asked for a setting to modify the download queue. Chosen scope (owner multi-select): max simultaneous downloads, pause/resume the whole queue, and a manage-queued-items screen. Reordering was offered but NOT selected (Media3 has no native reorder), so it is excluded.

Downloads run through Media3 `DownloadManager` in `playback/DownloadUtil.kt` (previously `maxParallelDownloads = 5`, hard-coded). Changes:
- constants/PreferenceKeys.kt: `MaxParallelDownloadsKey` (int, default 5), `DownloadQueuePausedKey` (bool, default false).
- DownloadUtil.kt: top-level `DOWNLOAD_STOP_REASON_PAUSE = 1`; `@Volatile queuePaused`; two DataStore collectors in `init` (max parallel → `downloadManager.maxParallelDownloads`, coerced 1–10; paused → `downloadManager.setStopReason(all)`); listener pauses freshly-added downloads while the queue is paused (guarded by `isNew` so a hand-resumed item is not re-paused); public `pauseDownload(id)`/`resumeDownload(id)`/`cancelDownload(id)` using `setStopReason`/`removeDownload`. Stop reasons persist in the download index, so a paused queue stays paused across restarts.
- StorageSettings.kt: in the Storage/Download group — a "Maximum simultaneous downloads" picker dialog (choices 1,2,3,4,5,6,8,10), a "Pause downloads" switch, and a "Manage download queue" item → `settings/download_queue`.
- DownloadQueueScreen.kt (new): lists queue states (DOWNLOADING/QUEUED/RESTARTING/STOPPED), resolves titles via `database.getSongById`, shows status + percent + progress bar, per-item pause/resume + cancel, pause-all/resume-all top-bar action, empty state. Registered in NavigationBuilder.kt.
- metrolist_strings.xml: new English strings (max_parallel_downloads[_desc], pause_download_queue[_desc], manage_download_queue[_desc], download_queue_empty, download_state_*, pause/resume/cancel_download, pause/resume_all_downloads).

No Room schema, app version, signing, dependency or translation (non-English) change. Validation: no local Android SDK in Claude's workspace, so no Gradle build here; relying on GitHub PR CI (unit tests, FOSS debug build, lint). Media3 APIs used (`setStopReason(int)` / `setStopReason(String,int)`, `maxParallelDownloads`, `removeDownload`, `Download.percentDownloaded`/`stopReason`) are standard 1.10.1. Physical phone check: change max-parallel and watch concurrency; pause/resume whole queue; pause/resume/cancel individual items; queue survives app restart while paused.

Branch `download-queue-settings` off main c416ae69. PR opened for CI. Not merged/released — awaiting owner. AI: Claude. Issues disabled → temporary task ID.

## Released — 0.9.2.118 / DOWNLOAD-QUEUE-SETTINGS, Claude, 2026-11-26

Owner authorized "Publish". PR #38 CI: first run failed to compile (Media3 1.10.1 DownloadManager.setStopReason has no one-arg overload — fixed to setStopReason(null, reason) in a4443bcb); second run failed only on the flaky SavedErrorLogsIntegrationTest (unrelated SAVED-ERROR-LOGS test; async race on reports().single()); CI re-run of the failed jobs went green (build success on a4443bcb). Main had advanced to fab5548b (automated Spotify GQL-hash commit, docs/spotify-gql-hashes.json only — no overlap); PR mergeable/clean. Merged to main as 54e3156459a721438e3a09484646c3c2d4864f45 (merge commit). Signed release run 45692585499 (run number 118) succeeded and published https://github.com/rickevans86/Meld/releases/tag/v0.9.2.118 (non-draft/non-prerelease, target 54e31564, asset Wavelength.apk).

Verified public APK: application id com.rickevans86.wavelength, versionName 0.9.2.118, versionCode 280118 (previous 280117, +1), signing certificate SHA-256 9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8 (unchanged). APK SHA-256 a91195423b9bdd1ea9c46933b7f4fb4a53e6fabc79a2155ca4cbc7ea22714a1e. No manual version/schema/signing/dependency change. Physical phone check (concurrency change, pause/resume whole queue, per-item pause/resume/cancel, paused queue persists across restart) remains an owner follow-up. AI: Claude.

Note for other agents: SavedErrorLogsIntegrationTest ("crash entry point only saves when local logging is enabled") is intermittently red in CI — it asserts reports().single() immediately after CrashReporter.reportFatal, which appears to complete asynchronously. Belongs to SAVED-ERROR-LOGS (not modified here); worth making the test await the write.

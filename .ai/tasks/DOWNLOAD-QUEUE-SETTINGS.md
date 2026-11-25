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

# RECOG-FLOAT-TOGGLE

## Implementation — RECOG-FLOAT-TOGGLE, Claude, 2026-11-25

Owner requested that the floating recognise-song button (the draggable overlay added in FLOATING-RECOGNIZER / 0.9.2.116) be reachable from the Recognise song page itself, not only Settings → Features. Owner chose a top-bar icon toggle over a body button.

Scope (one file): `ui/screens/recognition/RecognitionScreen.kt` top-bar `actions` row. Added an `IconButton` between the existing "Keep listening" chip and the History button that:
- reads `FloatingRecognitionService.running` (StateFlow) and tints the icon with the primary colour while the overlay is active, `LocalContentColor.current` otherwise;
- on tap: if running → `FloatingRecognitionService.hide(context)`; else if `Settings.canDrawOverlays` → `FloatingRecognitionService.show(context)`; else launches the `ACTION_MANAGE_OVERLAY_PERMISSION` intent via `rememberLauncherForActivityResult(StartActivityForResult())`, then shows on grant;
- surfaces the existing `floating_recognition_start_failed` / `floating_recognition_permission_needed` toasts.

Reuses the exact service API and permission flow already used by `FloatingRecognitionSetting`. No new mechanism, no manifest/permission change, no new strings (reused `floating_recognition_title` as the content description), no schema/version/signing/dependency change. Icon: existing `R.drawable.open_in_new` (pop-out).

Docs: WAVELENGTH_CHANGES.md change log updated (2026-11-25 entry). Note: the repo's WAVELENGTH_CHANGES.md change log had no FLOATING-RECOGNIZER entry (PR #36 updated `.ai` records but not that file); this change's entry is self-contained and explains the overlay in passing.

Validation: no local Android SDK in Claude's workspace, so no Gradle build here; relying on GitHub PR CI (unit tests, FOSS debug build, lint). This is a pure Compose top-bar addition mirroring an in-repo pattern; no unit test added (UI toggle, matches how other top-bar actions are untested). Physical phone check: the top-bar icon pops out/hides the overlay, the first-time overlay-permission prompt appears, and the icon reflects the active state.

Branch `recog-float-toggle` off main e1c34bfc. PR opened for CI. Not merged/released — awaiting owner instruction. AI: Claude. Issues disabled → temporary task ID.

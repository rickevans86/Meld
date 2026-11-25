# FLOATING-RECOGNIZER

## Merged to main — FLOATING-RECOGNIZER, ChatGPT Codex, 2026-11-25

User requested a floating recognise-song button over other apps, then explicitly authorized push to main and AI-file updates. Implemented source 4225f94bac5c2fa88725e971cf50cb8cb9186740 in PR36 https://github.com/rickevans86/Meld/pull/36; merged main 41e7853c5a4e3a2b5412e119995c960aea662699. GitHub comparison confirms the merge has no file differences from tested source.

Settings → Features → Floating recognise song button enables a draggable overlay after Android Display over other apps permission. Tap starts one bounded microphone recognition; tap again cancels; × or notification hides it. Inline results open recognition history. Enabling alone never records. The overlay hides while the screen is locked and is not restarted automatically after process/phone restart. A specialUse foreground service keeps the visible shortcut; a separate microphone foreground service starts only from the tapped, visible launch activity, which waits for startup acknowledgement and handles microphone permission/timeout. Shared recognition callback runs once; history inserts use IO. No internal/headphone audio capture: this uses microphone recognition.

Scope: FloatingRecognitionService, FloatingRecognitionSetting/FeaturesSettings, RecognitionLaunchActivity/RecognitionForegroundService, MainActivity history intent, manifest permissions/service, default English resources, Android-resource unit-test configuration and eight new lifecycle/permission/bounds tests. No schema, app version, signing, dependency or translation changes.

Validation: full local testFossDebugUnitTest passed 339 tests, zero failures/errors/skips; assembleFossDebug and lintFossDebug completed successfully (7m41s), git diff --check passed. Log /workspace/work/floating-recognition-verified.log. Lint retains existing repository diagnostics with abortOnError=false; no new Error diagnostic in overlay/launch changes. Earlier resource test configuration failure was corrected; disk-space failures were discarded after clearing generated build cache and rerunning all checks. GitHub Build PR 45599733613 passed unit tests and APK build/upload; its lint step was still running at merge and is not claimed passed. PR title check passed.

Physical phone checks remain: overlay permission, dragging/rotation, lock-screen hiding, microphone grant/denial, tap/cancel/result/history and Android 14+ foreground startup. Release publication was not requested; installed published release remains 0.9.2.115. Pending Navidrome PR35 remains separate; its records are retained, its source was not merged here.

Workspace became unavailable after successful local verification. Source was already safely pushed; final merge and AI records completed through GitHub. Local docs-only d482039a was not pushed and is superseded by these final records. On environment recovery, fetch and inspect the checkout before syncing; preserve existing edits. AI ownership reservations released. AI: ChatGPT Codex.


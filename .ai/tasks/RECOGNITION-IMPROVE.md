# RECOGNITION-IMPROVE

AI: Claude
Status: implemented; owner authorized push to main + release on 2026-11-14. Temporary task ID because GitHub Issues are disabled.
Branch: feat/recognition-improve (from main; also folds in UPDATER-DOWNLOAD-BUTTON / PR #9)

Problem: song recognition sometimes missed songs that the real Shazam app recognised. Root cause was a single-shot pipeline: record a fixed 12s clip, build one fingerprint, one Shazam query, give up on no match.

Changes (owner approved 1+2+3 and 4-as-a-setting):
1. Rolling recognition (MusicRecognitionService.recognize): one AudioRecord session; Shazam is queried as audio accumulates at 5/8/12/16s and once more with the full buffer, up to MAX_LISTEN_MS=20s, stopping at the first match. Status flips Listening/Processing across tries.
2. Microphone source: createAudioRecord tries UNPROCESSED (API 24+), then MIC, then VOICE_RECOGNITION, using the first that initialises — cleaner input for fingerprinting than the voice-tuned default.
3. Gain normalisation (normalizeGain): scales 16-bit PCM so the peak is near full scale before fingerprinting; skips near-silence and audio that is already loud.
4. Optional AudD fallback (AudDRecognizer.kt): when Shazam finds nothing and the user enabled it in Settings with their own AudD API token, the resampled clip is sent to api.audd.io (multipart WAV) and mapped to a RecognitionResult. Off by default; user-provided token (no shared key). Settings UI: AudDFallbackSetting() in FeaturesSettings — a switch plus a token field (TextFieldDialog) with a hint pointing to dashboard.audd.io. Keys: AudDRecognizer.EnabledKey / TokenKey in DataStore.

Also folded in (per owner, "next release"): UPDATER-DOWNLOAD-BUTTON — the Updater row download icon now starts the download.

Strings: audd_section, audd_fallback_setting, audd_fallback_desc, audd_token_setting, audd_token_not_set, audd_token_set, audd_token_placeholder, audd_token_help (default English only).
No database schema or version changes.

Tests: none added (hard to unit-test the audio/mic/HTTP pipeline without a device; logic is straightforward). Resource check clean. CI result recorded below.

Verification: CI result after the PR run (recorded on merge).

## Release-branch regression fix (bundled here) — 2026-11-14

Found while releasing: 0.9.2.87 was built by the "Build and Release" workflow from the stale
feat/local-folders branch (a daily upstream merge pushed to it). That branch has none of the
work merged into main (audiobook tabs, Android Auto, app-id change, home purity) and still uses
com.meld.app.debug. Because the version suffix is a monotonic run counter, 0.9.2.87 > 0.9.2.86,
so the updater offered it and users rolled back.

Fix: build_local_folders.yml release trigger is now `branches: [main]` only (was [main,
feat/local-folders]). main is the sole release branch. Note: upstream Spotify GQL-hash updates
were flowing via merges into feat/local-folders; those will no longer reach releases, so that
sync needs redirecting to main separately (flagged to owner).


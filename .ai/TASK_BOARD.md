# Task board

GitHub Issues are authoritative when enabled; currently disabled (HTTP 410). GitHub PRs/commits/releases provide implementation evidence.

| Task | AI | Status | Branch / PR | Evidence |
| --- | --- | --- | --- | --- |
| AUDIOBOOK-PHONE (temporary ID) | Codex | Completed and released | main / [PR #1](https://github.com/rickevans86/Meld/pull/1) | d3b6df1e, v0.9.2.81; PR checks and signed release workflow passed |

Acceptance met: separate phone chapter controls; configurable 15/30-second default skips; chapters, bookmarks, speed and sleep access; preserved progress/schema; shared coordination records; authorized publication to main and release. Hardware validation remains a manual follow-up.

## Coordination audit — 2026-11-13

Temporary audit ID: COORDINATION-AUDIT-2026-11-13. Responsible AI: Codex. Status: audit complete, documentation review pending. Branch: chore/ai-coordination-audit. GitHub Issue claim unavailable because Issues remain disabled; this is not a successful remote Issue assignment.

Next unclaimed follow-up: CI-NIGHTLY-SIGNING (proposed temporary ID).
- Scope: repair the optional Google Cast nightly signing configuration in .github/workflows/build.yml.
- Responsible AI: unassigned; no implementation claim made.
- Status: blocked on owner/integration access to signing configuration, not on the published FOSS release.
- Evidence: latest nightly run 43361593972 failed in GMS release signing and debug APK packaging. Its logs showed missing KEYSTORE and an empty DEBUG_KEYSTORE input. Current secret inventory could not be verified: metadata request returned HTTP 403, Resource not accessible by integration.
- Acceptance: preserve the existing GMS signing identity; validate the intended release/debug credentials; produce signed GMS artifacts and a successful nightly run; keep the working Wavelength FOSS release pipeline intact.
- Planned file scope after unblocking: .github/workflows/build.yml only if needed; signing secrets must be handled through repository settings, never pasted in chat or committed.
- Dependencies: owner verifies/configures the appropriate signing credentials and enables Issues if Issue-based claims are required.

No open PRs or current Issue claims were found at audit start. Physical-device validation of the audiobook release is another outstanding owner-assisted follow-up. No additional remote work by ChatGPT or Claude was observed on the available branches since the recorded commits.

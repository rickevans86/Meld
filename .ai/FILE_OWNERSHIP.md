# Planned file modifications

Task AUDIOBOOK-PHONE / PR #1, Codex, completed and released as v0.9.2.81 on main. No active file reservation. Remote Issue claim was unavailable.

- ui/player/Player.kt and AudiobookPlayerControls.kt
- audiobooks/AudiobookPlaybackSettings.kt and AudiobookBookmarks.kt
- ui/player/MiniPlayer.kt and ui/screens/settings/FeaturesSettings.kt
- audiobooks/abs/AbsRepository.kt and AbsChapterNavigation.kt
- .github/workflows/build_local_folders.yml (enable the existing signed release pipeline on main)
- playback/MusicService.kt
- default English values/metrolist_strings.xml
- relevant audiobook tests, .ai/* and additive AGENTS.md instructions

Other agents may work on these files after checking current main and active PRs. The list above records the completed scope, not an active lock.

## Coordination audit — Codex

Task COORDINATION-AUDIT-2026-11-13; branch chore/ai-coordination-audit. Completed file scope: .ai/PROJECT_STATE.md, TASK_BOARD.md, AI_ACTIVITY_LOG.md, HANDOFF.md and FILE_OWNERSHIP.md. Documentation review pending; no application/workflow files reserved.

Proposed CI-NIGHTLY-SIGNING follow-up: .github/workflows/build.yml only after access/ownership is established. No AI currently claims that implementation. The working FOSS release workflow is outside the proposed change scope.

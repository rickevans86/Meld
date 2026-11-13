# Decisions

- Preserve Claude's existing audiobook/podcast implementation. Limit this feature to audiobook playback UI and preferences; no database schema change.
- Reuse existing sleep timer, queue/player, preference storage and skip icon conventions.
- Defaults are 15 seconds rewind and 30 seconds forward, independently configurable.
- Store bookmarks in app Preferences DataStore. They are device-local.
- GitHub Issues disabled: explicitly record temporary task identity instead of claiming a successful remote assignment.

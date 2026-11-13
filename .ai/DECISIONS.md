# Decisions

- Preserve Claude's existing audiobook/podcast implementation. Limit this feature to audiobook playback UI and preferences; no database schema change.
- Reuse existing sleep timer, queue/player, preference storage and skip icon conventions.
- Defaults are 15 seconds rewind and 30 seconds forward, independently configurable.
- Store bookmarks in app Preferences DataStore. They are device-local.
- GitHub Issues disabled: explicitly record temporary task identity instead of claiming a successful remote assignment.
- Publish from main using the existing Wavelength FOSS signing workflow and automatic run-number versioning; preserve app ID/signing certificate. Verified on downloaded release 0.9.2.81, tagged at d3b6df1e. Legacy optional GMS nightly credentials remain a separate follow-up.

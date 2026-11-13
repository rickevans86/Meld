# WAVELENGTH-APP-ID

AI: Claude
Status: step 1 in progress (owner approved the move on 2026-11-14). Temporary task ID because GitHub Issues are disabled.
Branch: feat/wavelength-app-id (from main f2fd0f19)

Problem: Play Protect warns when installing Wavelength. Another app of the owner's, signed with his own key but with its own application ID, installs without a warning. Wavelength is signed with the owner's key ("Meld fork", AU) and is a non-debuggable, minified FOSS release build. But its application ID is com.meld.app.debug, the same ID as upstream Meld's developer builds, which are signed with other keys. A known ID with a different signer looks like a repackaged app.

Plan (owner approved):
1. 0.9.2.84, still com.meld.app.debug. Backups also include the Wavelength JSON files (podcast_follows, radio_favourites, radio_starred, played_episodes, podcast_speeds) under wavelength/ entries, and restore accepts only those names. The old app shows a one-time "Wavelength is moving" dialog and a Back up now card on the Updater screen.
2. 0.9.2.85, published only after the owner has backed up, with application ID com.rickevans86.wavelength (set in build_local_folders.yml). It installs as a new app next to the old one. While the old app is installed, the new app shows a guide: restore the backup, sign in again, and remove the old app (opens its system info page; manifest <queries> lists the old package).

Not carried over: server sign-ins (encrypted stores), downloaded files, and folder permissions. The FileProvider authority uses ${applicationId}, so both apps can be installed together. Same signing key.

Tests: WavelengthBackupFilesTest (3) covers entry round-trip, that the key files are included, and rejection of other names and path tricks.

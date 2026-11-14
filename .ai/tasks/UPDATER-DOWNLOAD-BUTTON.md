# UPDATER-DOWNLOAD-BUTTON

AI: Claude
Status: implemented; CI-verified on PR. Release timing per owner. Temporary task ID because GitHub Issues are disabled.
Branch: fix/updater-download-btn -> committed on fix/updater-download-btn

Bug (owner screenshot): the download icon in the Updater's "Latest: <version>" row did nothing useful — it was a static Icon in the row's trailing slot, so tapping it ran the row's onClick (performManualCheck, i.e. re-check for updates) instead of downloading. The actual download was only the "Download and install" button below.

Fix (UpdaterSettings.kt): the trailing icon is now a clickable IconButton (the project's ui/component/IconButton, which needs onLongClick — both click and long-click start the download) that calls the shared startDownload, and shows a small spinner while downloading. downloadState/downloadUrl/startDownload were lifted above the settings group so the row icon and the install button share them.

Note on Play Protect: on first install of the new app id (com.rickevans86.wavelength, 0.9.2.85+), Play Protect shows "App scan recommended — Play Protect hasn't seen this app before." This is the benign unknown-app prompt, not the earlier repackaged/blocking warning; tapping Scan app installs it. No code fix removes it; it can recur per new APK.

Tests: none (UI wiring). Resource check clean.

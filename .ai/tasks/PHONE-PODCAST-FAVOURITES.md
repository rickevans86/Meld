# PHONE-PODCAST-FAVOURITES

AI: ChatGPT Codex
Status: completed, merged and released as 0.9.2.94
Branch: fix/shared-podcast-favourites, baseline main f76f8a49 / release 0.9.2.93
Temporary task ID: Issues disabled. Live audit found no open PRs or overlapping active work. User explicitly authorized changes, publication to main, build/release and AI-record updates.

Scope: replace podcast show Speed dial menu with shared Favourite/Remove favourite controls on phone; use the same identities/preference as Android Auto; render favourites immediately below Episodes for later on both platforms; migrate existing known podcast-show pins without touching music pins. Keep podcast-classified playlists/episodes out of YouTube music-home rows. No schema or manual version/signing/app-ID changes.

Owned files: PodcastFavourites and migration/filter helpers, App initialization if needed, LibraryPodcastsScreen, AutoLibraryExtras, HomeViewModel/HomeScreen, lightweight DAO queries if needed, relevant tests and shared .ai records. Preserve earlier Codex/Claude implementations.

Verification: nine selected tests and :app:assembleFossDebug passed locally. Tests cover pin selection, real Room/DataStore migration preserving existing favourites/music pins, mixed RSS/YouTube favourite ordering, native/marked-playlist membership without retagging ordinary videos, explicit membership filtering before Speed dial display limits, and existing favourite persistence. Initial compile found a missing menu import, corrected before successful rerun. Full PR CI and signed release passed (details below). Physical phone/head-unit testing unavailable; no hardware success claimed.

Implementation: phone show/RSS menus call the same PodcastFavourites.setFavourite and use the same canonical IDs as Android Auto. Phone waits for persistence before dismissing the menu. Phone and Auto put favourites immediately below Episodes for later, retaining the selected sort within favourite/ordinary groups. Startup migration observes subscribed-show IDs and playlist pins; writes the favourite first, then deletes only matching show pins. Music/artist/song pins are untouched.

Music-home classification: lightweight DAO observes native episode IDs and selected local playlist membership. Selected online playlist membership is expanded through the existing YouTube pagination helper, bounded to 15 seconds per playlist, cached in Preferences for offline use and reused in-process. Pull-to-refresh reloads membership. YouTube Speed dial, history/quick-pick/keep-listening rows, feed items, similar recommendations and account playlist cards filter these explicit IDs; user classification does not permanently change song.isEpisode. Offline/failed first lookup cannot identify previously uncached online playlist members; live authenticated YouTube testing is outstanding.

## Final publication — Codex

Codex completed PHONE-PODCAST-FAVOURITES and published [Wavelength 0.9.2.94](https://github.com/rickevans86/Meld/releases/tag/v0.9.2.94) from main. PR #15, main merge 650649af7175bcd072bec9b24d5d3c493216bbb7, signed release run 43985275212; final PR CI 43983342034 on 567eeb20 passed full app/innertube tests, FOSS APK build/upload and lint. Nine selected local tests and FOSS debug APK passed.

Phone show/RSS menus now use the same Favourite/Remove favourite preference and IDs as Android Auto. Both place favourites immediately after Episodes for later; phone combines RSS/YouTube shows under the selected sort before favouriting. Existing subscribed podcast-show playlist pins migrate to favourites (persist first, delete second), leaving music/song/artist pins untouched. Explicit podcast-playlist membership filters ordinary YouTube videos from music-home rows/Speed dial, with persisted offline membership and pull-to-refresh reload; song.isEpisode is not permanently changed.

The public APK was downloaded, signature verified against 0.9.2.93, and confirmed to use com.rickevans86.wavelength with the same signing certificate, higher version code and a non-debuggable release build. Exact package/version evidence is in the task record. User authorized main publication/build/release/AI records. No schema, manual version, signing or app-ID changes. No implementation/release work remains; phone/head-unit and live authenticated YouTube testing remain outstanding. A failed first remote membership lookup cannot classify uncached videos; last successful membership is retained offline.

Public APK evidence: version name 0.9.2.94, version code 280094 (previous 280093), application ID com.rickevans86.wavelength; same SHA-256 signing certificate 9056f5a0b0d6f4e2e44e2b1b75ece08ac761fa1dd6a3a8654e8c62cd47fb46f8 as 0.9.2.93; verified non-debuggable. New podcastHomePlaylist filter code confirmed in the downloaded APK's DEX. Release tag v0.9.2.94 points to 650649af7175bcd072bec9b24d5d3c493216bbb7. Final records-only follow-up uses [skip ci]; application code is unchanged.

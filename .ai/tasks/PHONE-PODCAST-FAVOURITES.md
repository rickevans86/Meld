# PHONE-PODCAST-FAVOURITES

AI: ChatGPT Codex
Status: implemented; locally verified, full PR CI pending
Branch: fix/shared-podcast-favourites, baseline main f76f8a49 / release 0.9.2.93
Temporary task ID: Issues disabled. Live audit found no open PRs or overlapping active work. User explicitly authorized changes, publication to main, build/release and AI-record updates.

Scope: replace podcast show Speed dial menu with shared Favourite/Remove favourite controls on phone; use the same identities/preference as Android Auto; render favourites immediately below Episodes for later on both platforms; migrate existing known podcast-show pins without touching music pins. Keep podcast-classified playlists/episodes out of YouTube music-home rows. No schema or manual version/signing/app-ID changes.

Owned files: PodcastFavourites and migration/filter helpers, App initialization if needed, LibraryPodcastsScreen, AutoLibraryExtras, HomeViewModel/HomeScreen, lightweight DAO queries if needed, relevant tests and shared .ai records. Preserve earlier Codex/Claude implementations.

Verification: nine selected tests and :app:assembleFossDebug passed locally. Tests cover pin selection, real Room/DataStore migration preserving existing favourites/music pins, mixed RSS/YouTube favourite ordering, native/marked-playlist membership without retagging ordinary videos, explicit membership filtering before Speed dial display limits, and existing favourite persistence. Initial compile found a missing menu import, corrected before successful rerun. Full PR CI/release pending. Physical phone/head-unit testing unavailable; no hardware success claimed.

Implementation: phone show/RSS menus call the same PodcastFavourites.setFavourite and use the same canonical IDs as Android Auto. Phone waits for persistence before dismissing the menu. Phone and Auto put favourites immediately below Episodes for later, retaining the selected sort within favourite/ordinary groups. Startup migration observes subscribed-show IDs and playlist pins; writes the favourite first, then deletes only matching show pins. Music/artist/song pins are untouched.

Music-home classification: lightweight DAO observes native episode IDs and selected local playlist membership. Selected online playlist membership is expanded through the existing YouTube pagination helper, bounded to 15 seconds per playlist, cached in Preferences for offline use and reused in-process. Pull-to-refresh reloads membership. YouTube Speed dial, history/quick-pick/keep-listening rows, feed items, similar recommendations and account playlist cards filter these explicit IDs; user classification does not permanently change song.isEpisode. Offline/failed first lookup cannot identify previously uncached online playlist members; live authenticated YouTube testing is outstanding.

<div align="center">
<img src="fastlane/metadata/android/en-US/images/icon.png" width="160" height="160" style="display: block; margin: 0 auto"/>
<h1>Wavelength</h1>
<p>Music, podcasts, news, radio, audiobooks and your own media servers, all in one Android app</p>

[![Latest release](https://img.shields.io/github/v/release/rickevans86/Meld?style=for-the-badge)](https://github.com/rickevans86/Meld/releases/latest)
[![GitHub license](https://img.shields.io/github/license/rickevans86/Meld?style=for-the-badge)](LICENSE)

</div>

## What is Wavelength?

**Wavelength** is a personal fork of [Meld](https://github.com/FrancescoGrazioso/Meld), which is itself a fork
of [Metrolist](https://github.com/MetrolistGroup/Metrolist). Meld combines Spotify (for your library,
recommendations and search) with YouTube Music (for the audio). Wavelength keeps all of that and adds
podcasts, news bulletins, live radio, audiobooks, your own Navidrome, Jellyfin and Plex servers, files on
the phone, song recognition, and a much fuller Android Auto experience.

It picks up Meld's improvements automatically: a scheduled job merges the latest Meld code into this fork
every day. Nothing is ever pushed back to the original Meld repository.

- **[WAVELENGTH_CHANGES.md](WAVELENGTH_CHANGES.md)** is the detailed log of every change made in this fork,
  and how each one works.
- **[prompt.md](prompt.md)** is a single prompt that would rebuild Wavelength from Meld, broken into parts
  you can reuse as snippets in other projects.

## Download

Go to the [latest release](https://github.com/rickevans86/Meld/releases/latest) and download **Wavelength.apk**
(older releases named it Meld.apk). Open it on your Android phone and allow "Install from unknown sources"
if asked. After that, the app checks this repository for new versions and can update itself.

Every push to this fork builds and publishes a new release automatically. Releases are named
"Wavelength 0.9.2.<n>", where `<n>` is the build number.

## Features

### Home and navigation
- **Official-style home pages.** The YouTube Music and Spotify homes are laid out like the official apps
  (speed dial, quick picks, "Start listening", your playlists, new releases). The home tab is named after
  its source, so it reads "YouTube" or "Spotify" instead of "Home".
- **Spotify Recently played** on the Spotify home only lists songs you played from Spotify.
- **No podcasts on the music homes.** Podcasts and audiobooks are filtered out of the home rows, speed
  dial, "Similar to" and Spotify's "Start listening", because they have their own tab.
- **Editable bottom bar.** Choose up to 6 tabs and their order in Settings → Features → Bottom bar.
  Choices: YouTube or Spotify home, Library, Podcasts, News, Audiobooks, Radio, Navidrome, Media
  server and Local files.
- **Side drawer.** Every section is always reachable from the side menu, even if it isn't on the bottom bar.
- **Search on every tab.** The search button in the top bar searches the tab you're on: YouTube Music
  from the YouTube home, Spotify from the Spotify home, your library from Library, and podcasts, radio
  stations, Navidrome, your media server, news sources, audiobooks or files on the phone from those tabs.
  Every search uses the same search bar, so Search is no longer a bottom-bar tab (the combined Search
  screen is still in the side menu).
- **Features settings.** Settings → Features turns whole sections on or off. A new install starts with only
  the YouTube home and Library; switch on Spotify, Podcasts, News, Radio, Audiobooks, Navidrome, Media server
  or Local files there. (Updating from an older version keeps your sections as they were.)

### Library
- **YouTube and Spotify together.** Both libraries show on the same page.
- **Source logos.** Each playlist shows a small YouTube or Spotify logo between its picture and its name.
- **Pins.** Pin any playlist, album or artist, or Liked songs (⋮ → Pin to top) and it appears in a Pins row
  at the top of the library and at the top of the playlist list. Long-press a pin to open its menu.
- **Same menu everywhere.** The ⋮ inside a playlist or Liked songs has Play next, Pin to top and
  Auto-download new songs, like the playlist's menu in the Library.
- **Auto-download new songs.** On a playlist or Liked songs, ⋮ → Auto-download new songs. Every few hours the playlist is
  refreshed from YouTube and any new song is downloaded. Switching it on also downloads what's already there.
- **Downloads as files.** Downloaded songs can be saved as ordinary audio files on the phone.

### Podcasts
- **Follow and play podcasts** from the Apple Podcasts directory, with a New episodes view.
- **Discover.** A Discover chip shows top podcasts by category (from Apple's charts) to add with one tap.
- **YouTube playlists as podcasts.** Settings → Features → Podcasts lets you search your playlists
  (YouTube or local) and tick any to show in the Podcasts tab, even ones that aren't really podcasts. You
  can also paste a YouTube playlist link.
- **Episode downloads.** Episodes download when you play them and delete themselves when you finish (both
  can be switched off). Each episode also has a button to download or delete it by hand.
- **Downloaded filter.** A Downloaded chip in the podcast pages shows only episodes you have offline.
- **Unfollow anything.** Followed feeds, including ones that are no longer available, have a ⋮ menu with
  Unfollow and Share.

### News
- **News bulletins** from sources you choose, played as short audio briefings and labelled with the
  source name. Nothing is hard-coded: every source can be switched on or off.
- **Source checker.** Each source is checked to see whether it can be played. Sources from the list that
  have no public feed (made only for Google News) are removed automatically; others that can't play right
  now are marked, with a Check again button.
- **Where sources come from.** Each source is a public podcast feed found in the Apple Podcasts directory:
  a built-in set, Google News' old audio line-up, and more daily news shows (ABC News Daily, The Daily Aus,
  Up First, The Daily, FT News Briefing, Techmeme Ride Home, Short Wave and others). Podcasts you follow can be
  added too.

### Radio
- **Live radio stations** you can search, save and play. Each station's ⋮ menu adds it to or removes it
  from My stations. Tap the heart (on the station or in the player, including Android Auto) to make it a
  favourite; favourites stay at the top of the list in the app and in the car.
- **Save the song that's playing.** In Android Auto, the add button on a radio station finds the song the
  station says is playing on YouTube Music and adds it to a playlist you choose in
  Settings → Features → Radio songs (or a new "Radio songs" playlist).

### Your own media
- **Navidrome / Subsonic.** Browse your server by artist, album, playlist, starred songs, or as a folder
  tree that matches the folders on the server, with breadcrumbs and a back button.
- **Jellyfin and Plex.** One Media server tab where you pick which server to use. Jellyfin signs in with
  your username and password; Plex signs in through plex.tv. Browse, search, play and download.
- **Audiobooks** from your device and servers.
- **Local files.** A tab for music stored on the phone.

### Song recognition
- **Recognise a song** (Shazam-style) and add it automatically to a playlist you choose in
  Settings → Features → Song recognition. You can create a new private YouTube playlist for this.
  Songs already in the playlist aren't added twice.
- **Keep listening.** Hold the listen button for 5 seconds, or use the Keep listening switch, and the app
  keeps recognising songs until you turn it off. It shows "Auto listening" under the button, works with
  the screen off, and turns itself off after a time you set (in 30-minute steps). Tap the button again to stop.

### Android Auto
- **YouTube page.** Liked songs at the top, then your pinned playlists, then the rest.
- **Spotify page.** The same layout for your Spotify library.
- **Source labels.** Items are marked "YouTube ·" or "Spotify ·" so you can tell them apart.
- **Only buttons that work.** The player buttons change with what's playing: live radio shows only the heart (favourite
  station) and the add button, podcasts, news and audiobooks show only the heart (save for later), and your own servers and
  phone files leave out YouTube radio.
- **More sections.** Podcasts (including Downloaded and your podcast playlists), news, radio, Navidrome,
  Jellyfin/Plex, audiobooks and local files.
- **Continue listening.** A Continue section in the car. Settings → Android Auto lets you remove items
  from it on the phone.

### Backup and restore
Settings → Backup and restore has separate sections for:
- **Podcasts:** export and import OPML, the standard podcast format. Duplicates are skipped on import.
- **Radio:** export and import M3U playlists, also readable by other radio apps.
- **News:** export and import your news sources as OPML.

### Removed from Meld
Discord Rich Presence, Last.fm scrobbling and Listen Together are switched off in this fork.

## Inherited from Meld

Everything Meld does still works:
- **Spotify integration.** Log in inside the app (no developer account needed) and use Spotify for your
  home, search and recommendations, while audio streams through YouTube Music. Free Spotify accounts work.
- **Lossless audio (Qobuz).** Optional FLAC playback with automatic fallback to YouTube Music.
- **Core music features** from Metrolist: synced lyrics, offline downloads, equaliser, sleep timer and more.

See the [Meld README](https://github.com/FrancescoGrazioso/Meld#readme) for full details of the Spotify and
Qobuz integrations.

## Setup tips

- **Battery:** for reliable playback and background listening, set the app to Unrestricted in your phone's
  Settings → Apps → Battery.
- **Spotify:** Settings → Integrations → Spotify → Login, then switch on "Use Spotify for Home" and/or
  "Use Spotify for Search", and pull down on the home screen to refresh.
- **Android Auto:** if the app doesn't appear, open Android Auto's settings, tap the version number
  several times to unlock developer settings, then turn on "Unknown sources" in the ⋮ menu.
- **Media servers:** open the Navidrome or Media server tab and enter your server address and sign-in.

## Building from source

The app is built by GitHub Actions on every push to the `feat/local-folders` branch. Releases are signed
with a key stored in the `DEBUG_KEYSTORE_BASE64` repository secret, which is never committed. Every
release must use the same key, or the in-app update won't install.

## Credits

Wavelength is a fork of [Meld](https://github.com/FrancescoGrazioso/Meld) by Francesco Grazioso, which is a
fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist), originally created by
[Mo Agamy](https://github.com/mostafaalagamy).

### Upstream projects
- **InnerTune:** [Zion Huang](https://github.com/z-huang) · [Malopieds](https://github.com/Malopieds)
- **OuterTune:** [Davide Garberi](https://github.com/DD3Boh) · [Michael Zh](https://github.com/mikooomich)

### Libraries and integrations
- [**Better Lyrics**](https://better-lyrics.boidu.dev): time-synced lyrics
- [**SimpMusic Lyrics**](https://github.com/maxrave-dev/SimpMusic): lyrics data
- [**MusicRecognizer**](https://github.com/aleksey-saenko/MusicRecognizer): music recognition and Shazam API integration

## License

GPL-3.0, the same as Meld and Metrolist. See [LICENSE](LICENSE).

## Disclaimer

This project is not affiliated with, funded, authorised or endorsed by YouTube, Google LLC, Spotify AB,
Apple, Plex, Jellyfin, Navidrome, Qobuz or any of their affiliates. All trademarks belong to their owners.

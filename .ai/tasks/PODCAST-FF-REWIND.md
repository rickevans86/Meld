# PODCAST-FF-REWIND

AI: Claude
Status: released in Wavelength 0.9.2.91 from main (merge 05232f0b, run 43710728432). APK verified: com.rickevans86.wavelength, version code 280091, same cert. Owner authorized merge + release on 2026-11-15. Temporary task ID because GitHub Issues are disabled.
Branch: feat/podcast-ff-rewind (from main)

Request: add dedicated fast-forward and rewind buttons to podcasts, like audiobooks. Owner chose: keep the existing "repurpose next/previous" podcast skip setting, and give the new dedicated buttons their own separate amounts.

Changes:
- PodcastSkip.kt: new ButtonForwardKey / ButtonBackKey (defaults 30 / 15, CHOICES reused); rememberPodcastSeekButtons(mediaMetadata) returns the amounts for a podcast episode (always, no enable toggle, like audiobooks); new PodcastSeekRow composable — a centred rewind + fast-forward row using the existing SkipIcon.
- Player.kt: for a podcast episode (not an audiobook, not casting, not a listen-together guest) the PodcastSeekRow is shown above the normal play/pause/next/previous controls; seeking clamps via audiobookSeekPosition.
- FeaturesSettings.kt: two always-visible amount pickers (Fast-forward / Rewind button amount) in the Podcasts group, with their own picker dialog, separate from the skip pickers. (Fixed an if/else + list precedence bug so the new items show regardless of the skip toggle.)
- Strings: podcast_button_forward, podcast_button_back (default English only).
No database schema or version changes.

Tests: none (player UI). Resource check clean (R.string.ok is android.R, pre-existing). PR #12 CI passed (run 43709016240). Released 0.9.2.91.

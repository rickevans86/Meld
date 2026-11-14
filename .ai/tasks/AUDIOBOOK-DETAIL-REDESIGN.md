# AUDIOBOOK-DETAIL-REDESIGN

AI: Claude
Status: implemented; owner authorized push to main + release (once CI green) on 2026-11-14. Temporary task ID because GitHub Issues are disabled.
Branch: feat/audiobook-detail-redesign (from main)

Request: make the phone audiobook detail page look like the richer Audiobookshelf-style layout the owner shared (centred cover + Finished badge, Listen Again, Download/Finished row, Narrated By with avatars + See all, Overview with Read more, Genres chips, collapsible Chapters). Owner chose: apply to BOTH the Audiobookshelf and on-phone detail pages; leave "Add to Playlist" off for now.

Changes:
- AbsClient.kt: AbsBook now also parses narrators (list), description, genres from the item metadata.
- New ui/screens/library/audiobooks/AudiobookDetailUi.kt: shared internal composables — DetailCover (cover + Finished badge), AudiobookHero (centred cover/title/authors/meta + progress + primary button + a caller-supplied secondary RowScope of DetailActionButtons), NarratedBy (initial avatars, See all/less), DetailOverview (Read more/less), GenreChips (FlowRow), DetailSectionTitle, CollapsibleSectionTitle (count + chevron).
- AudiobookshelfContent.bookDetail rewritten to the hero + Narrated By + Overview + Genres + collapsible Chapters; the Download button is compact in the action row (states: download / % / remove / retry); old DownloadControls removed.
- AudiobooksScreen (on-phone) BookHeader rewritten to the shared hero (cover + Finished badge, Finished action button); chapters moved into a collapsible section. Local books show fewer sections (no narrators/overview/genres — they aren't available on device).
- Strings: audiobook_narrated_by_title, audiobook_overview, audiobook_genres, audiobook_see_all, audiobook_see_less, audiobook_read_more, audiobook_read_less (default English only).
No database schema or version changes.

Tests: none (pure Compose UI). Resource check clean; XML valid. CI result recorded on merge.

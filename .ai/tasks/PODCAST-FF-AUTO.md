# PODCAST-FF-AUTO

AI: Claude
Status: implemented; CI-verified on PR. Release pending owner go-ahead. Temporary task ID because GitHub Issues are disabled.
Branch: feat/podcast-ff-auto (from main)

Follow-up to PODCAST-FF-REWIND (0.9.2.91): the dedicated podcast fast-forward / rewind buttons were phone-only. The owner's car screenshots showed Android Auto with just previous/next. Owner chose: add the seek buttons ALONGSIDE previous/next (not replacing them).

Changes:
- MediaSessionConstants: new ACTION_SEEK_BACK/FORWARD + CommandSeekBack/Forward (separate from the old CommandSkip*, which the repurpose-next/previous setting uses).
- MusicService: podcastSeekButtonSetting (dedicated amounts) loaded from PodcastSkip.ButtonBackKey/ButtonForwardKey; podcastSeekButtonAmounts() (podcast episode, always); podcastSeekButton(forward) seeks by the dedicated amounts; updateNotification adds two SLOT_OVERFLOW seek buttons for podcast episodes so they appear next to prev/next rather than replacing them (SLOT_BACK/FORWARD are left for the old skip feature).
- MediaLibrarySessionCallback: registers CommandSeekBack/Forward and routes them to podcastSeekButton.
- Reuses existing strings podcast_skip_back_by / podcast_skip_forward_by. No new strings, no schema/version changes.

Tests: none (media-session/AA). Resource check clean. CI result recorded on merge.

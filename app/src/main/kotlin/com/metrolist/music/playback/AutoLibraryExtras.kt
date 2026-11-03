/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.audiobooks.AudiobookRepository
import com.metrolist.music.audiobooks.abs.AbsRepository
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.constants.isAudiobookId
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.localfiles.LocalMusicFolders
import com.metrolist.music.models.toMediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * Android Auto sections for local files, audiobooks (on the phone and Audiobookshelf) and podcasts.
 * Media ids are "/"-separated paths under [LOCAL_FILES], [AUDIOBOOKS] and [PODCASTS].
 */
class AutoLibraryExtras
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val audiobooks: AudiobookRepository,
    private val audiobookshelf: AbsRepository,
    private val localMusicFolders: LocalMusicFolders,
    private val podcastFeeds: com.metrolist.music.podcasts.PodcastDirectory,
    private val news: com.metrolist.music.podcasts.NewsBriefing,
    private val radio: com.metrolist.music.radio.RadioDirectory,
    private val subsonic: com.metrolist.music.subsonic.SubsonicRepository,
) {
    fun isBrowsable(mediaId: String): Boolean =
        mediaId == LOCAL_FILES ||
            mediaId == NEWS ||
            mediaId == RADIO ||
            mediaId == SUBSONIC ||
            mediaId == SUB_STARRED ||
            mediaId == SUB_PLAYLISTS ||
            mediaId == SUB_ALBUMS ||
            ((mediaId.startsWith("$SUB_ALBUM/") || mediaId.startsWith("$SUB_PLAYLIST/")) && mediaId.count { it == '/' } == 2) ||
            mediaId == AUDIOBOOKS ||
            mediaId == ABS_ALL ||
            mediaId == PODCASTS ||
            mediaId == SAVED_EPISODES ||
            (mediaId.startsWith("$PODCAST_CHANNEL/") && mediaId.count { it == '/' } == 2) ||
            (mediaId.startsWith("$PODCAST_FEED/") && mediaId.count { it == '/' } == 2)

    fun rootItem(section: String): MediaItem = when (section) {
        LOCAL_FILES -> folder(LOCAL_FILES, context.getString(R.string.local_files), null, icon(R.drawable.folder), MediaMetadata.MEDIA_TYPE_PLAYLIST)
        NEWS -> folder(NEWS, context.getString(R.string.news_briefing), null, icon(R.drawable.newspaper), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
        RADIO -> folder(RADIO, context.getString(R.string.radio_tab), null, icon(R.drawable.radio), MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
        SUBSONIC -> folder(SUBSONIC, context.getString(R.string.subsonic_tab), null, icon(R.drawable.dns), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        AUDIOBOOKS -> folder(AUDIOBOOKS, context.getString(R.string.filter_audiobooks), null, icon(R.drawable.audiobook), MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
        else -> folder(PODCASTS, context.getString(R.string.filter_podcasts), null, icon(R.drawable.mic), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
    }

    /** Children for one of this class's folders, or null when [parentId] isn't one of them. */
    suspend fun children(parentId: String): List<MediaItem>? = when {
        parentId == LOCAL_FILES -> {
            val songs = localSongs()
            if (songs.isEmpty()) emptyList() else listOf(shuffleItem(LOCAL_FILES)) + songs.map { it.toAutoItem(LOCAL_FILES) }
        }
        parentId == NEWS -> newsItems()
        parentId == RADIO -> radioStations().map { station ->
            playable("$RADIO/${station.id}", station.name, station.details.ifBlank { null }, station.favicon?.toUri() ?: icon(R.drawable.radio))
        }
        parentId == SUBSONIC -> subsonicRoot()
        parentId == SUB_STARRED -> serverSongsWithShuffle(SUB_STARRED) { subsonic.starredSongs() }
        parentId == SUB_PLAYLISTS -> serverCall { subsonic.playlists() }.orEmpty().map {
            folder("$SUB_PLAYLIST/${it.id}", it.name, null, subsonic.coverUrl(it.coverArt)?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST)
        }
        parentId == SUB_ALBUMS -> serverCall { subsonic.albums("newest") }.orEmpty().map {
            folder("$SUB_ALBUM/${it.id}", it.name, it.artist, subsonic.coverUrl(it.coverArt)?.toUri(), MediaMetadata.MEDIA_TYPE_ALBUM)
        }
        parentId.startsWith("$SUB_ALBUM/") -> serverSongsWithShuffle(parentId) { subsonic.albumSongs(parentId.substringAfterLast('/')) }
        parentId.startsWith("$SUB_PLAYLIST/") -> serverSongsWithShuffle(parentId) { subsonic.playlistSongs(parentId.substringAfterLast('/')) }
        parentId == AUDIOBOOKS -> audiobookItems()
        parentId == ABS_ALL -> absAllBooks()
        parentId == PODCASTS -> podcastFolders()
        parentId == SAVED_EPISODES -> savedEpisodes().map { it.toAutoItem(SAVED_EPISODES) }
        parentId.startsWith("$PODCAST_CHANNEL/") -> channelEpisodes(parentId)
        parentId.startsWith("$PODCAST_FEED/") -> feedEpisodes(parentId)
        else -> null
    }

    /** What to queue when a playable item from these sections is chosen, or null when it isn't ours. */
    suspend fun playback(path: List<String>): MediaItemsWithStartPosition? = when (path.firstOrNull()) {
        LOCAL_FILES -> {
            val songs = localSongs()
            val id = path.getOrNull(1)
            if (id == SHUFFLE) {
                MediaItemsWithStartPosition(songs.shuffled().map { it.toMediaItem() }, 0, C.TIME_UNSET)
            } else {
                MediaItemsWithStartPosition(
                    songs.map { it.toMediaItem() },
                    songs.indexOfFirst { it.id == id }.coerceAtLeast(0),
                    C.TIME_UNSET,
                )
            }
        }
        NEWS -> playNews(path)
        RADIO -> {
            val stations = radioStations()
            val songs = radio.prepare(stations)
            val start = songs.indexOfFirst { it.id == com.metrolist.music.constants.RADIO_ID_PREFIX + path.getOrNull(1) }
            if (songs.isEmpty()) null else MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, start.coerceAtLeast(0), C.TIME_UNSET)
        }
        SUBSONIC -> playSubsonic(path)
        AUDIOBOOKS -> playAudiobook(path)
        PODCASTS -> playPodcast(path)
        else -> null
    }

    private suspend fun localSongs(): List<Song> {
        val all = database.localSongs(SongSortType.NAME, false).first().filterNot { isAudiobookId(it.id) }
        val choices = localMusicFolders.currentChoices()
        val included = runCatching { localMusicFolders.includedFolderById() }.getOrNull()
        val indexedPaths = localMusicFolders.index.value.orEmpty().mapTo(HashSet()) { it.path }
        return all.filter { song ->
            when {
                song.id.startsWith("local:doc:") ->
                    choices.added.any { tree -> song.song.localPath?.startsWith(tree) == true } &&
                        song.song.localPath?.let(LocalMusicFolders::documentPath) !in indexedPaths
                included == null -> true
                else -> song.id.removePrefix("local:").toLongOrNull()?.let { it in included } ?: true
            }
        }
    }

    /** Favourite stations, or popular ones near you when there are none yet. */
    private suspend fun radioStations(): List<com.metrolist.music.radio.RadioStation> =
        radio.favourites.value.ifEmpty {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) { runCatching { radio.popular().take(40) }.getOrNull() }.orEmpty()
        }

    private fun subsonicRoot(): List<MediaItem> {
        if (subsonic.session.value == null) return emptyList()
        return listOf(
            folder(SUB_STARRED, context.getString(R.string.subsonic_starred), null, icon(R.drawable.favorite), MediaMetadata.MEDIA_TYPE_PLAYLIST),
            folder(SUB_PLAYLISTS, context.getString(R.string.playlists), null, icon(R.drawable.queue_music), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            folder(SUB_ALBUMS, context.getString(R.string.subsonic_recent_albums), null, icon(R.drawable.album), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
        )
    }

    private suspend fun <T> serverCall(block: suspend () -> T): T? =
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            runCatching { block() }.onFailure { Timber.tag(TAG).w(it, "Server request failed") }.getOrNull()
        }

    private suspend fun serverSongs(
        load: suspend () -> List<com.metrolist.music.subsonic.SubsonicSong>,
    ): List<Song> = serverCall { subsonic.prepare(load()) }.orEmpty()

    private suspend fun serverSongsWithShuffle(
        parent: String,
        load: suspend () -> List<com.metrolist.music.subsonic.SubsonicSong>,
    ): List<MediaItem> {
        val songs = serverSongs(load)
        return if (songs.isEmpty()) emptyList() else listOf(shuffleItem(parent)) + songs.map { it.toAutoItem(parent) }
    }

    /** Paths: subsonic/starred/<song>, subsonic/album/<album>/<song>, subsonic/playlist/<playlist>/<song>. */
    private suspend fun playSubsonic(path: List<String>): MediaItemsWithStartPosition? {
        val (songs, chosen) = when (path.getOrNull(1)) {
            "starred" -> serverSongs { subsonic.starredSongs() } to path.getOrNull(2)
            "album" -> serverSongs { subsonic.albumSongs(path.getOrNull(2).orEmpty()) } to path.getOrNull(3)
            "playlist" -> serverSongs { subsonic.playlistSongs(path.getOrNull(2).orEmpty()) } to path.getOrNull(3)
            else -> return null
        }
        if (songs.isEmpty()) return null
        if (chosen == SHUFFLE) return MediaItemsWithStartPosition(songs.shuffled().map { it.toMediaItem() }, 0, C.TIME_UNSET)
        val start = songs.indexOfFirst { it.id == chosen }.coerceAtLeast(0)
        return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, start, C.TIME_UNSET)
    }

    private suspend fun enabledNewsSources() =
        news.choices.first().filter { it.enabled }.map { it.source }

    private suspend fun newsItems(): List<MediaItem> {
        val sources = enabledNewsSources()
        if (sources.isEmpty()) return emptyList()
        return listOf(
            playable("$NEWS/$NEWS_ALL", context.getString(R.string.news_play_all), sources.joinToString(" · ") { it.name }, icon(R.drawable.newspaper)),
        ) + sources.mapIndexed { index, source ->
            playable("$NEWS/$index", source.name, source.region, source.artworkUrl?.toUri() ?: icon(R.drawable.newspaper))
        }
    }

    private suspend fun playNews(path: List<String>): MediaItemsWithStartPosition? {
        val sources = enabledNewsSources()
        val chosen = when (val which = path.getOrNull(1)) {
            NEWS_ALL -> sources
            else -> listOfNotNull(which?.toIntOrNull()?.let(sources::getOrNull))
        }
        val bulletins = news.latestBulletins(chosen)
        if (bulletins.isEmpty()) return null
        return MediaItemsWithStartPosition(bulletins.map { it.toMediaItem() }, 0, C.TIME_UNSET)
    }

    private suspend fun audiobookItems(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val seen = HashSet<String>()

        if (audiobookshelf.session.value != null) {
            val inProgress = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                runCatching { audiobookshelf.inProgress() }.getOrNull()
            }.orEmpty()
            inProgress.forEach { book ->
                if (seen.add(book.id)) {
                    items += playable("$AUDIOBOOKS/abs/${book.id}", book.title, book.author, audiobookshelf.coverUrl(book.id)?.toUri())
                }
            }
            audiobookshelf.downloadedBooks().forEach { book ->
                if (seen.add(book.id)) {
                    items += playable("$AUDIOBOOKS/abs/${book.id}", book.title, book.author, audiobookshelf.coverUrl(book.id)?.toUri())
                }
            }
        }

        val progress = audiobooks.progress.value
        audiobooks.books.value
            .sortedByDescending { progress[it.id]?.updatedAt ?: 0L }
            .forEach { book ->
                items += playable("$AUDIOBOOKS/device/${book.id}", book.title, book.author, book.coverUri?.toUri())
            }

        if (audiobookshelf.session.value != null) {
            items += folder(ABS_ALL, context.getString(R.string.auto_all_audiobookshelf_books), null, icon(R.drawable.audiobook))
        }
        return items
    }

    private suspend fun absAllBooks(): List<MediaItem> {
        val library = audiobookshelf.selectedLibraryId()
            ?: runCatching { audiobookshelf.libraries().firstOrNull()?.id }.getOrNull()
            ?: return emptyList()
        val books = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            runCatching { audiobookshelf.books(library, 0, 200).books }.getOrNull()
        }.orEmpty()
        return books.map { playable("$AUDIOBOOKS/abs/${it.id}", it.title, it.author, audiobookshelf.coverUrl(it.id)?.toUri()) }
    }

    private suspend fun playAudiobook(path: List<String>): MediaItemsWithStartPosition? {
        val id = path.getOrNull(2) ?: return null
        return when (path.getOrNull(1)) {
            "device" -> {
                val book = audiobooks.books.value.firstOrNull { it.id == id } ?: return null
                val chapters = audiobooks.chapters(book)
                if (chapters.isEmpty()) return null
                val progress = audiobooks.progress.value[book.id]
                val start = if (progress == null || progress.finished) 0 else book.chapterIds.indexOf(progress.chapterId).coerceAtLeast(0)
                MediaItemsWithStartPosition(chapters.map { it.toMediaItem() }, start, C.TIME_UNSET)
            }
            "abs" -> runCatching {
                val prepared = audiobookshelf.prepare(id, null)
                MediaItemsWithStartPosition(prepared.songs.map { it.toMediaItem() }, prepared.startIndex, C.TIME_UNSET)
            }.onFailure { Timber.tag(TAG).w(it, "Could not start Audiobookshelf book $id") }.getOrNull()
            else -> null
        }
    }

    private suspend fun podcastFolders(): List<MediaItem> {
        val saved = folder(SAVED_EPISODES, context.getString(R.string.auto_saved_episodes), null, icon(R.drawable.mic))
        val channels = database.subscribedPodcasts().first().map { podcast ->
            folder("$PODCAST_CHANNEL/${podcast.id}", podcast.title, podcast.author, podcast.thumbnailUrl?.toUri())
        }
        val feeds = podcastFeeds.followed.value.map { show ->
            folder("$PODCAST_FEED/${feedKey(show.feedUrl)}", show.title, show.author, show.artworkUrl?.toUri())
        }
        return listOf(saved) + channels + feeds
    }

    private fun followedFeed(key: String) =
        podcastFeeds.followed.value.firstOrNull { feedKey(it.feedUrl) == key }

    private suspend fun feedEpisodes(parentId: String): List<MediaItem> {
        val show = followedFeed(parentId.removePrefix("$PODCAST_FEED/").substringBefore('/')) ?: return emptyList()
        val feed = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { runCatching { podcastFeeds.feed(show) }.getOrNull() }
            ?: return emptyList()
        return feed.episodes.map { episode ->
            playable("$parentId/${episode.id}", episode.title, feed.show.title, (episode.imageUrl ?: feed.show.artworkUrl)?.toUri())
        }
    }

    private fun feedKey(feedUrl: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(feedUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)

    private suspend fun savedEpisodes(): List<Song> = database.savedPodcastEpisodesByCreateDateAsc().first()

    private suspend fun channelEpisodes(parentId: String): List<MediaItem> {
        val podcastId = parentId.removePrefix("$PODCAST_CHANNEL/").substringBefore('/')
        val episodes = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { YouTube.podcast(podcastId).getOrNull()?.episodes }.orEmpty()
        return episodes.map { episode ->
            playable(
                "$PODCAST_CHANNEL/$podcastId/${episode.id}",
                episode.title,
                episode.author?.name,
                episode.thumbnail.toUri(),
            )
        }
    }

    private suspend fun playPodcast(path: List<String>): MediaItemsWithStartPosition? = when (path.getOrNull(1)) {
        "saved" -> {
            val episodes = savedEpisodes()
            val id = path.getOrNull(2)
            MediaItemsWithStartPosition(
                episodes.map { it.toMediaItem() },
                episodes.indexOfFirst { it.id == id }.coerceAtLeast(0),
                C.TIME_UNSET,
            )
        }
        "feed" -> {
            val show = path.getOrNull(2)?.let(::followedFeed)
            val episodeId = path.getOrNull(3)
            val feed = show?.let { runCatching { podcastFeeds.feed(it) }.getOrNull() }
            if (feed == null) {
                null
            } else {
                val songs = podcastFeeds.prepare(feed)
                MediaItemsWithStartPosition(
                    songs.map { it.toMediaItem() },
                    feed.episodes.indexOfFirst { it.id == episodeId }.coerceAtLeast(0),
                    C.TIME_UNSET,
                )
            }
        }
        "channel" -> {
            val podcastId = path.getOrNull(2)
            val episodeId = path.getOrNull(3)
            if (podcastId == null) {
                null
            } else {
                val episodes = YouTube.podcast(podcastId).getOrNull()?.episodes.orEmpty()
                if (episodes.isEmpty()) {
                    null
                } else {
                    MediaItemsWithStartPosition(
                        episodes.map { it.toMediaMetadata().toMediaItem() },
                        episodes.indexOfFirst { it.id == episodeId }.coerceAtLeast(0),
                        C.TIME_UNSET,
                    )
                }
            }
        }
        else -> null
    }

    private fun Song.toAutoItem(parent: String): MediaItem =
        playable("$parent/$id", song.title, artists.joinToString { it.name }.ifBlank { null }, song.thumbnailUrl?.toUri())

    private fun shuffleItem(parent: String): MediaItem =
        playable("$parent/$SHUFFLE", context.getString(R.string.shuffle), null, icon(R.drawable.shuffle))

    private fun playable(id: String, title: String, subtitle: String?, artwork: Uri?) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(artwork)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            ).build()

    private fun folder(
        id: String,
        title: String,
        subtitle: String?,
        artwork: Uri?,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artwork)
                .setIsPlayable(false)
                .setIsBrowsable(true)
                .setMediaType(mediaType)
                .build(),
        ).build()

    private fun icon(@DrawableRes id: Int): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.resources.getResourcePackageName(id))
            .appendPath(context.resources.getResourceTypeName(id))
            .appendPath(context.resources.getResourceEntryName(id))
            .build()

    companion object {
        private const val TAG = "AutoLibraryExtras"
        private const val NETWORK_TIMEOUT_MS = 8_000L
        const val LOCAL_FILES = "local_files"
        const val NEWS = "news"
        private const val NEWS_ALL = "__all__"
        const val AUDIOBOOKS = "audiobooks"
        const val PODCASTS = "podcasts"
        const val RADIO = "radio"
        const val SUBSONIC = "subsonic"
        private const val SUB_STARRED = "$SUBSONIC/starred"
        private const val SUB_PLAYLISTS = "$SUBSONIC/playlists"
        private const val SUB_ALBUMS = "$SUBSONIC/albums"
        private const val SUB_ALBUM = "$SUBSONIC/album"
        private const val SUB_PLAYLIST = "$SUBSONIC/playlist"
        private const val ABS_ALL = "$AUDIOBOOKS/abs_all"
        private const val SAVED_EPISODES = "$PODCASTS/saved"
        private const val PODCAST_CHANNEL = "$PODCASTS/channel"
        private const val PODCAST_FEED = "$PODCASTS/feed"
        private const val SHUFFLE = "__shuffle__"
    }
}

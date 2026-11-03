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
) {
    fun isBrowsable(mediaId: String): Boolean =
        mediaId == LOCAL_FILES ||
            mediaId == AUDIOBOOKS ||
            mediaId == ABS_ALL ||
            mediaId == PODCASTS ||
            mediaId == SAVED_EPISODES ||
            (mediaId.startsWith("$PODCAST_CHANNEL/") && mediaId.count { it == '/' } == 2) ||
            (mediaId.startsWith("$PODCAST_FEED/") && mediaId.count { it == '/' } == 2)

    fun rootItem(section: String): MediaItem = when (section) {
        LOCAL_FILES -> folder(LOCAL_FILES, context.getString(R.string.local_files), null, icon(R.drawable.folder), MediaMetadata.MEDIA_TYPE_PLAYLIST)
        AUDIOBOOKS -> folder(AUDIOBOOKS, context.getString(R.string.filter_audiobooks), null, icon(R.drawable.audiobook), MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
        else -> folder(PODCASTS, context.getString(R.string.filter_podcasts), null, icon(R.drawable.mic), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
    }

    /** Children for one of this class's folders, or null when [parentId] isn't one of them. */
    suspend fun children(parentId: String): List<MediaItem>? = when {
        parentId == LOCAL_FILES -> {
            val songs = localSongs()
            if (songs.isEmpty()) emptyList() else listOf(shuffleItem(LOCAL_FILES)) + songs.map { it.toAutoItem(LOCAL_FILES) }
        }
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
        const val AUDIOBOOKS = "audiobooks"
        const val PODCASTS = "podcasts"
        private const val ABS_ALL = "$AUDIOBOOKS/abs_all"
        private const val SAVED_EPISODES = "$PODCASTS/saved"
        private const val PODCAST_CHANNEL = "$PODCASTS/channel"
        private const val PODCAST_FEED = "$PODCASTS/feed"
        private const val SHUFFLE = "__shuffle__"
    }
}

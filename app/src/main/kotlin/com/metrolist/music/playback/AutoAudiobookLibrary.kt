/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.playback

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.metrolist.music.R
import com.metrolist.music.audiobooks.Audiobook
import com.metrolist.music.audiobooks.AudiobookRepository
import com.metrolist.music.audiobooks.AutoAudiobookSection
import com.metrolist.music.audiobooks.AutoAudiobookSections
import com.metrolist.music.audiobooks.autoTitleRes
import com.metrolist.music.audiobooks.abs.AbsBookSummary
import com.metrolist.music.audiobooks.abs.AbsBrowseKind
import com.metrolist.music.audiobooks.abs.AbsRepository
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

internal const val AUTO_GROUP_TITLE = "android.media.browse.CONTENT_STYLE_GROUP_TITLE"
internal const val AUTO_BROWSABLE_STYLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
internal const val AUTO_PLAYABLE_STYLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
internal const val AUTO_SINGLE_STYLE = "android.media.browse.CONTENT_STYLE_SINGLE_ITEM"

internal fun autoGridItem(item: MediaItem, group: String? = null): MediaItem = item.buildUpon()
    .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
        putInt(AUTO_BROWSABLE_STYLE, 2)
        putInt(AUTO_PLAYABLE_STYLE, 2)
        putInt(AUTO_SINGLE_STYLE, 2)
        if (group != null) putString(AUTO_GROUP_TITLE, group)
    }).build()).build()

internal fun autoAudiobookPlaybackPath(path: List<String>): List<String>? {
    if (path.firstOrNull() != AutoLibraryExtras.AUDIOBOOKS) return null
    if (path.getOrNull(1) != "shelf") return path
    if (path.size != 5 || AutoAudiobookSection.entries.none { it.id == path[2] } ||
        path[3] !in listOf("device", "abs") || path[4].isBlank()) return null
    return listOf(AutoLibraryExtras.AUDIOBOOKS, path[3], path[4])
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoAudiobookLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val local: AudiobookRepository,
    private val abs: AbsRepository,
) {
    @Volatile private var defaultLibrary: Pair<String, String>? = null
    @Volatile private var shelfCache: Pair<String, Map<String, List<AbsBookSummary>>>? = null

    fun rootItem(): MediaItem = folder(AutoLibraryExtras.AUDIOBOOKS, R.string.filter_audiobooks, R.drawable.audiobook)

    fun isBrowsable(id: String): Boolean {
        if (id == AutoLibraryExtras.AUDIOBOOKS || id == LEGACY_ALL) return true
        val path = id.split('/')
        if (path.firstOrNull() != AutoLibraryExtras.AUDIOBOOKS) return false
        return when (path.getOrNull(1)) {
            "library", "abs_all" -> path.size in 2..3 && (path.size == 2 || page(path[2]) != null)
            "groups" -> path.size == 4 && kind(path[2]) != null && page(path[3]) != null
            "group" -> path.size == 5 && kind(path[2]) != null && page(path[3]) != null && path[4].isNotBlank()
            "local_playlists" -> path.size == 2
            "local_playlist" -> path.size == 3 && path[2].isNotBlank()
            else -> false
        }
    }

    suspend fun children(id: String): List<MediaItem>? {
        if (!isBrowsable(id)) return null
        if (id == AutoLibraryExtras.AUDIOBOOKS) return home()
        val path = id.split('/')
        return when (path[1]) {
            "library", "abs_all" -> library(page(path.getOrElse(2) { "0" }) ?: 0, path[1] == "abs_all")
            "groups" -> groups(kind(path[2])!!, page(path[3])!!)
            "group" -> {
                val library = libraryId() ?: return emptyList()
                val result = remote { abs.groups(library, kind(path[2])!!, page(path[3])!!, GROUP_PAGE_SIZE) }
                result?.groups?.firstOrNull { it.id == Uri.decode(path[4]) }?.books.orEmpty().map { bookItem(it) }
            }
            "local_playlists" -> localPlaylists()
            "local_playlist" -> {
                localPlaylistItems(Uri.decode(path[2]))
            }
            else -> emptyList()
        }
    }

    suspend fun downloaded(): List<MediaItem> = local.books.value.map { bookItem(it) } +
        if (abs.session.value != null) abs.downloadedBooks().map {
            bookItem(AbsBookSummary(it.id, it.title, it.author, it.durationSec))
        } else emptyList()

    private suspend fun home(): List<MediaItem> {
        val prefs = context.dataStore.data.first()
        val hidden = prefs[AutoLibraryExtras.ContinueHiddenKey].orEmpty()
        val sections = AutoAudiobookSections.decode(prefs[AutoAudiobookSections.Key].orEmpty())
        val enabled = sections.filter { it.second }.map { it.first }
        val shelves = if (enabled.any { it != AutoAudiobookSection.BROWSE }) serverShelves() else emptyMap()
        val rows = enabled.map { section ->
            val books = when (section) {
                AutoAudiobookSection.CONTINUE -> {
                    val progress = local.progress.value
                    local.books.value.filter { progress[it.id]?.finished == false && "book:${it.id}@${progress[it.id]?.updatedAt}" !in hidden }
                        .sortedByDescending { progress[it.id]?.updatedAt }.take(SHELF_SIZE).map { bookItem(it, section) } +
                        shelves["continue-listening"].orEmpty().filter { "abs:${it.id}" !in hidden }.take(SHELF_SIZE).map { bookItem(it, section) }
                }
                AutoAudiobookSection.RECENT -> shelves["recently-added"].orEmpty().take(SHELF_SIZE).map { bookItem(it, section) }
                AutoAudiobookSection.SERIES -> shelves["continue-series"].orEmpty().take(SHELF_SIZE).map { bookItem(it, section) }
                AutoAudiobookSection.BROWSE -> browseTiles()
            }
            books.map { autoGridItem(it, context.getString(section.autoTitleRes())) }
        }
        return rows.flatten()
    }

    private fun browseTiles(): List<MediaItem> = buildList {
        add(folder(LIBRARY, R.string.auto_audiobook_library, R.drawable.auto_audiobook_library))
        if (abs.session.value != null) {
            add(folder("$GROUPS/series/0", R.string.auto_audiobook_series, R.drawable.auto_audiobook_series))
            add(folder("$GROUPS/collections/0", R.string.auto_audiobook_collections, R.drawable.auto_audiobook_collections))
            add(folder("$GROUPS/playlists/0", R.string.playlists, R.drawable.auto_audiobook_playlists))
        } else {
            add(folder(LOCAL_PLAYLISTS, R.string.playlists, R.drawable.auto_audiobook_playlists))
        }
    }

    private suspend fun serverShelves(): Map<String, List<AbsBookSummary>> {
        val session = abs.session.value ?: return emptyMap()
        val library = libraryId() ?: return emptyMap()
        val key = "${session.serverUrl}|${session.username}|$library"
        val fresh = remote { abs.shelves(library) }
        if (fresh != null) shelfCache = key to fresh
        return fresh ?: shelfCache?.takeIf { it.first == key }?.second.orEmpty()
    }

    private suspend fun library(page: Int, serverOnly: Boolean): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        if (page == 0 && !serverOnly) items += local.books.value.sortedBy { it.title.lowercase() }.map { bookItem(it) }
        val library = libraryId()
        val result = library?.let { remote { abs.books(it, page, BOOK_PAGE_SIZE) } }
        val books = result?.books ?: if (page == 0 && abs.session.value != null) {
            abs.downloadedBooks().map { AbsBookSummary(it.id, it.title, it.author, it.durationSec) }
        } else emptyList()
        items += books.map { bookItem(it) }
        if (result != null && (page.toLong() + 1) * BOOK_PAGE_SIZE < result.total && result.books.isNotEmpty()) {
            items += folder("${if (serverOnly) LEGACY_ALL else LIBRARY}/${page + 1}", R.string.auto_audiobook_more, R.drawable.audiobook)
        }
        return items
    }

    private suspend fun groups(kind: AbsBrowseKind, page: Int): List<MediaItem> {
        val library = libraryId() ?: return if (kind == AbsBrowseKind.PLAYLISTS) localPlaylists() else emptyList()
        val result = remote { abs.groups(library, kind, page, GROUP_PAGE_SIZE) }
        val items = result?.groups.orEmpty().map { group ->
            val artwork = group.books.firstOrNull()?.let { abs.coverUrl(it.id)?.toUri() }
            folder("$GROUP/${kind.path}/$page/${Uri.encode(group.id)}", group.name, artwork)
        }.toMutableList()
        if (kind == AbsBrowseKind.PLAYLISTS && page == 0) items += localPlaylists()
        if (result != null && (page.toLong() + 1) * GROUP_PAGE_SIZE < result.total && result.groups.isNotEmpty()) {
            items += folder("$GROUPS/${kind.path}/${page + 1}", R.string.auto_audiobook_more, R.drawable.audiobook)
        }
        return items
    }

    private suspend fun localPlaylists(): List<MediaItem> {
        return database.playlistEntitiesByNameAsc().filter { playlist ->
            playlist.bookmarkedAt != null && database.playlistSongIds(playlist.id).any { chapterId ->
                local.bookForChapter(chapterId) != null ||
                    (abs.session.value != null && com.metrolist.music.constants.isAbsTrackId(chapterId))
            }
        }.map { folder("$LOCAL_PLAYLIST/${Uri.encode(it.id)}", it.name, null) }
    }

    private suspend fun localPlaylistItems(id: String): List<MediaItem> {
        val seen = HashSet<String>()
        return database.playlistSongIds(id).mapNotNull { chapterId ->
            val deviceBook = local.bookForChapter(chapterId)
            if (deviceBook != null) {
                if (seen.add("device:${deviceBook.id}")) bookItem(deviceBook) else null
            } else if (com.metrolist.music.constants.isAbsTrackId(chapterId) && abs.session.value != null) {
                val bookId = chapterId.removePrefix(com.metrolist.music.constants.ABS_TRACK_ID_PREFIX).substringBeforeLast(':')
                if (!seen.add("abs:$bookId")) return@mapNotNull null
                val song = database.getSongById(chapterId) ?: return@mapNotNull null
                bookItem(AbsBookSummary(bookId, song.song.albumName ?: song.song.title, song.artists.joinToString { it.name }, 0.0))
            } else null
        }
    }

    private suspend fun libraryId(): String? {
        val session = abs.session.value ?: return null
        abs.selectedLibraryId()?.let { return it }
        val key = "${session.serverUrl}|${session.username}"
        defaultLibrary?.takeIf { it.first == key }?.let { return it.second }
        val library = remote { abs.libraries().firstOrNull()?.id }
        if (library != null) defaultLibrary = key to library
        return library
    }

    private suspend fun <T> remote(block: suspend () -> T): T? = withTimeoutOrNull(8_000L) {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Timber.tag("AutoAudiobooks").w(e, "Audiobook browse request failed")
            null
        }
    }

    private fun bookItem(book: Audiobook, section: AutoAudiobookSection? = null): MediaItem =
        playable(bookId("device", book.id, section), book.title, book.author, book.coverUri?.toUri())

    private fun bookItem(book: AbsBookSummary, section: AutoAudiobookSection? = null): MediaItem =
        playable(bookId("abs", book.id, section), book.title, book.author, abs.coverUrl(book.id)?.toUri())

    private fun bookId(source: String, id: String, section: AutoAudiobookSection?) =
        if (section == null) "audiobooks/$source/$id" else "audiobooks/shelf/${section.id}/$source/$id"

    private fun playable(id: String, title: String, author: String?, artwork: Uri?): MediaItem = autoGridItem(MediaItem.Builder()
        .setMediaId(id).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(author).setArtist(author)
            .setArtworkUri(artwork).setIsPlayable(true).setIsBrowsable(false).setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK).build()).build())

    private fun folder(id: String, titleRes: Int, icon: Int) = folder(id, context.getString(titleRes),
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/drawable/${context.resources.getResourceEntryName(icon)}"))

    private fun folder(id: String, title: String, artwork: Uri?): MediaItem = autoGridItem(MediaItem.Builder()
        .setMediaId(id).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtworkUri(artwork)
            .setIsPlayable(false).setIsBrowsable(true).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS).build()).build())

    companion object {
        private const val SHELF_SIZE = 12
        private const val BOOK_PAGE_SIZE = 200
        private const val GROUP_PAGE_SIZE = 50
        private const val LIBRARY = "audiobooks/library"
        private const val LEGACY_ALL = "audiobooks/abs_all"
        private const val GROUPS = "audiobooks/groups"
        private const val GROUP = "audiobooks/group"
        private const val LOCAL_PLAYLISTS = "audiobooks/local_playlists"
        private const val LOCAL_PLAYLIST = "audiobooks/local_playlist"
        private fun kind(raw: String) = AbsBrowseKind.entries.find { it.path == raw }
        private fun page(raw: String) = raw.toIntOrNull()?.takeIf { it in 0..(Int.MAX_VALUE / BOOK_PAGE_SIZE - 1) }
    }
}

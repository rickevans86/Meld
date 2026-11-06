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
import com.metrolist.music.utils.dataStore
import com.metrolist.music.constants.podcastPlaylistIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val downloadUtil: DownloadUtil,
    private val subsonic: com.metrolist.music.subsonic.SubsonicRepository,
    private val mediaServer: com.metrolist.music.mediaserver.MediaServerRepository,
) {
    fun isBrowsable(mediaId: String): Boolean =
        mediaId == LOCAL_FILES ||
            (mediaId.startsWith("$LOCAL_FOLDER/") && mediaId.count { it == '/' } == 2) ||
            (mediaId.startsWith("$LIB_ALBUM/") && mediaId.count { it == '/' } == 1) ||
            (mediaId.startsWith("$LIB_ARTIST/") && mediaId.count { it == '/' } == 1) ||
            mediaId == NEW_EPISODES ||
            mediaId == CONTINUE ||
            mediaId == NEWS ||
            mediaId == RADIO ||
            mediaId == SUBSONIC ||
            mediaId == SUB_STARRED ||
            mediaId == SUB_PLAYLISTS ||
            mediaId == SUB_ALBUMS ||
            mediaId == SUB_FOLDERS ||
            (mediaId.startsWith("$SUB_DIR/") && mediaId.count { it == '/' } == 2) ||
            ((mediaId.startsWith("$SUB_ALBUM/") || mediaId.startsWith("$SUB_PLAYLIST/")) && mediaId.count { it == '/' } == 2) ||
            mediaId == MEDIA_SERVER ||
            mediaId == MS_FAVOURITES ||
            mediaId == MS_PLAYLISTS ||
            mediaId == MS_ALBUMS ||
            mediaId == MS_ARTISTS ||
            ((mediaId.startsWith("$MS_ALBUM/") || mediaId.startsWith("$MS_PLAYLIST/") || mediaId.startsWith("$MS_ARTIST/")) &&
                mediaId.count { it == '/' } == 2) ||
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
        CONTINUE -> folder(CONTINUE, context.getString(R.string.auto_continue_listening), null, icon(R.drawable.history), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        SUBSONIC -> folder(SUBSONIC, context.getString(R.string.subsonic_tab), null, icon(R.drawable.dns), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        MEDIA_SERVER -> folder(MEDIA_SERVER, context.getString(R.string.media_server_tab), null, icon(R.drawable.dns), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        AUDIOBOOKS -> folder(AUDIOBOOKS, context.getString(R.string.filter_audiobooks), null, icon(R.drawable.audiobook), MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
        else -> folder(PODCASTS, context.getString(R.string.filter_podcasts), null, icon(R.drawable.mic), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
    }

    /** Children for one of this class's folders, or null when [parentId] isn't one of them. */
    suspend fun children(parentId: String): List<MediaItem>? = when {
        // Folders (with Shuffle all on top) instead of one long list
        parentId == LOCAL_FILES -> {
            val folders = localFolders()
            if (folders.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    playable("$LOCAL_FILES/$SHUFFLE", context.getString(R.string.auto_shuffle_all), null, icon(R.drawable.shuffle)),
                ) + folders.map { (path, songs) ->
                    folder(
                        "$LOCAL_FOLDER/${key(path)}",
                        path.substringAfterLast('/').ifEmpty { context.getString(R.string.local_files) },
                        listOfNotNull(
                            context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size),
                            path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() },
                        ).joinToString(" · "),
                        icon(R.drawable.folder),
                        MediaMetadata.MEDIA_TYPE_PLAYLIST,
                    )
                }
            }
        }
        parentId.startsWith("$LOCAL_FOLDER/") -> {
            val songs = folderSongs(parentId.substringAfterLast('/'))
            if (songs.isEmpty()) emptyList() else listOf(shuffleItem(parentId)) + songs.map { it.toAutoItem(parentId) }
        }
        parentId.startsWith("$LIB_ALBUM/") -> {
            val songs = poolAlbumSongs(parentId.substringAfter('/'))
            if (songs.isEmpty()) emptyList() else listOf(shuffleItem(parentId)) + songs.map { it.toAutoItem(parentId) }
        }
        parentId.startsWith("$LIB_ARTIST/") -> {
            val songs = poolArtistSongs(parentId.substringAfter('/'))
            if (songs.isEmpty()) emptyList() else listOf(shuffleItem(parentId)) + songs.map { it.toAutoItem(parentId) }
        }
        parentId == CONTINUE -> continueListening().mapIndexed { index, entry ->
            playable("$CONTINUE/$index", entry.title, entry.subtitle, entry.artwork)
        }
        parentId == NEW_EPISODES -> newEpisodes(refresh = true).mapIndexed { index, item ->
            playable(
                "$NEW_EPISODES/$index",
                item.mediaMetadata.title?.toString().orEmpty(),
                item.mediaMetadata.artist?.toString() ?: item.mediaMetadata.albumTitle?.toString(),
                item.mediaMetadata.artworkUri,
            )
        }
        parentId == NEWS -> newsItems()
        parentId == RADIO -> radioStations().map { station ->
            playable("$RADIO/${station.id}", station.name, station.details.ifBlank { null }, station.favicon?.toUri() ?: icon(R.drawable.radio))
        }
        parentId == SUBSONIC -> subsonicRoot()
        // Jellyfin / Plex
        parentId == MEDIA_SERVER -> mediaServerRoot()
        parentId == MS_FAVOURITES -> msSongsWithShuffle(MS_FAVOURITES) { mediaServer.favourites() }
        parentId == MS_PLAYLISTS -> serverCall { mediaServer.playlists() }.orEmpty().map {
            folder("$MS_PLAYLIST/${it.id}", it.name, null, it.coverUrl?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST)
        }
        parentId == MS_ALBUMS -> serverCall { mediaServer.albums() }.orEmpty().map {
            folder("$MS_ALBUM/${it.id}", it.title, it.artist, it.coverUrl?.toUri(), MediaMetadata.MEDIA_TYPE_ALBUM)
        }
        parentId == MS_ARTISTS -> serverCall { mediaServer.artists() }.orEmpty().map {
            folder("$MS_ARTIST/${it.id}", it.name, null, it.coverUrl?.toUri(), MediaMetadata.MEDIA_TYPE_ARTIST)
        }
        parentId.startsWith("$MS_ARTIST/") -> serverCall { mediaServer.artistAlbums(parentId.substringAfterLast('/')) }.orEmpty().map {
            folder("$MS_ALBUM/${it.id}", it.title, it.year?.toString(), it.coverUrl?.toUri(), MediaMetadata.MEDIA_TYPE_ALBUM)
        }
        parentId.startsWith("$MS_ALBUM/") -> msSongsWithShuffle(parentId) { mediaServer.albumSongs(parentId.substringAfterLast('/'), null) }
        parentId.startsWith("$MS_PLAYLIST/") -> msSongsWithShuffle(parentId) { mediaServer.playlistSongs(parentId.substringAfterLast('/'), null) }
        parentId == SUB_STARRED -> serverSongsWithShuffle(SUB_STARRED) { subsonic.starredSongs() }
        parentId == SUB_PLAYLISTS -> serverCall { subsonic.playlists() }.orEmpty().map {
            folder("$SUB_PLAYLIST/${it.id}", it.name, null, subsonic.coverUrl(it.coverArt)?.toUri(), MediaMetadata.MEDIA_TYPE_PLAYLIST)
        }
        parentId == SUB_FOLDERS -> treeItems(SUB_FOLDERS, emptyList())
        parentId.startsWith("$SUB_DIR/") -> treeItems(parentId, treePath(parentId.substringAfterLast('/')))
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
            if (id == "folder") {
                playList(folderSongs(path.getOrNull(2).orEmpty()), path.getOrNull(3))
            } else if (id == SHUFFLE) {
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
        CONTINUE -> {
            val entries = continueCache.ifEmpty { continueListening() }
            path.getOrNull(1)?.toIntOrNull()?.let(entries::getOrNull)?.play?.invoke()
        }
        LIB_ALBUM -> playList(poolAlbumSongs(path.getOrNull(1).orEmpty()), path.getOrNull(2))
        LIB_ARTIST -> playList(poolArtistSongs(path.getOrNull(1).orEmpty()), path.getOrNull(2))
        RADIO -> {
            val stations = radioStations()
            val songs = radio.prepare(stations)
            val start = songs.indexOfFirst { it.id == com.metrolist.music.constants.RADIO_ID_PREFIX + path.getOrNull(1) }
            if (songs.isEmpty()) null else MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, start.coerceAtLeast(0), C.TIME_UNSET)
        }
        SUBSONIC -> playSubsonic(path)
        MEDIA_SERVER -> playMediaServer(path)
        AUDIOBOOKS -> playAudiobook(path)
        PODCASTS -> playPodcast(path)
        else -> null
    }

    /** Local songs for Android Auto lists: Music folders settings applied, exported copies collapsed. */
    private suspend fun localSongs(): List<Song> = LocalMusicFolders.uniqueCopies(allowedLocalSongs())

    /**
     * Library songs as Android Auto should list them: local files follow the Music folders settings
     * (so excluded folders like WhatsApp stay out), and copies of the same song collapse into one.
     */
    suspend fun visibleLibrarySongs(songs: List<Song>): List<Song> {
        val allowedLocal = allowedLocalSongs().mapTo(HashSet()) { it.id }
        return LocalMusicFolders.uniqueCopies(
            songs.filter { song ->
                !isAudiobookId(song.id) && (!song.id.startsWith("local:") || song.id in allowedLocal)
            },
        )
    }

    /** Local songs grouped by folder path, A–Z. Every copy is kept here, as in the app's folder view. */
    private suspend fun localFolders(): List<Pair<String, List<Song>>> {
        val songs = allowedLocalSongs()
        val folderById = runCatching { localMusicFolders.includedFolderById() }.getOrNull().orEmpty()
        return songs
            .groupBy { song ->
                song.id.removePrefix("local:").toLongOrNull()?.let(folderById::get)
                    ?: song.song.localPath?.takeIf { song.id.startsWith("local:doc:") }?.let(LocalMusicFolders::documentFolder)
                    ?: ""
            }
            .toList()
            .sortedBy { it.first.lowercase() }
    }

    private suspend fun folderSongs(key: String): List<Song> =
        localFolders().firstOrNull { key(it.first) == key }?.second.orEmpty()

    /** Downloaded, saved and local songs: what Albums and Artists are built from. */
    private suspend fun songPool(): List<Song> {
        val downloaded = downloadUtil.downloads.value
            .filterValues { it.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED }.keys.toList()
        val library = database.songsByCreateDateAsc().first()
        val downloadedSongs = downloaded.chunked(500).flatMap { database.getSongsByIds(it) }
        return visibleLibrarySongs((library + downloadedSongs + allowedLocalSongs()).distinctBy { it.id })
    }

    /** Albums from downloaded and local songs, skipping titles already in the saved library. */
    suspend fun poolAlbums(skipTitles: Set<String>): List<MediaItem> =
        songPool()
            .filter { !it.song.albumName.isNullOrBlank() && it.song.albumName!!.lowercase() !in skipTitles }
            .groupBy { it.song.albumName!!.trim() }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (name, songs) ->
                folder(
                    "$LIB_ALBUM/${key(name.lowercase())}",
                    name,
                    songs.firstNotNullOfOrNull { s -> s.artists.firstOrNull()?.name },
                    songs.firstNotNullOfOrNull { it.song.thumbnailUrl }?.toUri(),
                    MediaMetadata.MEDIA_TYPE_ALBUM,
                )
            }

    /** Artists from downloaded and local songs, skipping ones already in the saved library. */
    suspend fun poolArtists(skipIds: Set<String>): List<MediaItem> {
        val byArtist = LinkedHashMap<String, MutableList<Song>>()
        val names = HashMap<String, String>()
        songPool().forEach { song ->
            song.artists.filter { it.id !in skipIds && it.name.isNotBlank() }.forEach { artist ->
                byArtist.getOrPut(artist.id) { mutableListOf() }.add(song)
                names[artist.id] = artist.name
            }
        }
        return byArtist.entries
            .sortedBy { names[it.key].orEmpty().lowercase() }
            .map { (artistId, songs) ->
                folder(
                    "$LIB_ARTIST/${key(artistId)}",
                    names[artistId].orEmpty(),
                    context.resources.getQuantityString(R.plurals.n_song, songs.size, songs.size),
                    null,
                    MediaMetadata.MEDIA_TYPE_ARTIST,
                )
            }
    }

    private suspend fun poolAlbumSongs(albumKey: String): List<Song> =
        songPool().filter { s -> s.song.albumName?.trim()?.lowercase()?.let { key(it) } == albumKey }

    private suspend fun poolArtistSongs(artistKey: String): List<Song> =
        songPool().filter { s -> s.artists.any { key(it.id) == artistKey } }

    private fun playList(songs: List<Song>, chosen: String?): MediaItemsWithStartPosition? {
        if (songs.isEmpty()) return null
        if (chosen == SHUFFLE) return MediaItemsWithStartPosition(songs.shuffled().map { it.toMediaItem() }, 0, C.TIME_UNSET)
        return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, songs.indexOfFirst { it.id == chosen }.coerceAtLeast(0), C.TIME_UNSET)
    }

    private fun key(value: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)

    /**
     * Wavelength's combined New episodes: recent episodes from followed feeds, then YouTube's.
     * Kept for a few minutes so the list and what plays match.
     */
    @Volatile
    private var newEpisodesCache: Pair<Long, List<MediaItem>>? = null

    private suspend fun newEpisodes(refresh: Boolean): List<MediaItem> {
        val cached = newEpisodesCache
        if (cached != null && (!refresh || System.currentTimeMillis() - cached.first < NEW_EPISODES_TTL_MS)) return cached.second
        val items = coroutineScope {
            val rss = async {
                withTimeoutOrNull(NETWORK_TIMEOUT_MS * 2) { runCatching { podcastFeeds.recentFromFollowed() }.getOrNull() }.orEmpty()
            }
            val yt = async { withTimeoutOrNull(NETWORK_TIMEOUT_MS) { YouTube.newEpisodes().getOrNull() }.orEmpty() }
            com.metrolist.music.podcasts.PlayedEpisodes.init(context)
            val hidePlayed = context.dataStore.data.first()[com.metrolist.music.podcasts.HidePlayedEpisodesKey] ?: false
            fun keep(id: String) = !hidePlayed || !com.metrolist.music.podcasts.PlayedEpisodes.isPlayed(id)
            rss.await().filter { keep(it.first.id) }.map { it.first.toMediaItem() } +
                yt.await().filter { keep(it.id) }.map { it.toMediaItem() }
        }
        newEpisodesCache = System.currentTimeMillis() to items
        return items
    }

    /** One thing to resume: a half-played podcast episode or an unfinished audiobook. */
    private class ContinueEntry(
        val title: String,
        val subtitle: String?,
        val artwork: Uri?,
        val at: Long,
        val play: suspend () -> MediaItemsWithStartPosition?,
    )

    @Volatile
    private var continueCache: List<ContinueEntry> = emptyList()

    /** Half-finished podcast episodes and audiobooks, most recent first. */
    private suspend fun continueListening(): List<ContinueEntry> {
        com.metrolist.music.podcasts.PlayedEpisodes.init(context)
        val entries = mutableListOf<ContinueEntry>()
        database.inProgressEpisodes().filterNot { com.metrolist.music.podcasts.PlayedEpisodes.isPlayed(it.id) }
            .forEachIndexed { index, song ->
                entries += ContinueEntry(
                    title = song.song.title,
                    subtitle = song.song.albumName ?: song.artists.firstOrNull()?.name,
                    artwork = song.song.thumbnailUrl?.toUri(),
                    // Already newest first; keep that order among episodes
                    at = Long.MAX_VALUE / 2 - index,
                    play = { MediaItemsWithStartPosition(listOf(song.toMediaItem()), 0, C.TIME_UNSET) },
                )
            }
        audiobooks.books.value.forEach { book ->
            val progress = audiobooks.progress.value[book.id] ?: return@forEach
            if (progress.finished) return@forEach
            entries += ContinueEntry(
                title = book.title,
                subtitle = book.author,
                artwork = book.coverUri?.toUri(),
                at = progress.updatedAt,
                play = { playAudiobook(listOf(AUDIOBOOKS, "device", book.id)) },
            )
        }
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) { runCatching { audiobookshelf.inProgress() }.getOrNull() }.orEmpty()
            .forEach { book ->
                entries += ContinueEntry(
                    title = book.title,
                    subtitle = book.author,
                    artwork = audiobookshelf.coverUrl(book.id)?.toUri(),
                    at = 0,
                    play = { playAudiobook(listOf(AUDIOBOOKS, "abs", book.id)) },
                )
            }
        // Episodes are ordered by when they were last played; books by their own progress time
        return entries.sortedByDescending { it.at }.also { continueCache = it }
    }

    /**
     * "Hey Google, play … on Wavelength": the news, a favourite or searched radio station, the newest
     * episode of a followed podcast, an audiobook, or Navidrome music. Null lets YouTube search handle it.
     */
    suspend fun voicePlay(query: String): MediaItemsWithStartPosition? = runCatching {
        val words = query.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.isNotBlank() }
        val filler = setOf("play", "the", "latest", "newest", "new", "episode", "of", "on", "wavelength", "please", "some")
        val core = words.filterNot { it in filler }.joinToString("")
        val wantsRadio = words.any { it in setOf("radio", "station", "fm", "am") }
        val wantsServer = words.any { it in setOf("navidrome", "server") }
        val core2 = words.filterNot { it in filler || it in setOf("radio", "station", "podcast", "navidrome", "server", "my", "from") }
            .joinToString("")
        if (core.isEmpty()) return@runCatching null

        // The news
        if (core in setOf("news", "newsbriefing", "mynews", "briefing", "headlines")) {
            return@runCatching playNews(listOf(NEWS, NEWS_ALL))
        }
        // Favourite radio station, or a search when they said "radio" / "FM"
        val favourites = radio.favourites.value
        favourites.indexOfFirst { matches(it.name, core2) || matches(it.name, core) }.takeIf { it >= 0 }?.let { index ->
            radio.resolveForPlay(favourites[index])
            val songs = radio.prepare(favourites)
            return@runCatching MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, index, C.TIME_UNSET)
        }
        if (wantsRadio && core2.isNotEmpty()) {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) { radio.search(query.replace(Regex("(?i)\\b(play|radio|station|on wavelength)\\b"), "").trim()) }
                ?.firstOrNull()?.let { station ->
                    radio.resolveForPlay(station)
                    return@runCatching MediaItemsWithStartPosition(radio.prepare(listOf(station)).map { it.toMediaItem() }, 0, C.TIME_UNSET)
                }
        }
        // Followed podcast: newest episode first, older ones after
        podcastFeeds.followed.value.firstOrNull { matches(it.title, core2) || matches(it.title, core) }?.let { show ->
            val feed = withTimeoutOrNull(NETWORK_TIMEOUT_MS * 2) { podcastFeeds.feed(show) } ?: return@let
            val songs = podcastFeeds.prepare(feed.copy(episodes = feed.episodes.take(10)))
            if (songs.isNotEmpty()) return@runCatching MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, C.TIME_UNSET)
        }
        // Audiobook on the phone
        audiobooks.books.value.firstOrNull { matches(it.title, core2) || matches(it.title, core) }?.let { book ->
            playAudiobook(listOf(AUDIOBOOKS, "device", book.id))?.let { return@runCatching it }
        }
        // Navidrome: an exact album or song name, or anything when they asked for the server
        if (subsonic.session.value != null) {
            val text = query.replace(Regex("(?i)\\b(play|on wavelength|from navidrome|on navidrome|navidrome|from my server|my server)\\b"), "").trim()
            val result = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { subsonic.search(text) }
            if (result != null) {
                result.albums.firstOrNull { matches(it.name, core2) && normalize(it.name) == core2 }?.let { album ->
                    val songs = subsonic.prepare(subsonic.albumSongs(album.id))
                    if (songs.isNotEmpty()) return@runCatching MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, 0, C.TIME_UNSET)
                }
                val exactSong = result.songs.indexOfFirst { normalize(it.title) == core2 }
                if ((exactSong >= 0 || wantsServer) && result.songs.isNotEmpty()) {
                    val songs = subsonic.prepare(result.songs)
                    return@runCatching MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, exactSong.coerceAtLeast(0), C.TIME_UNSET)
                }
            }
        }
        null
    }.onFailure { Timber.tag(TAG).w(it, "Voice request failed") }.getOrNull()

    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    /** A spoken name matches a title when one contains the other (ignoring spaces and punctuation). */
    private fun matches(title: String, spoken: String): Boolean {
        if (spoken.length < 3) return false
        val name = normalize(title)
        return name == spoken || (spoken.length >= 4 && name.contains(spoken)) || (name.length >= 4 && spoken.contains(name))
    }

    private suspend fun allowedLocalSongs(): List<Song> {
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

    private fun mediaServerRoot(): List<MediaItem> {
        if (mediaServer.session.value == null) return emptyList()
        return listOfNotNull(
            if (mediaServer.hasFavourites) {
                folder(MS_FAVOURITES, context.getString(R.string.media_server_favourites), null, icon(R.drawable.favorite), MediaMetadata.MEDIA_TYPE_PLAYLIST)
            } else {
                null
            },
            folder(MS_PLAYLISTS, context.getString(R.string.playlists), null, icon(R.drawable.queue_music), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            folder(MS_ALBUMS, context.getString(R.string.albums), null, icon(R.drawable.album), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            folder(MS_ARTISTS, context.getString(R.string.artists), null, icon(R.drawable.artist), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
        )
    }

    private suspend fun msSongs(load: suspend () -> List<com.metrolist.music.mediaserver.MsSong>): List<Song> =
        serverCall { mediaServer.prepare(load()) }.orEmpty()

    private suspend fun msSongsWithShuffle(
        parent: String,
        load: suspend () -> List<com.metrolist.music.mediaserver.MsSong>,
    ): List<MediaItem> {
        val songs = msSongs(load)
        return if (songs.isEmpty()) emptyList() else listOf(shuffleItem(parent)) + songs.map { it.toAutoItem(parent) }
    }

    /** Paths: media_server/favourites/<song>, media_server/album/<album>/<song>, media_server/playlist/<playlist>/<song>. */
    private suspend fun playMediaServer(path: List<String>): MediaItemsWithStartPosition? {
        val (songs, chosen) = when (path.getOrNull(1)) {
            "favourites" -> msSongs { mediaServer.favourites() } to path.getOrNull(2)
            "album" -> msSongs { mediaServer.albumSongs(path.getOrNull(2).orEmpty(), null) } to path.getOrNull(3)
            "playlist" -> msSongs { mediaServer.playlistSongs(path.getOrNull(2).orEmpty(), null) } to path.getOrNull(3)
            else -> return null
        }
        if (songs.isEmpty()) return null
        if (chosen == SHUFFLE) return MediaItemsWithStartPosition(songs.shuffled().map { it.toMediaItem() }, 0, C.TIME_UNSET)
        val start = songs.indexOfFirst { it.id == chosen }.coerceAtLeast(0)
        return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, start, C.TIME_UNSET)
    }

    private fun subsonicRoot(): List<MediaItem> {
        if (subsonic.session.value == null) return emptyList()
        return listOf(
            folder(SUB_STARRED, context.getString(R.string.subsonic_starred), null, icon(R.drawable.favorite), MediaMetadata.MEDIA_TYPE_PLAYLIST),
            folder(SUB_PLAYLISTS, context.getString(R.string.playlists), null, icon(R.drawable.queue_music), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            folder(SUB_ALBUMS, context.getString(R.string.subsonic_recent_albums), null, icon(R.drawable.album), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            folder(SUB_FOLDERS, context.getString(R.string.subsonic_folders), null, icon(R.drawable.folder), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
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
            "dir" -> serverSongs {
                subsonic.treeNode(treePath(path.getOrNull(2).orEmpty()))?.songs.orEmpty()
            } to path.getOrNull(3)
            "folders" -> serverSongs { subsonic.treeNode(emptyList())?.songs.orEmpty() } to path.getOrNull(2)
            else -> return null
        }
        if (songs.isEmpty()) return null
        if (chosen == SHUFFLE) return MediaItemsWithStartPosition(songs.shuffled().map { it.toMediaItem() }, 0, C.TIME_UNSET)
        val start = songs.indexOfFirst { it.id == chosen }.coerceAtLeast(0)
        return MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, start, C.TIME_UNSET)
    }

    // Same order as the News tab's sort
    /** A server folder for Android Auto: sub-folders to open, then Shuffle and its songs. */
    /** Folder ids carry the whole folder path, encoded so it stays one part of the id. */
    private fun treePath(encoded: String): List<String> =
        android.net.Uri.decode(encoded).split('/').filter { it.isNotEmpty() }

    /** One real server folder: its sub-folders (with item counts), then shuffle and its songs. */
    private suspend fun treeItems(parentId: String, path: List<String>): List<MediaItem> {
        val node = serverCall { subsonic.treeNode(path) } ?: return emptyList()
        val folders = node.folders.values.map { child ->
            folder(
                "$SUB_DIR/${android.net.Uri.encode((child.path).joinToString("/"))}",
                child.name,
                context.resources.getQuantityString(R.plurals.subsonic_folder_items, child.itemCount, child.itemCount),
                icon(R.drawable.folder),
                MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            )
        }
        val songs = node.songs.takeIf { it.isNotEmpty() }?.let { serverCall { subsonic.prepare(it) } }.orEmpty()
        return folders + (if (songs.isEmpty()) emptyList() else listOf(shuffleItem(parentId))) + songs.map { it.toAutoItem(parentId) }
    }

    private suspend fun enabledNewsSources() = news.sortedEnabledSources()

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
        val newEpisodes = folder(NEW_EPISODES, context.getString(R.string.new_episodes), null, icon(R.drawable.podcasts))
        val saved = folder(SAVED_EPISODES, context.getString(R.string.auto_saved_episodes), null, icon(R.drawable.mic))
        val subscribed = database.subscribedPodcasts().first()
        val followed = podcastFeeds.followed.value
        val channels = subscribed.map { podcast ->
            folder("$PODCAST_CHANNEL/${podcast.id}", podcast.title, podcast.author, podcast.thumbnailUrl?.toUri())
        }
        val feeds = followed.map { show ->
            folder("$PODCAST_FEED/${feedKey(show.feedUrl)}", show.title, show.author, show.artworkUrl?.toUri())
        }
        // Your playlists marked as podcasts (Settings → Features → Podcasts) come after your shows
        val markedIds = context.dataStore.data.first().podcastPlaylistIds()
        val allPlaylists = database.playlistsByCreateDateAsc().first()
        val marked = markedIds.mapNotNull { id -> allPlaylists.firstOrNull { it.id == id } }.map { playlist ->
            folder(
                "${MusicService.PLAYLIST}/${playlist.id}",
                playlist.playlist.name,
                context.resources.getQuantityString(R.plurals.n_song, playlist.songCount, playlist.songCount),
                playlist.thumbnails.firstOrNull()?.toUri(),
                MediaMetadata.MEDIA_TYPE_PLAYLIST,
            )
        }
        return listOf(newEpisodes, saved) + com.metrolist.music.constants.sortPodcastFolders(context, channels, feeds, subscribed, followed) + marked
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
        "new" -> {
            val items = newEpisodes(refresh = false)
            if (items.isEmpty()) null else MediaItemsWithStartPosition(items, (path.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, items.lastIndex), C.TIME_UNSET)
        }
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
        const val MEDIA_SERVER = "media_server"
        private const val MS_FAVOURITES = "$MEDIA_SERVER/favourites"
        private const val MS_PLAYLISTS = "$MEDIA_SERVER/playlists"
        private const val MS_ALBUMS = "$MEDIA_SERVER/albums"
        private const val MS_ARTISTS = "$MEDIA_SERVER/artists"
        private const val MS_ALBUM = "$MEDIA_SERVER/album"
        private const val MS_PLAYLIST = "$MEDIA_SERVER/playlist"
        private const val MS_ARTIST = "$MEDIA_SERVER/artist"
        private const val SUB_STARRED = "$SUBSONIC/starred"
        private const val SUB_PLAYLISTS = "$SUBSONIC/playlists"
        private const val SUB_ALBUMS = "$SUBSONIC/albums"
        private const val SUB_ALBUM = "$SUBSONIC/album"
        private const val SUB_PLAYLIST = "$SUBSONIC/playlist"
        private const val SUB_FOLDERS = "$SUBSONIC/folders"
        private const val SUB_DIR = "$SUBSONIC/dir"
        private const val ABS_ALL = "$AUDIOBOOKS/abs_all"
        private const val SAVED_EPISODES = "$PODCASTS/saved"
        private const val PODCAST_CHANNEL = "$PODCASTS/channel"
        private const val PODCAST_FEED = "$PODCASTS/feed"
        private const val SHUFFLE = "__shuffle__"
        private const val LOCAL_FOLDER = "$LOCAL_FILES/folder"
        const val LIB_ALBUM = "lib_album"
        const val LIB_ARTIST = "lib_artist"
        private const val NEW_EPISODES = "$PODCASTS/new"
        const val CONTINUE = "continue"
        private const val NEW_EPISODES_TTL_MS = 5 * 60 * 1000L
    }
}

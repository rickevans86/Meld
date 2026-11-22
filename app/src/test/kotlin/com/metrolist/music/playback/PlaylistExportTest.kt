package com.metrolist.music.playback

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.datastore.preferences.core.edit
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.DownloadExportFolderKey
import com.metrolist.music.constants.DownloadPlaylistFoldersKey
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.ui.component.offerPlaylistCleanup
import com.metrolist.music.ui.component.exportCleanupFolders
import com.metrolist.music.ui.component.removeExportFolders
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaylistExportTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var db: InternalDatabase
    private lateinit var cache: SimpleCache
    private lateinit var provider: ExportTestDocumentsProvider
    private lateinit var exporter: DownloadExporter
    private val tree = DocumentsContract.buildTreeDocumentUri("test.exports", "root")
    private val bytes = byteArrayOf(0, 0, 0, 20, 102, 116, 121, 112, 77, 52, 65, 32, 1, 2, 3, 4)

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Application>()
        context.dataStore.edit { it.clear(); it[DownloadExportFolderKey] = tree.toString(); it[DownloadPlaylistFoldersKey] = true }
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
        db.dao.insert(SongEntity(id = "song", title = "Shared song", duration = 20))
        for (id in listOf("A", "B", "Unrequested")) {
            db.dao.insert(PlaylistEntity(id = id, name = id))
            db.dao.insert(PlaylistSongMap(playlistId = id, songId = "song", position = 0))
        }
        cache = SimpleCache(temp.newFolder("cache"), NoOpCacheEvictor(), StandaloneDatabaseProvider(context))
        val hole = cache.startReadWrite("song", 0, bytes.size.toLong())
        val file = cache.startFile("song", 0, bytes.size.toLong()); file.writeBytes(bytes)
        cache.commitFile(file, bytes.size.toLong()); cache.releaseHoleSpan(hole)
        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(metadata, bytes.size.toLong())
        cache.applyContentMetadataMutations("song", metadata)
        provider = ExportTestDocumentsProvider().apply { root = temp.newFolder("export") }
        provider.attachInfo(context, ProviderInfo().apply {
            authority = "test.exports"; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = readPermission
        })
        ShadowContentResolver.registerProviderInternal("test.exports", provider)
        exporter = DownloadExporter(context, MusicDatabase(db), cache)
    }
    @After fun teardown() { cache.release(); db.close() }

    @Test fun `only requested playlist is created and later playlist reuses exported bytes without cache or network`() = runBlocking {
        exporter.request("song", "A")
        assertTrue(exporter.export("song"))
        assertEquals(listOf("A"), provider.root.list()!!.toList())
        assertArrayEquals(bytes, File(provider.root, "A/Shared song.m4a").readBytes())
        assertTrue(exporter.requestedFolders("song").isEmpty())
        cache.removeResource("song") // Only the public copy remains; no upstream DataSource exists.
        exporter.request("song", "B")
        assertTrue(exporter.export("song"))
        assertEquals(setOf("A", "B"), provider.root.list()!!.toSet())
        assertArrayEquals(bytes, File(provider.root, "B/Shared song.m4a").readBytes())
        File(provider.root, "A").deleteRecursively()
        exporter.request("song", "B")
        assertTrue(exporter.export("song"))
        assertFalse(File(provider.root, "A").exists())
        assertEquals(1, File(provider.root, "B").list()!!.size)
    }

    @Test fun `queued requests survive exporter recreation and unrequested membership never creates folders`() = runBlocking {
        exporter.request("song", "A")
        exporter.request("song", "B")
        val recreated = DownloadExporter(context, MusicDatabase(db), cache)
        assertEquals(setOf("A", "B"), recreated.requestedFolders("song").toSet())
        assertTrue(recreated.export("song"))
        assertFalse(File(provider.root, "Unrequested").exists())
        assertTrue(recreated.requestedFolders("song").isEmpty())
    }

    @Test fun `a playlist requested during export is drained without resurrecting completed requests`() = runBlocking {
        exporter.request("song", "A")
        provider.onWrite = {
            provider.onWrite = null
            runBlocking { exporter.request("song", "B") }
        }
        assertTrue(exporter.export("song"))
        assertEquals(setOf("A", "B"), provider.root.list()!!.toSet())
        assertTrue(exporter.requestedFolders("song").isEmpty())
    }

    @Test fun `failed copy is removed and can be retried without completing pending request`() = runBlocking {
        exporter.request("song", "A")
        provider.failWrites = true
        assertFalse(exporter.export("song"))
        assertEquals(listOf("A"), exporter.requestedFolders("song"))
        assertEquals(0, File(provider.root, "A").list()!!.size)
        provider.failWrites = false
        assertTrue(exporter.export("song"))
    }

    @Test fun `flat mode writes only root and completes named request`() = runBlocking {
        context.dataStore.edit { it[DownloadPlaylistFoldersKey] = false }
        exporter.request("song", "A")
        assertTrue(exporter.export("song"))
        assertEquals(listOf("Shared song.m4a"), provider.root.list()!!.toList())
        assertTrue(exporter.requestedFolders("song").isEmpty())
    }

    private fun saveTrack(id: String, title: String, episode: Boolean = false) {
        db.dao.insert(SongEntity(id = id, title = title, duration = 20, isEpisode = episode))
        val hole = cache.startReadWrite(id, 0, bytes.size.toLong())
        val file = cache.startFile(id, 0, bytes.size.toLong()); file.writeBytes(bytes)
        cache.commitFile(file, bytes.size.toLong()); cache.releaseHoleSpan(hole)
        val length = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(length, bytes.size.toLong())
        cache.applyContentMetadataMutations(id, length)
    }

    @Test fun `unassigned music goes to No Playlist and an explicit playlist still wins`() = runBlocking {
        saveTrack("unassigned", "Alone")
        exporter.request("unassigned", null)
        assertTrue(exporter.export("unassigned"))
        assertArrayEquals(bytes, File(provider.root, "No Playlist/Alone.m4a").readBytes())
        exporter.request("unassigned", "Chosen")
        assertTrue(exporter.export("unassigned"))
        assertArrayEquals(bytes, File(provider.root, "Chosen/Alone.m4a").readBytes())
    }

    @Test fun `unscoped music with playlist membership keeps root rather than inventing a playlist`() = runBlocking {
        exporter.request("song", null)
        assertTrue(exporter.export("song"))
        assertTrue(File(provider.root, "Shared song.m4a").exists())
        assertFalse(File(provider.root, "No Playlist").exists())
    }

    @Test fun `feed podcast download remains cached and is excluded from music exports`() = runBlocking {
        saveTrack("podcast:episode", "Feed episode", episode = true)
        exporter.request("podcast:episode", "Not music")
        assertTrue(exporter.export("podcast:episode"))
        assertEquals(0, exporter.exportAll(listOf("podcast:episode")))
        assertTrue(cache.isCached("podcast:episode", 0, bytes.size.toLong()))
        assertTrue(provider.root.list()!!.isEmpty())
    }

    @Test fun `first download from marked YouTube podcast stays private without cached remote membership`() = runBlocking {
        saveTrack("yt-episode", "YouTube episode")
        context.dataStore.edit { it[com.metrolist.music.constants.PodcastPlaylistsKey] = "yt:VLshow-list\tShow\t" }
        exporter.request("yt-episode", "YouTube - Show", sourcePlaylistId = "youtube:show-list")
        assertTrue(exporter.export("yt-episode"))
        val restarted = DownloadExporter(context, MusicDatabase(db), cache)
        assertEquals(0, restarted.exportAll(listOf("yt-episode")))
        assertTrue(cache.isCached("yt-episode", 0, bytes.size.toLong()))
        assertTrue(provider.root.list()!!.isEmpty())
    }

    @Test fun `a marked local podcast playlist also prevents export from a music playlist`() = runBlocking {
        context.dataStore.edit { it[com.metrolist.music.constants.PodcastPlaylistsKey] = "A" }
        exporter.request("song", "B", sourcePlaylistId = "B")
        assertTrue(exporter.export("song"))
        assertTrue(provider.root.list()!!.isEmpty())
    }

    @Test fun `cleanup is next release only reviewed selection only and never deletes root files`() = runBlocking {
        assertTrue(offerPlaylistCleanup(280112, false, true))
        assertFalse(offerPlaylistCleanup(280111, false, true))
        assertFalse(offerPlaylistCleanup(280113, false, true))
        assertFalse(offerPlaylistCleanup(280112, true, true))
        assertFalse(offerPlaylistCleanup(280112, false, false))
        File(provider.root, "Keep").mkdir(); File(provider.root, "Remove").mkdir()
        File(provider.root, "Keep/song.m4a").writeBytes(bytes)
        File(provider.root, "Remove/song.m4a").writeBytes(bytes)
        File(provider.root, "loose.m4a").writeBytes(bytes)
        assertEquals(2, exportCleanupFolders(context, tree).size)
        assertTrue(removeExportFolders(context, tree, emptySet()).isEmpty())
        provider.failDeletes = true
        assertTrue(removeExportFolders(context, tree, setOf("root/Remove")).isEmpty())
        assertTrue(File(provider.root, "Remove/song.m4a").exists())
        provider.failDeletes = false
        assertEquals(setOf("root/Remove"), removeExportFolders(context, tree, setOf("root/Remove", "root", "root/Keep/song.m4a")))
        assertTrue(File(provider.root, "Keep/song.m4a").exists())
        assertTrue(File(provider.root, "loose.m4a").exists())
        assertFalse(File(provider.root, "Remove").exists())
    }
}

class ExportTestDocumentsProvider : ContentProvider() {
    lateinit var root: File
    var failWrites = false
    var failDeletes = false
    var onWrite: (() -> Unit)? = null
    private fun file(id: String) = if (id == "root") root else File(root, id.removePrefix("root/"))
    override fun onCreate() = true
    override fun getType(uri: Uri): String = "audio/mp4"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val id = DocumentsContract.getDocumentId(uri)
        return if (uri.pathSegments.last() == "children") queryChildDocuments(id, projection, sortOrder) else queryDocument(id, projection)
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val parent = extras!!.getParcelable<Uri>("uri")!!
        val id = DocumentsContract.getDocumentId(parent)
        return when (method) {
            "android:createDocument" -> Bundle().apply {
                val created = createDocument(id, extras.getString("mime_type")!!, extras.getString("_display_name")!!)
                putParcelable("uri", DocumentsContract.buildDocumentUri("test.exports", created))
            }
            "android:deleteDocument" -> Bundle().also { deleteDocument(id) }
            else -> throw UnsupportedOperationException(method)
        }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = openDocument(DocumentsContract.getDocumentId(uri), mode, null)
    fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = rows(listOf(documentId))
    fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        rows(file(parentDocumentId).listFiles().orEmpty().map { "$parentDocumentId/${it.name}" })
    private fun rows(ids: List<String>) = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)).apply {
        ids.forEach { id -> val f = file(id); addRow(arrayOf(id, f.name,
            if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "audio/mp4")) }
    }
    fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val id = "$parentDocumentId/$displayName"; val target = file(id)
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) target.mkdir() else target.createNewFile()
        return id
    }
    fun deleteDocument(documentId: String) {
        if (failDeletes) throw java.io.IOException("Test deletion failure")
        file(documentId).deleteRecursively()
    }
    fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")
    fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode.contains('w')) onWrite?.invoke()
        if (failWrites && mode.contains('w')) throw java.io.IOException("Test write failure")
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    }
}

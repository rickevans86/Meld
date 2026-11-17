package com.metrolist.music.audiobooks.abs

import android.app.Application
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AbsOfflineTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var context: android.content.Context
    @Volatile private var progressResponse: String? = null
    private lateinit var db: MusicDatabase
    private lateinit var repository: AbsRepository
    private lateinit var server: HttpServer
    private val requests = AtomicInteger()
    private val json = Json { ignoreUnknownKeys = true }
    private val book = AbsBook("offline-book", "Downloaded book", "Author", null, emptyList(), null, emptyList(), 100.0,
        listOf(AbsTrack(0, "one.wav", 0.0, 50.0, "/audio/one"), AbsTrack(1, "two.wav", 50.0, 50.0, "/audio/two")),
        listOf(AbsChapter("First", 0.0, 50.0), AbsChapter("Second", 50.0, 100.0)))
    private lateinit var audioDir: File

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("audiobookshelf_auth", 0).edit().clear().commit()
        context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(folder.root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(folder.root, "cache").apply { mkdirs() }
            @Suppress("DEPRECATION") override fun getResources(): android.content.res.Resources =
                object : android.content.res.Resources(app.resources.assets, app.resources.displayMetrics, app.resources.configuration) {
                    override fun getString(id: Int) = "Downloaded audio is missing. Download this book again when online."
                }
        }
        val internal = Room.inMemoryDatabaseBuilder(app, InternalDatabase::class.java).allowMainThreadQueries().build()
        db = MusicDatabase(internal)
        audioDir = File(context.filesDir, "audiobookshelf/${book.id}").apply { mkdirs() }
        File(audioDir, "book.json").writeText(json.encodeToString(AbsBook.serializer(), book))
        File(audioDir, ".complete").writeText("done")
        File(audioDir, "0.wav").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(audioDir, "1.wav").writeBytes(byteArrayOf(5, 6, 7, 8))
        File(context.filesDir, "audiobookshelf_positions.json").writeText(
            """{"offline-book":{"currentTimeSec":72.0,"updatedAt":2000,"finished":false}}""")
        repository = AbsRepository(context, db)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests.incrementAndGet()
            val body = when {
                exchange.requestURI.path == "/api/me" -> """{"username":"Reader"}"""
                exchange.requestURI.path.startsWith("/api/me/progress/") -> progressResponse
                else -> null
            }
            val status = if (body != null) 200 else 503
            val bytes = (body ?: "unavailable").toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After fun teardown() { server.stop(0); db.close() }

    @Test fun `download metadata and prepare use local files with an unavailable signed-in server`() = runBlocking {
        repository.signInWithApiKey("http://127.0.0.1:${server.address.port}", "test-key")
        val before = requests.get()
        val loaded = withTimeout(2_000) { repository.book(book.id) }
        val prepared = withTimeout(15_000) { repository.prepare(book.id, null) }
        assertEquals(book, loaded)
        assertEquals(1, prepared.startIndex)
        assertEquals(22_000L, prepared.songs[1].song.playbackPosition)
        assertTrue(prepared.songs.all { it.song.localPath!!.startsWith("file:") })
        assertEquals(before, requests.get())
        assertNull(repository.coverUrl(book.id))
        assertEquals(72.0, repository.localProgress().single().currentTimeSec, 0.0)
    }

    @Test fun `download playback does not require a valid session or cached cover`() = runBlocking {
        assertNull(repository.session.value)
        val prepared = withTimeout(15_000) { repository.prepare(book.id, 0.0) }
        assertEquals(0, prepared.startIndex)
        assertEquals(0, requests.get())
        assertNotNull(repository.offlineUri("audiobook:abs:${book.id}:0"))
        assertNull(prepared.songs[0].thumbnailUrl)
    }

    @Test fun `missing audio in a completed download fails clearly without falling back to a stream`() = runBlocking {
        File(audioDir, "1.wav").delete()
        try {
            repository.prepare(book.id, null)
            fail("Missing file should not silently become a server stream")
        } catch (e: java.io.IOException) { assertTrue(e.message!!.contains("missing")) }
        assertEquals(0, requests.get())
    }

    @Test fun `missing download metadata fails locally rather than waiting for a server`() = runBlocking {
        File(audioDir, "book.json").delete()
        try { repository.book(book.id); fail("Missing metadata should fail locally") }
        catch (e: java.io.IOException) { assertTrue(e.message!!.contains("missing")) }
        assertEquals(0, requests.get())
    }

    @Test fun `new remote positions persist for offline resume and older remote positions cannot reset the phone`() = runBlocking {
        repository.signInWithApiKey("http://127.0.0.1:${server.address.port}", "test-key")
        progressResponse = """{"libraryItemId":"offline-book","currentTime":10,"duration":100,"lastUpdate":1000}"""
        repository.progress(book.id)
        assertEquals(72.0, repository.localProgress().single().currentTimeSec, 0.0)
        progressResponse = """{"libraryItemId":"offline-book","currentTime":85,"duration":100,"lastUpdate":3000}"""
        repository.progress(book.id)
        val before = requests.get()
        val reopened = AbsRepository(context, db)
        val prepared = withTimeout(15_000) { reopened.prepare(book.id, null) }
        assertEquals(35_000L, prepared.songs[1].song.playbackPosition)
        assertEquals(before, requests.get())
    }

    @Test fun `finished local books restart from the beginning offline`() = runBlocking {
        repository.book(book.id)
        File(context.filesDir, "audiobookshelf_positions.json").writeText(
            """{"offline-book":{"currentTimeSec":100.0,"updatedAt":4000,"finished":true}}""")
        val reopened = AbsRepository(context, db)
        val prepared = withTimeout(15_000) { reopened.prepare(book.id, null) }
        assertEquals(0, prepared.startIndex)
        assertTrue(reopened.localProgress().single().isFinished)
        assertEquals(0, requests.get())
    }

    @Test fun `an incomplete download is not offered as an offline book`() = runBlocking {
        File(audioDir, ".complete").delete()
        assertFalse(repository.isDownloaded(book.id))
        assertTrue(repository.downloadedBooks().isEmpty())
        try { repository.book(book.id); fail("Incomplete metadata is not a downloaded book") }
        catch (_: IllegalStateException) { }
        assertEquals(0, requests.get())
    }
}

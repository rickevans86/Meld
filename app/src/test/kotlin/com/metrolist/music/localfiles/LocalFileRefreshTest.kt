package com.metrolist.music.localfiles

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LocalFileRefreshTest {
    @Test fun `refresh removes stale index rows but keeps readable files and permission failures`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = File(context.cacheDir, "local-refresh.mp3").apply { writeText("audio") }
        val provider = object : ContentProvider() {
            var deleted = false
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?) =
                MatrixCursor(projection).apply {
                    addRow(arrayOf(1L, file.absolutePath, 1, file.name))
                    addRow(arrayOf(2L, File(context.cacheDir, "gone.mp3").absolutePath, 1, "gone.mp3"))
                }
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                if (uri.lastPathSegment == "denied") throw SecurityException()
                if (deleted || uri.lastPathSegment == "2") throw FileNotFoundException()
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            override fun getType(uri: Uri) = "audio/mpeg"
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        }
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
        val folders = LocalMusicFolders(context)
        assertEquals(listOf(1L), folders.refreshIndex().map { it.mediaStoreId })
        assertTrue(localAudioStillExists(context, Uri.parse("content://media/denied")))
        provider.deleted = true
        assertTrue(folders.refreshIndex().isEmpty())
        assertTrue(file.exists()) // Refresh never deletes physical files.
    }
}

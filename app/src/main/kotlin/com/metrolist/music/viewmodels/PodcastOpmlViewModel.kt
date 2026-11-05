/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.R
import com.metrolist.music.podcasts.PodcastDirectory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Backup and import for Wavelength's own lists, each in its own section of Backup and restore:
 * podcasts (OPML), radio stations (M3U / PLS) and news sources (OPML). Imports skip what you already have.
 */
@HiltViewModel
class PodcastOpmlViewModel
@Inject
constructor(
    private val directory: PodcastDirectory,
    private val radio: com.metrolist.music.radio.RadioDirectory,
    private val news: com.metrolist.music.podcasts.NewsBriefing,
) : ViewModel() {
    fun exportRadio(context: Context, uri: Uri) = write(context, uri, R.string.radio_backup_failed) {
        val count = radio.favourites.value.size
        radio.exportM3u() to it.resources.getQuantityString(R.plurals.radio_backup_saved, count, count)
    }

    fun importRadio(context: Context, uri: Uri) = read(context, uri, R.string.radio_import_failed) { app, stream ->
        val result = radio.importPlaylist(stream.bufferedReader().readText())
        app.getString(R.string.radio_imported, result.added, result.skipped)
    }

    fun exportNews(context: Context, uri: Uri) = write(context, uri, R.string.news_backup_failed) { app ->
        val text = news.exportOpml()
        val count = Regex("wavelengthKey=").findAll(text).count()
        text to app.resources.getQuantityString(R.plurals.news_backup_saved, count, count)
    }

    fun importNews(context: Context, uri: Uri) = read(context, uri, R.string.news_import_failed) { app, stream ->
        val result = news.importOpml(stream)
        app.getString(R.string.news_imported, result.added, result.skipped)
    }

    private fun write(context: Context, uri: Uri, failed: Int, build: suspend (Context) -> Pair<String, String>) {
        val app = context.applicationContext
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val (text, done) = build(app)
                    app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("no file")
                    done
                }.onFailure { Timber.w(it, "Backup failed") }.getOrNull()
            } ?: app.getString(failed)
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun read(context: Context, uri: Uri, failed: Int, import: suspend (Context, java.io.InputStream) -> String) {
        val app = context.applicationContext
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { import(app, it) } }
                    .onFailure { Timber.w(it, "Import failed") }.getOrNull()
            } ?: app.getString(failed)
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    val followedCount get() = directory.followed.value.size

    fun export(context: Context, uri: Uri) {
        val app = context.applicationContext
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri)?.use { it.write(directory.exportOpml().toByteArray()) } != null
                }.onFailure { Timber.w(it, "OPML export failed") }.getOrDefault(false)
            }
            val message = if (ok) {
                app.resources.getQuantityString(R.plurals.podcast_opml_exported, directory.followed.value.size, directory.followed.value.size)
            } else {
                app.getString(R.string.podcast_opml_export_failed)
            }
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }
    }

    fun import(context: Context, uri: Uri) {
        val app = context.applicationContext
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { directory.importOpml(it) } }
                    .onFailure { Timber.w(it, "OPML import failed") }.getOrNull()
            }
            if (result == null) {
                Toast.makeText(app, app.getString(R.string.podcast_opml_import_failed), Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(
                app,
                app.getString(R.string.podcast_opml_imported, result.added.size, result.skipped),
                Toast.LENGTH_LONG,
            ).show()
            // Artwork and publisher aren't in OPML files, so fetch them from each new feed
            // Runs outside this screen so it finishes even if you leave Backup and restore
            if (result.added.isNotEmpty()) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
                    directory.fillDetails(result.added)
                }
            }
        }
    }
}

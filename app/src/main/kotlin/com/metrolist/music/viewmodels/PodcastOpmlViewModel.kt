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

/** Backs up followed podcasts to an OPML file and follows the podcasts in one, skipping ones already followed. */
@HiltViewModel
class PodcastOpmlViewModel
@Inject
constructor(
    private val directory: PodcastDirectory,
) : ViewModel() {
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

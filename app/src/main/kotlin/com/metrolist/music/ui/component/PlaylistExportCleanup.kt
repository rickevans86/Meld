package com.metrolist.music.ui.component

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.BuildConfig
import com.metrolist.music.R
import com.metrolist.music.constants.DownloadExportFolderKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Offer only in the next signed release, and at most once per installation. This is not an app version bump.
internal const val PLAYLIST_CLEANUP_RELEASE = 280112
internal fun offerPlaylistCleanup(version: Int, reviewed: Boolean, configured: Boolean) =
    version == PLAYLIST_CLEANUP_RELEASE && !reviewed && configured

internal data class ExportCleanupFolder(val id: String, val name: String)
internal fun exportCleanupFolders(context: Context, tree: Uri): List<ExportCleanupFolder> = buildList {
    context.contentResolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null,
    )?.use { rows ->
        while (rows.moveToNext()) {
            if (rows.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                add(ExportCleanupFolder(rows.getString(0), rows.getString(1)))
            }
        }
    }
}.sortedBy { it.name.lowercase() }

/** Deletes only explicitly selected direct children, after rechecking the export root. */
internal fun removeExportFolders(context: Context, tree: Uri, selected: Set<String>): Set<String> {
    val removed = mutableSetOf<String>()
    exportCleanupFolders(context, tree).filter { it.id in selected }.forEach { folder ->
        runCatching {
            DocumentsContract.deleteDocument(context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, folder.id))
        }.onSuccess { if (it) removed += folder.id }
    }
    return removed
}

@Composable
fun PlaylistExportCleanup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reviewedKey = remember { booleanPreferencesKey("playlistExportCleanupReviewed") }
    var tree by remember { mutableStateOf<Uri?>(null) }
    var folders by remember { mutableStateOf<List<ExportCleanupFolder>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun finish() {
        folders = emptyList()
        scope.launch { context.dataStore.edit { it[reviewedKey] = true } }
    }
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        val raw = prefs[DownloadExportFolderKey]
        if (BuildConfig.VERSION_CODE == PLAYLIST_CLEANUP_RELEASE && prefs[reviewedKey] != true && raw.isNullOrBlank()) {
            finish()
            return@LaunchedEffect
        }
        if (offerPlaylistCleanup(BuildConfig.VERSION_CODE, prefs[reviewedKey] == true, !raw.isNullOrBlank())) {
            val uri = raw!!.toUri()
            val found = withContext(Dispatchers.IO) { runCatching { exportCleanupFolders(context, uri) }.getOrNull() }
            if (found != null) {
                tree = uri
                folders = found
                if (found.isEmpty()) finish()
            }
        }
    }
    if (folders.isEmpty()) return
    AlertDialog(
        onDismissRequest = { if (!busy) { if (confirming) confirming = false else finish() } },
        title = { Text(stringResource(if (confirming) R.string.export_cleanup_confirm_title else R.string.export_cleanup_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(if (confirming) R.string.export_cleanup_confirm_desc else R.string.export_cleanup_desc)) }
                if (error) item { Text(stringResource(R.string.export_cleanup_failed), color = MaterialTheme.colorScheme.error) }
                items(if (confirming) folders.filter { it.id in selected } else folders, key = { it.id }) { folder ->
                    if (confirming) {
                        Text("• ${folder.name}")
                    } else {
                        Row(Modifier.fillMaxWidth().toggleable(value = folder.id in selected,
                            enabled = !busy, role = Role.Checkbox, onValueChange = { checked ->
                                selected = if (checked) selected + folder.id else selected - folder.id
                            })) {
                            Checkbox(checked = folder.id in selected, onCheckedChange = null)
                            Text(folder.name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty() && !busy, onClick = {
                if (!confirming) confirming = true else {
                    busy = true
                    scope.launch {
                        val root = tree!!
                        val removed = withContext(Dispatchers.IO) {
                            runCatching { removeExportFolders(context, root, selected) }.getOrDefault(emptySet())
                        }
                        val removedNames = folders.filter { it.id in removed }.map { it.name }.toSet()
                        context.dataStore.edit { prefs ->
                            prefs.asMap().keys.filter { it.name.startsWith("downloadExportTargets:") }.forEach { key ->
                                val typed = stringSetPreferencesKey(key.name)
                                prefs[typed] = prefs[typed].orEmpty() - removedNames
                            }
                        }
                        if (removed.containsAll(selected)) finish() else {
                            folders = folders.filterNot { it.id in removed }
                            selected = selected - removed
                            error = true
                            confirming = false
                        }
                        busy = false
                    }
                }
            }) { Text(stringResource(if (confirming) R.string.export_cleanup_remove else R.string.export_cleanup_review)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { if (confirming) confirming = false else finish() }) {
                Text(stringResource(if (confirming) android.R.string.cancel else R.string.export_cleanup_keep))
            }
        },
    )
}

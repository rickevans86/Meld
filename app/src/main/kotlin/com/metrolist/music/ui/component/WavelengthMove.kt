/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.component

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.utils.rememberPreference

/**
 * Wavelength moved from the app ID com.meld.app.debug (shared with Meld's own developer builds,
 * which made Play Protect warn on install) to its own ID. The old app tells you to back up before
 * the next update; the new app, while the old one is installed, walks you through restoring and
 * removing it.
 */
object WavelengthMove {
    const val OLD_APP_ID = "com.meld.app.debug"
    const val BACKUP_ROUTE = "settings/backup_restore"
    private val OldNoticeSeenKey = booleanPreferencesKey("wavelengthMoveOldNoticeSeen")
    private val NewGuideDoneKey = booleanPreferencesKey("wavelengthMoveNewGuideDone")

    fun isOldApp(context: Context) = context.packageName == OLD_APP_ID

    fun oldAppInstalled(context: Context): Boolean =
        context.packageName != OLD_APP_ID && runCatching {
            context.packageManager.getPackageInfo(OLD_APP_ID, 0)
        }.isSuccess

    @Composable
    fun Dialog(onOpenBackup: () -> Unit) {
        val context = LocalContext.current
        if (isOldApp(context)) OldAppNotice(onOpenBackup) else NewAppGuide(onOpenBackup)
    }

    /** Shown once in the old app. */
    @Composable
    private fun OldAppNotice(onOpenBackup: () -> Unit) {
        var seen by rememberPreference(OldNoticeSeenKey, false)
        if (seen) return
        AlertDialog(
            onDismissRequest = { seen = true },
            title = { Text(stringResource(R.string.wavelength_move_title)) },
            text = { Text(stringResource(R.string.wavelength_move_old_text)) },
            confirmButton = {
                Button(onClick = { seen = true; onOpenBackup() }) { Text(stringResource(R.string.wavelength_move_back_up)) }
            },
            dismissButton = { TextButton(onClick = { seen = true }) { Text(stringResource(R.string.wavelength_move_later)) } },
        )
    }

    /** Shown in the new app on each launch while the old app is installed, until marked done. */
    @Composable
    private fun NewAppGuide(onOpenBackup: () -> Unit) {
        val context = LocalContext.current
        var done by rememberPreference(NewGuideDoneKey, false)
        var hidden by remember { mutableStateOf(false) }
        val installed = remember { oldAppInstalled(context) }
        if (done || hidden || !installed) return
        AlertDialog(
            onDismissRequest = { hidden = true },
            title = { Text(stringResource(R.string.wavelength_move_new_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.wavelength_move_new_text))
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { hidden = true; onOpenBackup() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wavelength_move_restore))
                    }
                    OutlinedButton(onClick = { openOldAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.wavelength_move_remove_old))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { done = true }) { Text(stringResource(R.string.wavelength_move_done)) } },
            dismissButton = { TextButton(onClick = { hidden = true }) { Text(stringResource(R.string.wavelength_move_later)) } },
        )
    }

    /** Opens the old app's system info page, where Uninstall is one tap. */
    private fun openOldAppSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$OLD_APP_ID".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** A card for the old app's Updater screen. */
    @Composable
    fun UpdaterCard(onOpenBackup: () -> Unit) {
        if (!isOldApp(LocalContext.current)) return
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.wavelength_move_title), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.wavelength_move_old_text), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenBackup) { Text(stringResource(R.string.wavelength_move_back_up)) }
            }
        }
    }
}

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.SavedErrorLogs
import com.metrolist.music.utils.SavedErrorReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SavedErrorLogSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(SavedErrorLogs.enabled()) }
    var busy by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf<List<SavedErrorReport>>(emptyList()) }
    var showReports by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SavedErrorReport?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val failure = stringResource(R.string.saved_error_logs_failure)
    fun failureNotice() = Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
    fun toggle(value: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { SavedErrorLogs.setEnabled(value) } }
            if (result.isSuccess) enabled = value else failureNotice()
            busy = false
        }
    }
    Material3SettingsGroup(
        title = stringResource(R.string.saved_error_logs),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.bug_report),
                title = { Text(stringResource(R.string.save_error_logs)) },
                description = { Text(stringResource(R.string.save_error_logs_desc)) },
                trailingContent = { Switch(checked = enabled, enabled = !busy, onCheckedChange = ::toggle) },
                onClick = { toggle(!enabled) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.history),
                title = { Text(stringResource(R.string.review_error_logs)) },
                onClick = {
                    scope.launch {
                        reports = withContext(Dispatchers.IO) { SavedErrorLogs.reports() }
                        showReports = true
                    }
                },
            ),
        ),
    )
    if (showReports && selected == null) AlertDialog(
        onDismissRequest = { showReports = false },
        title = { Text(stringResource(R.string.saved_error_logs)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (reports.isEmpty()) Text(stringResource(R.string.no_saved_error_logs))
                reports.forEach { report ->
                    Text(report.text.lineSequence().take(2).joinToString("\n"),
                        Modifier.clickable { selected = report }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { showReports = false }) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(enabled = reports.isNotEmpty(), onClick = { confirmClear = true }) {
                Text(stringResource(R.string.clear_saved_error_logs))
            }
        },
    )
    selected?.let { report ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.saved_error_logs)) },
            text = {
                SelectionContainer {
                    Text(report.text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, report.text.lineSequence().first())
                            putExtra(Intent.EXTRA_TEXT, report.text)
                        }, context.getString(R.string.share)))
                    }.onFailure { failureNotice() }
                }) { Text(stringResource(R.string.share)) }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        text = { Text(stringResource(R.string.clear_saved_error_logs_confirm)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { SavedErrorLogs.clear() } }
                    reports = withContext(Dispatchers.IO) { SavedErrorLogs.reports() }
                    if (result.isFailure) failureNotice()
                    busy = false
                    confirmClear = false
                }
            }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(android.R.string.cancel)) } },
    )
}

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.recognition.FloatingRecognitionService
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem

@Composable
internal fun FloatingRecognitionSetting() {
    val context = LocalContext.current
    val running by FloatingRecognitionService.running.collectAsState()
    fun show() {
        if (!FloatingRecognitionService.show(context)) {
            Toast.makeText(context, R.string.floating_recognition_start_failed, Toast.LENGTH_LONG).show()
        }
    }
    val overlayPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(context)) show()
        else Toast.makeText(context, R.string.floating_recognition_permission_needed, Toast.LENGTH_LONG).show()
    }
    fun toggle(enabled: Boolean) {
        when {
            !enabled -> FloatingRecognitionService.hide(context)
            Settings.canDrawOverlays(context) -> show()
            else -> runCatching {
                overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }.onFailure { Toast.makeText(context, R.string.floating_recognition_start_failed, Toast.LENGTH_LONG).show() }
        }
    }
    Material3SettingsGroup(items = listOf(Material3SettingsItem(
        icon = painterResource(R.drawable.mic),
        title = { Text(stringResource(R.string.floating_recognition_title)) },
        description = { Text(stringResource(R.string.floating_recognition_description)) },
        trailingContent = { Switch(checked = running, onCheckedChange = ::toggle) },
        onClick = { toggle(!running) },
    )))
}

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.radio

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import com.metrolist.music.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.viewmodels.RadioViewModel
import java.util.Locale

/** A stream link spotted on a station website, with the page title as a name guess. */
private data class FoundStream(val url: String, val name: String)

/**
 * Browses a station-finder site (StreamURL.link, FMSTREAM) inside the app. When the page opens or
 * plays a radio stream, Wavelength offers to play it or save it as a station.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioWebScreen(
    navController: NavController,
    startUrl: String,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val playerConnection = LocalPlayerConnection.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<FoundStream?>(null) }
    var addDialog by remember { mutableStateOf<FoundStream?>(null) }

    // Called from WebView callbacks, some on background threads: touch the WebView only on its own thread
    fun offer(url: String, view: WebView?) {
        view?.post {
            if (found?.url != url) found = FoundStream(url, cleanTitle(view.title.orEmpty()))
        }
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }

    addDialog?.let { stream ->
        AddStationDialog(
            initialLink = stream.url,
            initialName = stream.name,
            onDismiss = { addDialog = null },
            onAdd = { name, link, onError ->
                viewModel.addCustom(name, link) { error ->
                    if (error == null) {
                        addDialog = null
                        found = null
                        Toast.makeText(context, R.string.radio_saved, Toast.LENGTH_SHORT).show()
                    } else {
                        onError(error)
                    }
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        TopAppBar(
            title = { Text(title.ifEmpty { startUrl }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp) {
                    Icon(painterResource(R.drawable.close), contentDescription = null)
                }
            },
            actions = {
                IconButton(onClick = { webView?.url?.let(uriHandler::openUri) }) {
                    Icon(painterResource(R.drawable.open_in_new), contentDescription = stringResource(R.string.radio_open_in_browser))
                }
            },
        )
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url.toString()
                                if (looksLikeStream(url)) {
                                    offer(url, view)
                                    return true
                                }
                                return !(url.startsWith("http://") || url.startsWith("https://"))
                            }

                            // Players on the page load the stream in the background; catch those too
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString()
                                if (!request.isForMainFrame && looksLikeStream(url, strict = false)) offer(url, view)
                                return null
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                title = view.title.orEmpty()
                                canGoBack = view.canGoBack()
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        setDownloadListener { url, _, _, mimeType, _ ->
                            val type = mimeType.orEmpty().lowercase(Locale.ROOT)
                            if (type.startsWith("audio") || "mpegurl" in type || "scpls" in type || looksLikeStream(url)) {
                                offer(url, this)
                            }
                        }
                        setOnLongClickListener {
                            val hit = hitTestResult
                            val link = hit.extra
                            if (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE && link != null && link.startsWith("http")) {
                                offer(link, this)
                                true
                            } else {
                                false
                            }
                        }
                        loadUrl(startUrl)
                        webView = this
                    }
                },
            )
            found?.let { stream ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.radio_found_stream), style = MaterialTheme.typography.labelLarge)
                        Text(
                            stream.name.ifEmpty { stream.url },
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stream.url,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                // Stop the page's own player so the two don't play over each other
                                webView?.evaluateJavascript(
                                    "document.querySelectorAll('audio,video').forEach(function(m){m.pause();});",
                                    null,
                                )
                                viewModel.playLink(
                                    stream.name,
                                    stream.url,
                                    onReady = { song ->
                                        playerConnection?.playQueue(ListQueue(title = song.song.title, items = listOf(song.toMediaItem())))
                                    },
                                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() },
                                )
                            }) { Text(stringResource(R.string.radio_play)) }
                            OutlinedButton(onClick = { addDialog = stream }) { Text(stringResource(R.string.radio_add_station)) }
                            Box(Modifier.weight(1f))
                            IconButton(onClick = { found = null }) {
                                Icon(painterResource(R.drawable.close), contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val STREAM_EXTENSIONS = listOf(".pls", ".m3u", ".m3u8", ".asx", ".xspf", ".mp3", ".aac", ".aacp", ".ogg", ".opus", ".flac")
/** Hosts that only serve audio streams. */
private val STREAM_HOSTS = listOf(
    "icecast", "shoutcast", "streamtheworld", "radiotime.com/tune", "zeno.fm", "radio.co/", "streamguys",
    "akamaihd.net", "lsrv", "audio-edge", "streamon.fm", "securenetsystems", "radioca.st", "listen2myradio",
)

/** Path hints that usually mean a stream, but are also used by ordinary pages. */
private val STREAM_PATH_HINTS = listOf(";stream", "/stream", "/listen", "/live", "stream.mp3")

/**
 * True for links that look like a radio stream or stream playlist rather than a web page.
 * [strict] (for links the user taps) skips the looser path hints so normal pages still open.
 */
internal fun looksLikeStream(url: String, strict: Boolean = true): Boolean {
    if (!url.startsWith("http://") && !url.startsWith("https://")) return false
    val lower = url.lowercase(Locale.ROOT)
    val path = lower.substringBefore('?').substringBefore('#')
    if (STREAM_EXTENSIONS.any { path.endsWith(it) }) return true
    if (listOf(".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".ico", ".woff", ".woff2", ".json", ".html", ".php").any { path.endsWith(it) }) {
        return false
    }
    // Shoutcast/Icecast servers often run on their own port, e.g. http://host:8000/
    val port = Regex("^https?://[^/]+:(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull()
    if (port != null && port !in listOf(80, 443)) return true
    if (STREAM_HOSTS.any { it in lower }) return true
    return !strict && STREAM_PATH_HINTS.any { it in path }
}

private fun cleanTitle(title: String): String =
    title.split(" | ", " - FMSTREAM", " – ", " - StreamURL", " - Stream URL", " — ")
        .first()
        .replace(Regex("(?i)\\s*(stream url|radio stream|live stream|listen live|online)\\s*$"), "")
        .trim()

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wavelength: the search button in the top bar. On a tab without its own search screen (Radio,
 * Navidrome, Media server, News, Audiobooks, Local files) the top bar turns into a search field and
 * the tab shows what matches. [route] is the tab whose search is open, null when closed.
 */
object PageSearch {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun open(route: String) {
        _query.value = ""
        _route.value = route
    }

    fun close() {
        _route.value = null
        _query.value = ""
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    /** Tabs that search inside the page itself (the top bar becomes a search field). */
    val inPageRoutes = setOf("radio", "subsonic", "media_server", "news", "audiobooks", "local_files")

    /** Hint shown in the search field for each tab. */
    fun hintFor(route: String?): Int = when (route) {
        "radio" -> R.string.radio_search_hint
        "subsonic" -> R.string.subsonic_search_hint
        "media_server" -> R.string.media_server_search
        "news" -> R.string.page_search_news
        "audiobooks" -> R.string.page_search_audiobooks
        "local_files" -> R.string.page_search_local_files
        else -> R.string.search
    }
}

/** What's typed in the top-bar search for [route], or "" when that tab's search isn't open. */
@Composable
fun rememberPageSearchQuery(route: String): String {
    val open by PageSearch.route.collectAsState()
    val query by PageSearch.query.collectAsState()
    return if (open == route) query else ""
}

/**
 * Runs [onSearch] as the user types, after a short pause (straight away when cleared). Skips the
 * first, empty value so opening a tab doesn't reset it.
 */
@Composable
fun PageSearchEffect(query: String, onSearch: (String) -> Unit) {
    val last = remember { arrayOf(query) }
    LaunchedEffect(query) {
        if (query == last[0]) return@LaunchedEffect
        if (query.isNotBlank()) delay(400)
        last[0] = query
        onSearch(query)
    }
}

/** True when [text] contains [query] (ignoring case). Blank queries match everything. */
fun matchesSearch(query: String, vararg text: String?): Boolean =
    query.isBlank() || text.any { it?.contains(query.trim(), ignoreCase = true) == true }

/** The search text field used in every search top bar, so they all look the same. */
@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: (String) -> Unit = {},
    autoFocus: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(100)
            runCatching {
                focusRequester.requestFocus()
                keyboard?.show()
            }
        }
    }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 16.sp),
                    )
                }
                innerTextField()
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            onSearch(query)
        }),
    )
}

/** Clear (✕) button for a search top bar; only shown while there's text. */
@Composable
fun SearchBarClearButton(query: String, onClear: () -> Unit) {
    if (query.isNotEmpty()) {
        IconButton(onClick = onClear) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A whole search top bar (back arrow, field, clear), for screens that draw their own top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onBack: () -> Unit,
    onSearch: (String) -> Unit = {},
    autoFocus: Boolean = true,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        title = {
            SearchBarField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = placeholder,
                onSearch = onSearch,
                autoFocus = autoFocus,
            )
        },
        actions = {
            SearchBarClearButton(query) { onQueryChange("") }
            actions()
        },
        colors = colors,
    )
}

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.audiobooks

import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.R

/** Wavelength: the tabs on the phone Audiobooks screen, shown in the order saved in settings. */
enum class AudiobookTab(val id: String, val titleRes: Int, val iconRes: Int) {
    LIBRARY("library", R.string.audiobook_tab_library, R.drawable.library_music_outlined),
    DOWNLOADED("downloaded", R.string.audiobook_tab_downloaded, R.drawable.download),
    LISTENING("listening", R.string.audiobook_tab_listening, R.drawable.audiobook),
}

/** How the Library tab orders books. Books on the phone have no added date, so they sort by title for [ADDED]. */
enum class AudiobookSort(val id: String, val labelRes: Int) {
    TITLE("title", R.string.audiobook_sort_title),
    AUTHOR("author", R.string.audiobook_sort_author),
    ADDED("added", R.string.audiobook_sort_added),
}

object AudiobookTabs {
    val OrderKey = stringPreferencesKey("audiobookTabOrder")
    val SelectedKey = stringPreferencesKey("audiobookTabSelected")
    val SortKey = stringPreferencesKey("audiobookLibrarySort")

    fun encode(tabs: List<AudiobookTab>): String = tabs.joinToString(",") { it.id }

    /** The saved order; unknown or repeated entries are dropped and missing tabs are added at the end. */
    fun decode(raw: String): List<AudiobookTab> {
        val saved = raw.split(',').mapNotNull { id -> AudiobookTab.entries.find { it.id == id.trim() } }.distinct()
        return saved + AudiobookTab.entries.filter { it !in saved }
    }

    /** The open tab: the saved one if it still exists, otherwise the first in [order]. */
    fun selected(raw: String, order: List<AudiobookTab>): AudiobookTab =
        AudiobookTab.entries.find { it.id == raw } ?: order.first()

    fun sort(raw: String): AudiobookSort = AudiobookSort.entries.find { it.id == raw } ?: AudiobookSort.TITLE

    /** Sorts books held on the phone by title or author (case-insensitive, titles break ties). */
    fun <T> sortLocal(items: List<T>, sort: AudiobookSort, title: (T) -> String, author: (T) -> String?): List<T> {
        val byTitle = compareBy<T, String>(String.CASE_INSENSITIVE_ORDER) { title(it) }
        return when (sort) {
            AudiobookSort.AUTHOR -> items.sortedWith(
                compareBy<T> { author(it).isNullOrBlank() }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { author(it).orEmpty() }
                    .then(byTitle),
            )
            else -> items.sortedWith(byTitle)
        }
    }
}

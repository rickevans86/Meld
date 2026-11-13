/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.audiobooks

import com.metrolist.music.R
import androidx.datastore.preferences.core.stringPreferencesKey

enum class AutoAudiobookSection(val id: String) {
    CONTINUE("continue"), RECENT("recent"), BROWSE("browse"), SERIES("series");
}

object AutoAudiobookSections {
    val Key = stringPreferencesKey("autoAudiobookSections")
    fun encode(sections: List<Pair<AutoAudiobookSection, Boolean>>): String =
        sections.joinToString(",") { (section, enabled) -> "${section.id}:$enabled" }

    fun decode(raw: String): List<Pair<AutoAudiobookSection, Boolean>> {
        val parsed = raw.split(',').mapNotNull { token ->
            val parts = token.split(':')
            if (parts.size != 2) return@mapNotNull null
            val section = AutoAudiobookSection.entries.find { it.id == parts.firstOrNull() } ?: return@mapNotNull null
            val enabled = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: return@mapNotNull null
            section to enabled
        }.distinctBy { it.first }
        return parsed + AutoAudiobookSection.entries.filter { section -> parsed.none { it.first == section } }.map { it to true }
    }
}

fun AutoAudiobookSection.autoTitleRes(): Int = when (this) {
    AutoAudiobookSection.CONTINUE -> R.string.auto_continue_listening
    AutoAudiobookSection.RECENT -> R.string.auto_audiobook_recent
    AutoAudiobookSection.BROWSE -> R.string.auto_audiobook_browse
    AutoAudiobookSection.SERIES -> R.string.auto_audiobook_continue_series
}

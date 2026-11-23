package com.metrolist.music.utils

/** Keep existing rows in place when another page or a delayed section arrives. Reset on refresh. */
internal class StableHomeOrder {
    private val ids = mutableListOf<String>()
    fun <T> arrange(items: List<T>, key: (T) -> String): List<T> {
        val current = items.associateBy(key)
        ids.addAll(current.keys.filterNot { it in ids })
        return ids.mapNotNull(current::get)
    }
}

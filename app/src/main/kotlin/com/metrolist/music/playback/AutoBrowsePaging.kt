/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.playback

/** Android Auto shows at most 100 children per browse node; stay under it, More folder included. */
internal const val AUTO_BROWSE_LIMIT = 99
internal const val AUTO_PAGE_SIZE = AUTO_BROWSE_LIMIT - 1

internal data class AutoPage<T>(val items: List<T>, val hasMore: Boolean)

/** A page number from a browse ID, rejecting values whose offset would overflow. */
internal fun autoPageNumber(raw: String?, size: Int = AUTO_PAGE_SIZE): Int? =
    raw?.toIntOrNull()?.takeIf { it in 0..(Int.MAX_VALUE / size - 1) }

internal fun <T> autoPage(items: List<T>, page: Int, size: Int = AUTO_PAGE_SIZE): AutoPage<T> {
    val from = (page.toLong() * size).coerceAtMost(items.size.toLong()).toInt()
    val to = (from + size).coerceAtMost(items.size)
    return AutoPage(items.subList(from, to), to < items.size)
}

/** One server page: its items and the server's total count, or null when the request failed. */
internal typealias AutoRemotePage<T> = suspend (page: Int, limit: Int) -> Pair<List<T>, Int>?

/**
 * Page [page] of an in-memory list joined with a server list paged in [size] chunks. Returns null
 * when a needed server request fails, so callers can fall back to offline content.
 */
internal suspend fun <T> autoCombinedPage(
    page: Int,
    local: List<T>,
    localFirst: Boolean,
    remote: AutoRemotePage<T>,
    size: Int = AUTO_PAGE_SIZE,
): AutoPage<T>? {
    val offset = page.toLong() * size
    val end = offset + size
    val localCount = local.size.toLong()
    fun localSlice(from: Long, to: Long) =
        local.subList(from.coerceIn(0, localCount).toInt(), to.coerceIn(0, localCount).toInt())

    if (localFirst) {
        val localPart = localSlice(offset, end)
        if (end < localCount) return AutoPage(localPart, true)
        val (remoteItems, total) = remoteWindow((offset - localCount).coerceAtLeast(0), end - localCount, size, remote) ?: return null
        return AutoPage(localPart + remoteItems, end < localCount + total)
    }
    val (remoteItems, total) = remoteWindow(offset, end, size, remote) ?: return null
    return AutoPage(remoteItems + localSlice(offset - total, end - total), end < total + localCount)
}

/** Server items with index in [from, to), and the server total. Fetches at least one page to learn the total. */
private suspend fun <T> remoteWindow(from: Long, to: Long, size: Int, remote: AutoRemotePage<T>): Pair<List<T>, Long>? {
    val first = (from / size).toInt()
    val last = if (to > from) ((to - 1) / size).toInt() else first
    val items = mutableListOf<T>()
    var total = 0L
    for (p in first..last) {
        val (pageItems, pageTotal) = remote(p, size) ?: return null
        total = pageTotal.toLong()
        pageItems.forEachIndexed { index, item -> if (p.toLong() * size + index in from until to) items += item }
        if (pageItems.size < size) break
    }
    return items to total
}

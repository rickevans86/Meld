/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.screens.library.audiobooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import java.util.Locale

/** Large centred cover with a "Finished" badge, as on the book detail page. */
@Composable
internal fun DetailCover(url: String?, finished: Boolean, size: Dp = 200.dp) {
    Box(contentAlignment = Alignment.TopStart) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(painterResource(R.drawable.audiobook), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (url != null) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (finished) {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Icon(painterResource(R.drawable.check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.audiobook_finished), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * The top of the book detail page: centred cover + badge, title, author(s), a meta line (duration),
 * optional progress, the primary action button, and a row of secondary actions supplied by the caller.
 */
@Composable
internal fun AudiobookHero(
    coverUrl: String?,
    title: String,
    authors: String?,
    metaText: String?,
    finished: Boolean,
    fraction: Float?,
    primaryText: String,
    primaryIcon: Int,
    busy: Boolean,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondary: @Composable RowScope.() -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        DetailCover(coverUrl, finished)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        authors?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        metaText?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.timer), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (fraction != null && fraction > 0f && !finished) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPrimary, enabled = primaryEnabled && !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(painterResource(primaryIcon), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(primaryText)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { secondary() }
    }
}

/** A compact outlined action button for the secondary row (sized by the caller, e.g. Modifier.weight). */
@Composable
internal fun RowScope.DetailActionButton(icon: Int, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Narrated By" with initial-avatars; collapses to the first two with a "See all" toggle. */
@Composable
internal fun NarratedBy(narrators: List<String>) {
    if (narrators.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded || narrators.size <= 2) narrators else narrators.take(2)
    DetailSectionTitle(
        stringResource(R.string.audiobook_narrated_by_title),
        trailing = {
            if (narrators.size > 2) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(R.string.audiobook_see_less) else stringResource(R.string.audiobook_see_all, narrators.size))
                }
            }
        },
    )
    shown.forEach { name ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Text(name.trim().take(1).uppercase(Locale.getDefault()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Overview" description, clamped with a Read more / Read less toggle. */
@Composable
internal fun DetailOverview(text: String) {
    if (text.isBlank()) return
    var expanded by remember { mutableStateOf(false) }
    DetailSectionTitle(stringResource(R.string.audiobook_overview))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 8.dp)) {
        Text(if (expanded) stringResource(R.string.audiobook_read_less) else stringResource(R.string.audiobook_read_more))
    }
}

/** "Genres" as chips. */
@Composable
internal fun GenreChips(genres: List<String>) {
    if (genres.isEmpty()) return
    DetailSectionTitle(stringResource(R.string.audiobook_genres))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        genres.forEach { genre ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp)) {
                Text(genre, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** A section heading with an optional trailing control (e.g. "See all", a chevron). */
@Composable
internal fun DetailSectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A tappable "Chapters" / "Audio files" heading that shows the count and a chevron. */
@Composable
internal fun CollapsibleSectionTitle(text: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Icon(
            painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

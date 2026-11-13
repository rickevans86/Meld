/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R

/** One pinned item in the Library's Pins row. */
data class LibraryPinCard(
    val key: String,
    val title: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
    val round: Boolean = false,
    /** Shown when there's no artwork, e.g. the heart for liked songs. */
    val placeholder: Int = R.drawable.queue_music,
    /** Long-press: opens the item's ⋮ menu (which has Unpin). Null unpins straight away. */
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/**
 * Spotify-style Pins row at the top of the Library: square artwork with the name underneath.
 * Long-press a pin to open its menu (auto-download, unpin and the rest).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPinsRow(
    pins: List<LibraryPinCard>,
    onUnpin: (LibraryPinCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pins.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Icon(painterResource(R.drawable.ic_push_pin), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.library_pins),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(pins, key = { it.key }) { pin ->
                val shape = if (pin.round) CircleShape else RoundedCornerShape(6.dp)
                Column(
                    Modifier
                        .width(PinSize)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = pin.onClick, onLongClick = pin.onLongClick ?: { onUnpin(pin) }),
                ) {
                    Box(
                        Modifier
                            .size(PinSize)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pin.thumbnailUrl != null) {
                            AsyncImage(
                                model = pin.thumbnailUrl,
                                contentDescription = pin.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(PinSize).clip(shape),
                            )
                        } else {
                            Icon(painterResource(pin.placeholder), contentDescription = null, modifier = Modifier.size(36.dp))
                        }
                    }
                    Text(
                        pin.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    pin.subtitle?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val PinSize = 96.dp

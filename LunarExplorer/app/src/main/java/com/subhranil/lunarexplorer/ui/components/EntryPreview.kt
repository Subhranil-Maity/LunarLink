package com.subhranil.lunarexplorer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.thumbs.Thumbnail

/**
 * The entry's icon, replaced by a server-made preview for images once it has loaded.
 * [serverId] is `null` when previews are off; the icon is then shown alone.
 */
@Composable
fun EntryPreview(
    entry: Entry,
    serverId: String?,
    iconSize: Dp,
    previewPx: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
) {
    val preview = serverId != null && Thumbnail.canPreview(entry)
    var loaded by remember(entry.path, entry.modified, preview) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) EntryIcon(entry, size = iconSize)
        if (preview) {
            AsyncImage(
                model = Thumbnail.of(serverId, entry, previewPx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
            )
        }
    }
}

/** A preview the same size as the icon, for list rows. */
@Composable
fun EntryPreviewSmall(entry: Entry, serverId: String?, size: Dp, shape: Shape) =
    EntryPreview(entry, serverId, iconSize = size, previewPx = 128, modifier = Modifier.size(size), shape = shape)

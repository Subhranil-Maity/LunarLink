package com.subhranil.lunarexplorer.ui.explorer

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.Permission
import com.subhranil.lunarexplorer.data.api.Root
import com.subhranil.lunarexplorer.data.api.RootKind
import com.subhranil.lunarexplorer.ui.components.EntryPreview
import com.subhranil.lunarexplorer.ui.components.EntryPreviewSmall
import com.subhranil.lunarexplorer.util.Crumb
import com.subhranil.lunarexplorer.util.formatBytes

@Composable
fun formatModified(epochSeconds: Long?): String {
    val context = LocalContext.current
    return remember(epochSeconds) {
        epochSeconds?.let {
            DateUtils.formatDateTime(
                context,
                it * 1000,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
            )
        }.orEmpty()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(
    entry: Entry,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where the entry is, relative to the folder being searched. Only set for search results. */
    location: String? = null,
    /** Server to load image previews from, or `null` to show icons only. */
    thumbnailServer: String? = null,
) {
    val modified = formatModified(entry.modified)
    val details = if (entry.isDirLike) listOf(modified) else listOf(formatBytes(entry.size), modified)
    val supporting = (listOfNotNull(location) + details).filter { it.isNotEmpty() }.joinToString(" · ")
    ListItem(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (entry.hidden) 0.65f else 1f),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        leadingContent = {
            if (selected) SelectionMark() else EntryPreviewSmall(entry, thumbnailServer, 40.dp, RoundedCornerShape(8.dp))
        },
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        supportingContent = { if (supporting.isNotEmpty()) Text(supporting, maxLines = 1) },
        trailingContent = if (selecting) null else {
            {
                IconButton(onClick = onMore) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
            }
        },
    )
}

/** A grid cell: a large preview with the name and size underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryTile(
    entry: Entry,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    /** Where the entry is, relative to the folder being searched. Only set for search results. */
    location: String? = null,
    /** Server to load image previews from, or `null` to show icons only. */
    thumbnailServer: String? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val supporting = location ?: if (entry.isDirLike) "" else formatBytes(entry.size)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .alpha(if (entry.hidden) 0.65f else 1f),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                EntryPreview(
                    entry,
                    serverId = thumbnailServer,
                    iconSize = 56.dp,
                    previewPx = 384,
                    modifier = Modifier.fillMaxSize(),
                )
                if (selected) {
                    SelectionMark(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        size = 28.dp,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 2.dp, bottom = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                if (!selecting) {
                    IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
fun LocationsList(
    roots: List<Root>,
    selected: Root?,
    onOpen: (Root) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(
                stringResource(R.string.explorer_locations),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(roots, key = { it.path }) { root ->
            LocationItem(root, selected = root.path == selected?.path, onClick = { onOpen(root) })
        }
    }
}

@Composable
private fun LocationItem(root: Root, selected: Boolean, onClick: () -> Unit) {
    val disk = root.disk
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        leadingContent = {
            Icon(
                painterResource(if (root.kind == RootKind.DRIVE) R.drawable.storage_icon else R.drawable.folder_icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        },
        headlineContent = { Text(root.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(root.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                if (disk != null && disk.totalBytes > 0) {
                    val used = 1f - disk.availableBytes.toFloat() / disk.totalBytes
                    LinearProgressIndicator(progress = { used.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.explorer_free_space, formatBytes(disk.availableBytes), formatBytes(disk.totalBytes)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        trailingContent = if (Permission.WRITE in root.permissions) null else {
            { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.explorer_read_only), Modifier.size(18.dp)) }
        },
    )
}

/** Scrollable path bar. The first chip goes back to the locations list. */
@Composable
fun BreadcrumbBar(
    crumbs: List<Crumb>,
    showLocations: Boolean,
    onLocations: () -> Unit,
    onCrumb: (Crumb) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(crumbs.size) { if (crumbs.isNotEmpty()) listState.animateScrollToItem(crumbs.size) }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLocations) {
            item {
                TextButton(onClick = onLocations) { Text(stringResource(R.string.explorer_locations)) }
            }
        }
        itemsIndexed(crumbs, key = { _, c -> c.path }) { index, crumb ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLocations || index > 0) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                val last = index == crumbs.lastIndex
                if (last) {
                    AssistChip(onClick = {}, label = { Text(crumb.name, maxLines = 1) })
                } else {
                    TextButton(onClick = { onCrumb(crumb) }) { Text(crumb.name, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
fun ClipboardBar(
    clipboard: Clipboard,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
            Text(
                pluralStringResource(
                    if (clipboard.mode == ClipboardMode.COPY) R.plurals.explorer_clipboard_copy else R.plurals.explorer_clipboard_move,
                    clipboard.entries.size,
                    clipboard.entries.size,
                ),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onPaste, enabled = canPaste) {
                Icon(painterResource(R.drawable.paste_icon), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.explorer_paste_here))
            }
        }
    }
}

@Composable
fun ProgressBar(progress: Progress) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.explorer_working, progress.done, progress.total))
            LinearProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
        }
    }
}

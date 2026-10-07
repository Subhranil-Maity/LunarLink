package com.subhranil.lunarexplorer.ui.home

import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.FilledTonalButton
import com.subhranil.lunarexplorer.data.discovery.DiscoveredServer
import android.content.ClipData
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import kotlinx.coroutines.launch

@Composable
fun ServerCard(
    item: ServerItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val server = item.server
    ElevatedCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OsBadge(server.osType)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${server.username} · ${server.baseUrl.substringAfter("://")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    lastConnectedText(server.lastConnectedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(4.dp))
                StatusChip(item.status)
            }
            ServerMenu(baseUrl = server.baseUrl, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

/** A server announced on the local network that isn't saved yet. */
@Composable
fun NearbyServerCard(server: DiscoveredServer, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onAdd, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OsBadge(server.osType)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${server.host}:${server.port}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.home_nearby_add)) }
        }
    }
}

@Composable
private fun OsBadge(osType: String?) {
    val icon = if (osType == "windows") R.drawable.windows_icon else R.drawable.ic_computer
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = osType?.let(::osDisplayName),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun StatusChip(status: ServerStatus) {
    val (label, color) = when (status) {
        ServerStatus.CHECKING -> stringResource(R.string.home_status_checking) to MaterialTheme.colorScheme.onSurfaceVariant
        ServerStatus.ONLINE -> stringResource(R.string.home_status_online) to MaterialTheme.colorScheme.primary
        ServerStatus.OFFLINE -> stringResource(R.string.home_status_offline) to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == ServerStatus.CHECKING) {
            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = color)
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun ServerMenu(baseUrl: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_copy_address)) },
                leadingIcon = { Icon(painterResource(R.drawable.copy_icon), contentDescription = null, Modifier.size(24.dp)) },
                onClick = {
                    expanded = false
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("address", baseUrl))) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

/** "linux" -> "Linux", "macos" -> "macOS". Unknown values are capitalized. */
fun osDisplayName(osType: String): String = when (osType.lowercase()) {
    "linux" -> "Linux"
    "windows" -> "Windows"
    "macos" -> "macOS"
    "freebsd" -> "FreeBSD"
    else -> osType.replaceFirstChar { it.uppercase() }
}

@Composable
private fun lastConnectedText(epochSeconds: Long?): String {
    if (epochSeconds == null) return stringResource(R.string.home_never_connected)
    val relative = DateUtils.getRelativeTimeSpanString(
        epochSeconds * 1000,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return stringResource(R.string.home_last_connected, relative)
}

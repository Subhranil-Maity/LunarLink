package com.subhranil.lunarexplorer.ui.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.EntryKind
import com.subhranil.lunarexplorer.ui.components.EntryIcon
import com.subhranil.lunarexplorer.util.NameError
import com.subhranil.lunarexplorer.util.PathStyle
import com.subhranil.lunarexplorer.util.ServerPaths
import com.subhranil.lunarexplorer.util.formatBytes
import com.subhranil.lunarexplorer.util.validateName

@Composable
private fun NameError.message(): String = stringResource(
    when (this) {
        NameError.EMPTY -> R.string.name_error_empty
        NameError.RESERVED -> R.string.name_error_reserved
        NameError.INVALID_CHARACTER -> R.string.name_error_invalid
        NameError.TOO_LONG -> R.string.name_error_too_long
    }
)

/**
 * Asks for a file or folder name with live validation. For renames the base name (without the
 * extension) is pre-selected.
 */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    style: PathStyle,
    existingNames: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectionEnd = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selectionEnd))) }
    val name = value.text.trim()
    val validation = validateName(name, style)
    val duplicate = name != initial && existingNames.any {
        if (style == PathStyle.WINDOWS) it.equals(name, ignoreCase = true) else it == name
    }
    val error: String? = when {
        value.text.isEmpty() -> null
        validation != null -> validation.message()
        duplicate -> stringResource(R.string.error_already_exists)
        else -> null
    }
    val canConfirm = validation == null && !duplicate
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.dialog_name)) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(name) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun DeleteDialog(entries: List<Entry>, onConfirm: (permanent: Boolean) -> Unit, onDismiss: () -> Unit) {
    var permanent by rememberSaveable { mutableStateOf(false) }
    val hasFolders = entries.any { it.kind == EntryKind.DIR }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = {
            Text(
                if (entries.size == 1) stringResource(R.string.dialog_delete_title_one, entries.first().name)
                else pluralStringResource(R.plurals.dialog_delete_title_many, entries.size, entries.size),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (permanent) R.string.dialog_delete_body_permanent else R.string.dialog_delete_body_trash)
                )
                if (hasFolders) Text(stringResource(R.string.dialog_delete_folders_note))
                CheckRow(stringResource(R.string.dialog_delete_permanently), permanent) { permanent = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(permanent) }) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ConflictDialog(name: String, onChoice: (ConflictAction, applyToAll: Boolean) -> Unit) {
    var applyToAll by remember(name) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onChoice(ConflictAction.SKIP, false) },
        title = { Text(stringResource(R.string.dialog_conflict_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_conflict_body))
                CheckRow(stringResource(R.string.dialog_conflict_apply_all), applyToAll) { applyToAll = it }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onChoice(ConflictAction.SKIP, applyToAll) }) {
                    Text(stringResource(R.string.dialog_conflict_skip))
                }
                TextButton(onClick = { onChoice(ConflictAction.KEEP_BOTH, applyToAll) }) {
                    Text(stringResource(R.string.dialog_conflict_keep_both))
                }
                TextButton(onClick = { onChoice(ConflictAction.REPLACE, applyToAll) }) {
                    Text(stringResource(R.string.dialog_conflict_replace))
                }
            }
        },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@Composable
fun DetailsDialog(entry: Entry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { EntryIcon(entry, size = 32.dp) },
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailRow(stringResource(R.string.details_type), kindLabel(entry))
                if (!entry.isDirLike) DetailRow(stringResource(R.string.details_size), formatBytes(entry.size))
                DetailRow(stringResource(R.string.details_modified), formatModified(entry.modified))
                DetailRow(stringResource(R.string.details_location), ServerPaths.parent(entry.path) ?: entry.path)
                entry.symlinkTarget?.let { DetailRow(stringResource(R.string.details_link_target), it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun kindLabel(entry: Entry): String {
    val kind = stringResource(
        when (entry.kind) {
            EntryKind.FILE -> R.string.kind_file
            EntryKind.DIR -> R.string.kind_folder
            EntryKind.SYMLINK -> R.string.kind_symlink
            EntryKind.OTHER -> R.string.kind_other
        }
    )
    return entry.mimeType?.let { "$kind · $it" } ?: kind
}

/** Actions for one item: shown when a file is tapped or an item's "more" button is pressed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryActionsSheet(
    entry: Entry,
    canWrite: Boolean,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { EntryIcon(entry) },
                headlineContent = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(kindLabel(entry)) },
            )
            HorizontalDivider()
            SheetAction(rememberVectorPainter(Icons.Default.Info), stringResource(R.string.explorer_details), onClick = onDetails)
            if (!entry.isDirLike) {
                SheetAction(
                    painterResource(R.drawable.download_icon),
                    stringResource(R.string.explorer_download),
                    onClick = onDownload,
                )
            }
            SheetAction(painterResource(R.drawable.copy_icon), stringResource(R.string.explorer_copy), onClick = onCopy)
            if (canDelete) {
                SheetAction(painterResource(R.drawable.move_folder_icon), stringResource(R.string.explorer_move), onClick = onMove)
            }
            if (canWrite) {
                SheetAction(painterResource(R.drawable.rename_icon), stringResource(R.string.explorer_rename), onClick = onRename)
            }
            if (canDelete) {
                SheetAction(
                    painterResource(R.drawable.delete_icon),
                    stringResource(R.string.action_delete),
                    destructive = true,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: Painter,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp)) },
        headlineContent = { Text(label, color = color) },
    )
}

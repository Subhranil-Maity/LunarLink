package com.subhranil.lunarexplorer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.SortKey
import com.subhranil.lunarexplorer.data.api.SortOrder
import com.subhranil.lunarexplorer.data.settings.ThemeMode
import com.subhranil.lunarexplorer.data.settings.ViewMode
import com.subhranil.lunarexplorer.ui.resolve
import org.koin.androidx.compose.koinViewModel

private enum class SettingsDialog { THEME, SORT, VIEW, REMOVE_SERVERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it.resolve(context)) } }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Keep rows readable on wide screens instead of stretching edge to edge.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = padding,
            ) {
                item { SectionHeader(stringResource(R.string.settings_appearance)) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_theme)) },
                        supportingContent = { Text(themeLabel(settings.theme)) },
                        modifier = Modifier.clickable { dialog = SettingsDialog.THEME },
                    )
                }
                item {
                    SwitchItem(
                        title = stringResource(R.string.settings_dynamic_color),
                        summary = stringResource(R.string.settings_dynamic_color_summary),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
                item {
                    SwitchItem(
                        title = stringResource(R.string.settings_haptics),
                        summary = stringResource(R.string.settings_haptics_summary),
                        checked = settings.hapticsEnabled,
                        onCheckedChange = viewModel::setHaptics,
                    )
                }

                item { SectionHeader(stringResource(R.string.settings_browsing)) }
                item {
                    SwitchItem(
                        title = stringResource(R.string.explorer_show_hidden),
                        summary = stringResource(R.string.settings_show_hidden_summary),
                        checked = settings.showHiddenFiles,
                        onCheckedChange = viewModel::setShowHidden,
                    )
                }
                item {
                    val order = if (settings.sortOrder == SortOrder.DESC) " · ${stringResource(R.string.explorer_sort_descending)}" else ""
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_default_sort)) },
                        supportingContent = { Text(sortLabel(settings.sortKey) + order) },
                        modifier = Modifier.clickable { dialog = SettingsDialog.SORT },
                    )
                }
                item {
                    SwitchItem(
                        title = stringResource(R.string.settings_thumbnails),
                        summary = stringResource(R.string.settings_thumbnails_summary),
                        checked = settings.showThumbnails,
                        onCheckedChange = viewModel::setShowThumbnails,
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_default_view)) },
                        supportingContent = { Text(viewModeLabel(settings.viewMode)) },
                        modifier = Modifier.clickable { dialog = SettingsDialog.VIEW },
                    )
                }

                item { SectionHeader(stringResource(R.string.settings_data)) }
                item {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.settings_remove_servers), color = MaterialTheme.colorScheme.error)
                        },
                        supportingContent = { Text(stringResource(R.string.settings_remove_servers_summary)) },
                        modifier = Modifier.clickable { dialog = SettingsDialog.REMOVE_SERVERS },
                    )
                }

                item { SectionHeader(stringResource(R.string.settings_about)) }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.app_name)) },
                        supportingContent = { Text(stringResource(R.string.settings_version, version)) },
                    )
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.theme,
            label = { themeLabel(it) },
            onSelect = { viewModel.setTheme(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.SORT -> SortDialog(
            key = settings.sortKey,
            order = settings.sortOrder,
            onChange = viewModel::setSort,
            onDismiss = { dialog = null },
        )
        SettingsDialog.VIEW -> ChoiceDialog(
            title = stringResource(R.string.settings_default_view),
            options = ViewMode.entries,
            selected = settings.viewMode,
            label = { viewModeLabel(it) },
            onSelect = { viewModel.setViewMode(it); dialog = null },
            onDismiss = { dialog = null },
        )
        SettingsDialog.REMOVE_SERVERS -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_remove_servers_confirm)) },
            text = { Text(stringResource(R.string.settings_remove_servers_summary)) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeAllServers(); dialog = null }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.selectable(selected = checked, role = Role.Switch) { onCheckedChange(!checked) },
    )
}

@Composable
fun themeLabel(theme: ThemeMode): String = stringResource(
    when (theme) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }
)

@Composable
fun viewModeLabel(mode: ViewMode): String = stringResource(
    when (mode) {
        ViewMode.LIST -> R.string.explorer_view_list
        ViewMode.GRID -> R.string.explorer_view_grid
    }
)

@Composable
fun sortLabel(key: SortKey): String = stringResource(
    when (key) {
        SortKey.NAME -> R.string.explorer_sort_name
        SortKey.MODIFIED -> R.string.explorer_sort_modified
        SortKey.SIZE -> R.string.explorer_sort_size
        SortKey.KIND -> R.string.explorer_sort_kind
    }
)

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    RadioRow(text = label(option), selected = option == selected) { onSelect(option) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** Shared by Settings and the explorer's sort menu. */
@Composable
fun SortDialog(key: SortKey, order: SortOrder, onChange: (SortKey, SortOrder) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.explorer_sort)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    SortKey.entries.forEach { option ->
                        RadioRow(text = sortLabel(option), selected = option == key) { onChange(option, order) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                val descending = order == SortOrder.DESC
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = descending, role = Role.Switch) {
                            onChange(key, if (descending) SortOrder.ASC else SortOrder.DESC)
                        }
                        .padding(vertical = 8.dp),
                ) {
                    Text(stringResource(R.string.explorer_sort_descending), Modifier.weight(1f))
                    Switch(checked = descending, onCheckedChange = null)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, Modifier.padding(start = 16.dp))
    }
}

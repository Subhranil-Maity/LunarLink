package com.subhranil.lunarexplorer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.SortKey
import com.subhranil.lunarexplorer.data.api.SortOrder
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.data.session.SessionManager
import com.subhranil.lunarexplorer.data.settings.AppSettings
import com.subhranil.lunarexplorer.data.settings.SettingsRepository
import com.subhranil.lunarexplorer.data.settings.ThemeMode
import com.subhranil.lunarexplorer.data.settings.ViewMode
import com.subhranil.lunarexplorer.ui.UiText
import com.subhranil.lunarexplorer.ui.uiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val servers: ServerRepository,
    private val sessions: SessionManager,
) : ViewModel() {

    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setTheme(theme: ThemeMode) = launch { settings.setTheme(theme) }

    fun setDynamicColor(enabled: Boolean) = launch { settings.setDynamicColor(enabled) }

    fun setShowHidden(show: Boolean) = launch { settings.setShowHiddenFiles(show) }

    fun setSort(key: SortKey, order: SortOrder) = launch { settings.setSort(key, order) }

    fun setViewMode(mode: ViewMode) = launch { settings.setViewMode(mode) }

    fun setHaptics(enabled: Boolean) = launch { settings.setHaptics(enabled) }

    fun removeAllServers() = launch {
        sessions.disconnect()
        servers.deleteAll()
        _messages.send(uiText(R.string.settings_servers_removed))
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

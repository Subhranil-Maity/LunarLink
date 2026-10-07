package com.subhranil.lunarexplorer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.subhranil.lunarexplorer.data.api.SortKey
import com.subhranil.lunarexplorer.data.api.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ViewMode { LIST, GRID }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val showHiddenFiles: Boolean = true,
    val sortKey: SortKey = SortKey.NAME,
    val sortOrder: SortOrder = SortOrder.ASC,
    val viewMode: ViewMode = ViewMode.LIST,
    val hapticsEnabled: Boolean = true,
)

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            theme = p[THEME].toEnum(defaults.theme),
            dynamicColor = p[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            showHiddenFiles = p[SHOW_HIDDEN] ?: defaults.showHiddenFiles,
            sortKey = p[SORT_KEY].toEnum(defaults.sortKey),
            sortOrder = p[SORT_ORDER].toEnum(defaults.sortOrder),
            viewMode = p[VIEW_MODE].toEnum(defaults.viewMode),
            hapticsEnabled = p[HAPTICS] ?: defaults.hapticsEnabled,
        )
    }

    suspend fun setTheme(theme: ThemeMode) = store.edit { it[THEME] = theme.name }

    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[DYNAMIC_COLOR] = enabled }

    suspend fun setShowHiddenFiles(show: Boolean) = store.edit { it[SHOW_HIDDEN] = show }

    suspend fun setSort(key: SortKey, order: SortOrder) = store.edit {
        it[SORT_KEY] = key.name
        it[SORT_ORDER] = order.name
    }

    suspend fun setViewMode(mode: ViewMode) = store.edit { it[VIEW_MODE] = mode.name }

    suspend fun setHaptics(enabled: Boolean) = store.edit { it[HAPTICS] = enabled }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val SORT_KEY = stringPreferencesKey("sort_key")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val HAPTICS = booleanPreferencesKey("haptics")

        inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
            this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
    }
}

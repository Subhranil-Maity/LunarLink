package com.subhranil.lunarexplorer.settings

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import kotlin.collections.indexOfFirst

enum class Theme {
    SYSTEM,
    LIGHT,
    DARK
}

val AppearanceSettings = SettingObject.SettingGroup(
    name = "Appearance",
    description = "Customize the look and feel of the app.",
    settings = listOf(
        SettingObject.OptionSettingItem(
            title = "Theme",
            description = "Choose the theme for the app.",
            options = Theme.entries,
            value = Theme.SYSTEM
        )
    )
)
val DummySetting = SettingObject.SettingGroup(
    name = "Dummy",
    description = "This is a dummy setting group.",
    settings = listOf(
        SettingObject.StringSettingItem(
            title = "Dummy String",
            description = "This is a dummy string setting.",
            value = "Default String"
        ),
        SettingObject.IntSettingItem(
            title = "Dummy Int",
            description = "This is a dummy int setting.",
            value = 42,
            min = 0,
            max = 100,
            entryType = IntSettingEntryType.NUMBER
        ),
        SettingObject.IntSettingItem(
            title = "Dummy Int 2",
            description = "This is a dummy int setting.",
            value = 42,
            min = 0,
            max = 100,
            entryType = IntSettingEntryType.SLIDER
        ),
        SettingObject.BooleanSettingItem(
            title = "Dummy Boolean",
            description = "This is a dummy boolean setting.",
            value = true,
            entryType = BooleanEntryType.SWITCH
        )
    )
)


object AppSettings {
    // Use Compose state list instead of plain mutable list
    private var settings = mutableStateListOf<SettingObject.SettingGroup>()

    init {
        settings.addAll(
            listOf(
                AppearanceSettings,
                DummySetting
            )
        )
    }

    @Composable
    fun ForEachSetting(
        action: @Composable (SettingObject.SettingGroup) -> Unit
    ) {
        settings.forEach {
            action(it)
        }
    }

    fun getSettings(): List<SettingObject.SettingGroup> = settings

    fun updateSetting(settingObject: SettingObject.SettingGroup) {
        val index = settings.indexOfFirst { it.name == settingObject.name }
        if (index != -1) {
            settings[index] = settingObject // Triggers recomposition now
        } else {
            throw IllegalArgumentException("Setting group with name ${settingObject.name} not found.")
        }
    }
}

package com.subhranil.lunarexplorer.settings.sub

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.settings.SettingGroup
import com.subhranil.lunarexplorer.settings.SettingObject
import com.subhranil.lunarexplorer.settings.settingui.SettingOptions
import kotlinx.serialization.Serializable

@Serializable
enum class Theme {
    SYSTEM,
    LIGHT,
    DARK
}
@Serializable
class AppearanceSettings() : SettingGroup(
    name = "Appearance",
    description = "Customize the look and feel of the app.",
) {
    var theme by  mutableStateOf( SettingObject.OptionSettingItem(
        title = "Theme",
        description = "Choose the theme for the app.",
        options = Theme.entries,
        value = Theme.SYSTEM
    ))
}
@Composable
fun AppearanceSettingPage(
    setting: AppearanceSettings,
    onValueChanged: (AppearanceSettings) -> Unit
) {

    Column {
        Text(
            setting.name,
            style = MaterialTheme.typography.headlineLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        SettingOptions(
            setting = setting.theme,
            onValueChange = {
                setting.theme = setting.theme.copy(
                    value = it
                )
                onValueChanged(setting)
            }
        )
    }
}


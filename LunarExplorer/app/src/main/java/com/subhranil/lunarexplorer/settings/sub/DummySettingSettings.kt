package com.subhranil.lunarexplorer.settings.sub

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.settings.BooleanEntryType
import com.subhranil.lunarexplorer.settings.IntSettingEntryType
import com.subhranil.lunarexplorer.settings.SettingGroup
import com.subhranil.lunarexplorer.settings.SettingObject
import com.subhranil.lunarexplorer.settings.settingui.SettingBoolean
import com.subhranil.lunarexplorer.settings.settingui.SettingInt
import com.subhranil.lunarexplorer.settings.settingui.SettingOptions
import kotlinx.serialization.Serializable

@Serializable
class DummySettingSettings() : SettingGroup(
    name = "Dummy",
    description = "This is a dummy setting group."
) {
    var dummyString by  mutableStateOf(SettingObject.StringSettingItem(
        title = "Dummy String",
        description = "This is a dummy string setting.",
        value = "Default String"
    ))
    var dummyInt   by  mutableStateOf(SettingObject.IntSettingItem(
        title = "Dummy Int",
        description = "This is a dummy int setting.",
        value = 42,
        min = 0,
        max = 100,
        entryType = IntSettingEntryType.NUMBER
    ))
    var dummyIntSlider  by  mutableStateOf(SettingObject.IntSettingItem(
        title = "Dummy Int Slider",
        description = "This is a dummy int setting with slider.",
        value = 42,
        min = 0,
        max = 100,
        entryType = IntSettingEntryType.SLIDER
    ))
    var dummyBoolean by mutableStateOf(SettingObject.BooleanSettingItem(
        title = "Dummy Boolean",
        description = "This is a dummy boolean setting.",
        value = true,
        entryType = BooleanEntryType.SWITCH
    ))


}
@Composable
fun DummySettingPage(
    modifier: Modifier = Modifier,
    setting: DummySettingSettings,
    onValueChanged: (DummySettingSettings) -> Unit
) {
    Column {
        Text(
            setting.name,
            style = MaterialTheme.typography.headlineLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        SettingInt(
            setting = setting.dummyInt,
            onValueChange = { set ->
                setting.dummyInt = setting.dummyInt.copy(
                    value = set
                )
                onValueChanged(setting)
            }
        )
        SettingInt(
            setting = setting.dummyIntSlider,
            onValueChange = { set ->
                setting.dummyIntSlider = setting.dummyIntSlider.copy(
                    value = set
                )
                onValueChanged(setting)
            }
        )
        SettingBoolean(
            setting = setting.dummyBoolean,
        ) { set ->
            setting.dummyBoolean = setting.dummyBoolean.copy(
                value = set
            )
            onValueChanged(setting)
        }
    }
}
package com.subhranil.lunarexplorer.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.settings.settingui.SettingBoolean
import com.subhranil.lunarexplorer.settings.settingui.SettingInt
import com.subhranil.lunarexplorer.settings.settingui.SettingOptions
import com.subhranil.lunarexplorer.settings.settingui.SettingString

@Composable
fun SettingSubList(
    modifier: Modifier = Modifier,
    setting: SettingObject.SettingGroup,
    onSettingChange: (SettingObject.SettingGroup) -> Unit
) {

    Column(
        modifier = modifier
    ) {
        Text(
            setting.name,
            style = MaterialTheme.typography.headlineLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            )
        )
        setting.settings.forEach { subSetting ->
            when(subSetting){
                is SettingObject.BooleanSettingItem -> {
                    SettingBoolean(
                        setting = subSetting,
                        onValueChange = {
                            onSettingChange(
                                setting.copy(
                                    settings = setting.settings.map {
                                        if (it is SettingObject.BooleanSettingItem && it.title == subSetting.title) {
                                            it.copy(value = it.value.not())
                                        } else {
                                            it
                                        }
                                    }
                                )
                            )
                        }
                    )
                }
                is SettingObject.IntSettingItem -> {
                    SettingInt(
                        setting = subSetting,
                        onValueChange = { newValue ->
                            onSettingChange(
                                setting.copy(
                                    settings = setting.settings.map {
                                        if (it is SettingObject.IntSettingItem && it.title == subSetting.title) {
                                            it.copy(value = newValue)
                                        } else {
                                            it
                                        }
                                    }
                                )
                            )
                        }
                    )
                }
                is SettingObject.OptionSettingItem<*> -> {
                    SettingOptions(
                        setting = subSetting,
                        onValueChange = { newValue ->
                            onSettingChange(
                                setting.copy(
                                    settings = setting.settings.map {
                                        if (it is SettingObject.OptionSettingItem<*> && it.title == subSetting.title) {
                                            @Suppress("UNCHECKED_CAST")
                                            (it as SettingObject.OptionSettingItem<Enum<*>>)
                                                .copy(value = newValue)
                                        } else it
                                    }
                                )
                            )
                        }
                    )
                }
                is SettingObject.SettingGroup -> {
                    Text("Sub Groups Are Not Supported Yet",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.error
                        ),
                    )
                }
                is SettingObject.StringSettingItem -> {
                    SettingString(
                        setting = subSetting,
                        onValueChange = { newValue ->
                            onSettingChange(
                                setting.copy(
                                    settings = setting.settings.map {
                                        if (it is SettingObject.StringSettingItem && it.title == subSetting.title) {
                                            it.copy(value = newValue)
                                        } else {
                                            it
                                        }
                                    }
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
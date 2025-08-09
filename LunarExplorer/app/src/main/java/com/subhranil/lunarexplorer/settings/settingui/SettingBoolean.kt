package com.subhranil.lunarexplorer.settings.settingui

import android.widget.ToggleButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.settings.BooleanEntryType
import com.subhranil.lunarexplorer.settings.SettingObject
import kotlin.Boolean

@Composable
fun SettingBoolean(
    modifier: Modifier = Modifier,
    setting: SettingObject.BooleanSettingItem,
    onValueChange: (Boolean) -> Unit
) {
    SettingIndividual(
        modifier = modifier,
        name = setting.title,
        description = setting.description
    ){
        when(setting.entryType){
            BooleanEntryType.CHECKBOX -> SettingBooleanCheckbox(
                value = setting.value,
                onValueChange = onValueChange
            )
            BooleanEntryType.SWITCH -> SettingBooleanToggle(
                value = setting.value,
                onValueChange = onValueChange
            )
        }
    }
}

@Composable
fun SettingBooleanToggle(
    modifier: Modifier = Modifier,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Switch(
        value,
        onCheckedChange = {
            onValueChange(it)
        }
    )
}

@Composable
fun SettingBooleanCheckbox(
    modifier: Modifier = Modifier,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Checkbox(
        checked = value,
        onCheckedChange = { onValueChange(it) }
    )
}
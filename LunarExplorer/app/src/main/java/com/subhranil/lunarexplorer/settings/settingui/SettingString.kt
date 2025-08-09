package com.subhranil.lunarexplorer.settings.settingui

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.subhranil.lunarexplorer.settings.IntSettingEntryType
import com.subhranil.lunarexplorer.settings.SettingObject

@Composable
fun SettingString(
    modifier: Modifier = Modifier,
    setting: SettingObject.StringSettingItem,
    onValueChange: (String) -> Unit
) {
    SettingIndividual(
        modifier = modifier,
        name = setting.title,
        description = setting.description
    ) {
        OutlinedTextField(
            value = setting.value,
            onValueChange = onValueChange,
            singleLine = true,
            maxLines = 1,
        )
    }
}
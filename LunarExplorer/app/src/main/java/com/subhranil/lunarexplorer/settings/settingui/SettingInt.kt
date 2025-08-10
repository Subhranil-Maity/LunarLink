package com.subhranil.lunarexplorer.settings.settingui

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.settings.IntSettingEntryType
import com.subhranil.lunarexplorer.settings.SettingObject
import kotlin.Int

@Composable
fun SettingInt(
    modifier: Modifier = Modifier,
    setting: SettingObject.IntSettingItem,
    onValueChange: (Int) -> Unit
) {
    SettingIndividual(
        modifier = modifier,
        name = setting.title,
        description = setting.description
    ) {
        when (setting.entryType) {
            IntSettingEntryType.NUMBER -> SettingIntText(
                value = setting.value,
                onValueChange = onValueChange,
                range = setting.min..setting.max
            )

            IntSettingEntryType.SLIDER -> SettingIntSlider(
                value = setting.value,
                onValueChange = onValueChange,
                range = setting.min..setting.max

            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingIntSlider(
    modifier: Modifier = Modifier,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    Row(
        Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value.toString())
        Spacer(Modifier.width(8.dp))
        Slider(
            modifier = Modifier.width(200.dp),
            value = value.toFloat(),
            onValueChange = {
                onValueChange(it.toInt())
            },
            steps = range.sum(),
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }

}

@Composable
fun SettingIntText(
    modifier: Modifier = Modifier,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = {
            val newValue = it.toIntOrNull()
            if (newValue != null) {
                if (newValue in range) {
                    onValueChange(newValue)
                }
            }
        },
        modifier = modifier,
        singleLine = true,
    )
}
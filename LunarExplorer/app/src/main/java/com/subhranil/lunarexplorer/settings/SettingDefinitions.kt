package com.subhranil.lunarexplorer.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.serialization.Serializable
import kotlin.enums.EnumEntries


//@Serializable
//abstract class SettingGroup(
//    open val name: String,
//    open val description: String,
//)


@Serializable
abstract class SettingGroup(
    val name: String = "Add Name",
    val description: String = "Add Description",

){
//    @Composable
//    open fun SettingPage(modifier: Modifier = Modifier) {
//        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//            Text(
//                "⚠ Please reselect the setting page.",
//                color = MaterialTheme.colorScheme.error
//            )
//        }
//    }

}


@Serializable
sealed class SettingObject(

) {
    @Serializable
    data class StringSettingItem(
        val title: String = "Add Title",
        val description: String = "Add Description",
        val value: String = "Default Value",
        val min: Int = 0,
        val max: Int = 1000,
    ) : SettingObject()

    @Serializable
    data class IntSettingItem(
        val title: String = "Add Title",
        val description: String = "Add Description",
        val value: Int = 0,
        val min: Int = 0,
        val max: Int = 1000,
        val entryType: IntSettingEntryType
    ) : SettingObject()

    @Serializable
    data class BooleanSettingItem(
        val title: String = "Add Title",
        val description: String = "Add Description",
        val entryType: BooleanEntryType = BooleanEntryType.SWITCH,
        val value: Boolean = false,
    ) : SettingObject()

    @Serializable
    data class OptionSettingItem<T : Enum<T>>(
        val title: String = "Add Title",
        val description: String = "Add Description",
        val options: EnumEntries<T>,
        val value: T = options.first(),
    ) : SettingObject()
}

@Serializable
enum class BooleanEntryType {
    CHECKBOX,
    SWITCH,
//    RADIO_BUTTON,
}


@Serializable
enum class IntSettingEntryType{
    NUMBER,
    SLIDER,
}


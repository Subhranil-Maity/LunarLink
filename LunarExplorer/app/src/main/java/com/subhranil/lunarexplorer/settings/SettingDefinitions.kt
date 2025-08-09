package com.subhranil.lunarexplorer.settings

import kotlinx.serialization.Serializable
import kotlin.enums.EnumEntries


@Serializable
sealed class SettingObject(

) {
    data class SettingGroup(
        val name: String,
        val description: String,
        val settings: List<SettingObject>
    ) : SettingObject()

    data class StringSettingItem(
        val title: String = "Add Title",
        val description: String = "Add Description",
        val value: String = "Default Value",
        val min: Int = 0,
        val max: Int = 1000,
    ) : SettingObject()

    data class IntSettingItem(
        val title: String = "Add Title",
        val description: String = "Add Description",
        val value: Int = 0,
        val min: Int = 0,
        val max: Int = 1000,
        val entryType: IntSettingEntryType
    ) : SettingObject()

    data class BooleanSettingItem(
        val title: String = "Add Title",
        val description: String = "Add Description",
        val entryType: BooleanEntryType = BooleanEntryType.SWITCH,
        val value: Boolean = false,
    ) : SettingObject()

    data class OptionSettingItem<T : Enum<T>>(
        val title: String = "Add Title",
        val description: String = "Add Description",
        val options: EnumEntries<T>,
        val value: T = options.first(),
    ) : SettingObject()
}

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


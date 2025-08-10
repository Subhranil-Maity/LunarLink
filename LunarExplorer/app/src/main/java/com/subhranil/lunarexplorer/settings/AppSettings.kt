package com.subhranil.lunarexplorer.settings

import android.content.Context
import android.widget.Toast
import com.subhranil.lunarexplorer.settings.sub.AppearanceSettings
import com.subhranil.lunarexplorer.settings.sub.DummySettingSettings
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext.get


//enum class AppSettingType(
//    settingGroup: SettingGroup
//){
//    APPEARANCE(settingGroup = AppearanceSettings())
//}


@Serializable
data class AppSettings (
    var appearanceSettings: AppearanceSettings = AppearanceSettings(),
    var dummySettingSettings: DummySettingSettings = DummySettingSettings()
){
    init {
        var context = get().get<Context>()
        Toast.makeText(context, "Setting Defaults Initialised", Toast.LENGTH_SHORT).show()
    }
}

package com.subhranil.lunarexplorer.home.models

import androidx.compose.ui.text.toLowerCase
import com.subhranil.lunarexplorer.home.models.LunarDeviceType.DARWIN
import com.subhranil.lunarexplorer.home.models.LunarDeviceType.LINUX
import com.subhranil.lunarexplorer.home.models.LunarDeviceType.OTHER
import com.subhranil.lunarexplorer.home.models.LunarDeviceType.WINDOWS
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class LunarHost(
    val host: String,
    val username: String,
    val password: String,
    val deviceType: LunarDeviceType,
    val osVersion: String,
    val deviceName: String,
    val lastConnected: Instant
)

enum class LunarDeviceType{
    WINDOWS,
    LINUX,
    DARWIN,
    OTHER;
}
fun detectDeviceType(devType: String): LunarDeviceType{
    return when(devType.lowercase()){
        "windows" -> WINDOWS
        "linux" -> LINUX
        "macos" -> DARWIN
        else -> OTHER
    }
}

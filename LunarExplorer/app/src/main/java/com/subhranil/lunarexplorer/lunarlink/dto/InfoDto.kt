package com.subhranil.lunarexplorer.lunarlink.dto

import com.subhranil.lunarexplorer.home.models.LunarDeviceType
import com.subhranil.lunarexplorer.home.models.LunarHost
import com.subhranil.lunarexplorer.home.models.LunarHostConnectionConfig
import com.subhranil.lunarexplorer.home.models.detectDeviceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class InfoDto(
    @SerialName("device_name")
    val deviceName: String,
    @SerialName("os_version")
    val osVersion: String,
    @SerialName("os_type")
    val osType: String,
)

@OptIn(ExperimentalTime::class)
fun InfoDto.map(config: LunarHostConnectionConfig): LunarHost{
    return LunarHost(
        deviceType = detectDeviceType(this.osType),
        osVersion = this.osVersion,
        deviceName = this.deviceName,
        host = config.host,
        username = config.username,
        password = config.password,
        lastConnected = Instant.fromEpochSeconds(config.lastConnected)
    )
}

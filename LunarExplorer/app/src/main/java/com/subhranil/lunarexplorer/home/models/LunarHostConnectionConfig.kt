package com.subhranil.lunarexplorer.home.models

import kotlinx.serialization.Serializable

@Serializable
data class LunarHostConnectionConfig(
    val host: String,
    val username: String,
    val password: String,
    val lastConnected: Long
)

package com.subhranil.lunarexplorer

import com.subhranil.lunarexplorer.home.models.LunarHost
import com.subhranil.lunarexplorer.home.models.LunarHostConnectionConfig

data class ExplorerConfig(
    val host: String,
    val username: String,
    val password: String,
)

class GlobalStateHandler {
    var config: ExplorerConfig? = null
}
package com.subhranil.lunarexplorer

import com.subhranil.lunarexplorer.home.models.LunarHost
import com.subhranil.lunarexplorer.home.models.LunarHostConnectionConfig
import com.subhranil.lunarexplorer.lunarlink.LunarServerRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

//data class ExplorerConfig(
//    val host: String,
//    val username: String,
//    val password: String,
//)

class GlobalStateHandler {
//    var connection: LunarServerRepo? = null
    private val _connection = MutableStateFlow<LunarServerRepo?>(null)
    var connection: StateFlow<LunarServerRepo?> = _connection

    fun setConnection(newConn: LunarServerRepo?) {
        _connection.value = newConn
    }
    fun disconnect() {
        _connection.value = null
    }
}
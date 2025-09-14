package com.subhranil.lunarexplorer

import com.subhranil.lunarexplorer.home.models.LunarHost
import com.subhranil.lunarexplorer.home.models.LunarHostConnectionConfig
import com.subhranil.lunarexplorer.lunarlink.LunarServerRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.String

class GlobalStateHandler {
    private val _connection = MutableStateFlow<LunarServerRepo?>(null)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var connection: StateFlow<LunarServerRepo?> = _connection


    fun setConnection(newConn: LunarServerRepo?) {
        _connection.value = newConn
    }

    fun disconnect() {
        _connection.value = null
    }
}
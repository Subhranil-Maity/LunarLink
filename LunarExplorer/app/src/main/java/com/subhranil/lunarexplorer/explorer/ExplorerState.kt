package com.subhranil.lunarexplorer.explorer

import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.models.defaultMounts

data class ExplorerState(
    val isConnected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val mounts: Mounts = defaultMounts(),
    val pathHistory: List<String> = emptyList()
)

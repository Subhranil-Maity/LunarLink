package com.subhranil.lunarexplorer.home

import com.subhranil.lunarexplorer.home.models.LunarHost
import com.subhranil.lunarexplorer.services.ConnectionManagerService

sealed class HomeActions{
    data object OnShowAddNewServerDialog: HomeActions()
    data object OnDismissAddNewServerDialog: HomeActions()
    data class OnAddServer(val host: String, val username: String, val password: String): HomeActions()
    data class OnSelectServer(val host: LunarHost, val connectionManagerService: ConnectionManagerService): HomeActions()

}
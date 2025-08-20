package com.subhranil.lunarexplorer.home

import com.subhranil.lunarexplorer.home.models.LunarHost

sealed class HomeActions{
    data object OnShowAddNewServerDialog: HomeActions()
    data object OnDismissAddNewServerDialog: HomeActions()
    data class OnAddServer(val host: String, val username: String, val password: String): HomeActions()
    data class OnSelectServer(val host: LunarHost): HomeActions()

}
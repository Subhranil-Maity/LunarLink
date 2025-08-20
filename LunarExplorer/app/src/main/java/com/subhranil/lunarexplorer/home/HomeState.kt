package com.subhranil.lunarexplorer.home

import com.subhranil.lunarexplorer.home.models.LunarHost

data class HomeState(
    val listOfActiveLunarHosts: List<LunarHost> = emptyList(),
    val showAddServerDialog: Boolean = false,
)
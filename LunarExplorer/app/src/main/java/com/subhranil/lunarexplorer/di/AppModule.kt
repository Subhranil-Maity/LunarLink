package com.subhranil.lunarexplorer.di

import com.subhranil.lunarexplorer.GlobalStateHandler
import com.subhranil.lunarexplorer.explorer.ExplorerViewModel
import com.subhranil.lunarexplorer.home.HomeViewModel
import com.subhranil.lunarexplorer.home.store.HomeStorage
import com.subhranil.lunarexplorer.lunarlink.provideHttpClient
import com.subhranil.lunarexplorer.settings.AppSettings
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

var singletons = module {
    single { AppSettings() }
    single { provideHttpClient() }
    singleOf(::HomeStorage)
    singleOf(::GlobalStateHandler)
}


var viewModels = module {
    viewModelOf(::ExplorerViewModel)
    viewModelOf(::HomeViewModel)
}
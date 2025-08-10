package com.subhranil.lunarexplorer.di

import com.subhranil.lunarexplorer.settings.AppSettings
import org.koin.dsl.module

var singletons = module {
    single { AppSettings() }
}

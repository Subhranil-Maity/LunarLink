package com.subhranil.lunarexplorer

import android.app.Application
import com.subhranil.lunarexplorer.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LunarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LunarApp)
            modules(appModule)
        }
    }
}

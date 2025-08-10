package com.subhranil.lunarexplorer

import android.app.Application
import com.subhranil.lunarexplorer.di.singletons
import com.subhranil.lunarexplorer.di.viewModels
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyApp)
            modules(singletons)
            modules(viewModels)
        }
    }
}
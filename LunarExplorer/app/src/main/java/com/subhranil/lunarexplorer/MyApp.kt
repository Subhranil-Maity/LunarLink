package com.subhranil.lunarexplorer

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.subhranil.lunarexplorer.di.singletons
import com.subhranil.lunarexplorer.di.viewModels
import com.subhranil.lunarexplorer.services.ConnectionManagerService
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
        val intent = Intent(this, ConnectionManagerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
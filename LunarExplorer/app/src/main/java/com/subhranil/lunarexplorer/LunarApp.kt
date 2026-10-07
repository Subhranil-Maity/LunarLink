package com.subhranil.lunarexplorer

import android.app.Application
import com.subhranil.lunarexplorer.data.download.APP_SCOPE
import com.subhranil.lunarexplorer.data.download.DownloadRepository
import com.subhranil.lunarexplorer.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

class LunarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LunarApp)
            modules(appModule)
        }
        // Downloads that were running when the process died go back to the queue and resume.
        get<CoroutineScope>(named(APP_SCOPE)).launch { get<DownloadRepository>().recoverInterrupted() }
    }
}

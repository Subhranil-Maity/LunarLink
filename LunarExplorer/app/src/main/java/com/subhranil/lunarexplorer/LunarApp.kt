package com.subhranil.lunarexplorer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.subhranil.lunarexplorer.data.download.APP_SCOPE
import com.subhranil.lunarexplorer.data.download.DownloadRepository
import com.subhranil.lunarexplorer.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named

class LunarApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LunarApp)
            modules(appModule)
        }
        // Downloads that were running when the process died go back to the queue and resume.
        get<CoroutineScope>(named(APP_SCOPE)).launch { get<DownloadRepository>().recoverInterrupted() }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = get()
}

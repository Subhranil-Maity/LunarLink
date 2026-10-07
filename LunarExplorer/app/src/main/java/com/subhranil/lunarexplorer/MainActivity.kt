package com.subhranil.lunarexplorer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.data.settings.AppSettings
import com.subhranil.lunarexplorer.data.settings.SettingsRepository
import com.subhranil.lunarexplorer.ui.LocalHapticsEnabled
import com.subhranil.lunarexplorer.ui.navigation.AppNavigation
import com.subhranil.lunarexplorer.ui.theme.LunarTheme
import com.subhranil.lunarexplorer.ui.theme.isDarkTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainViewModel(settings: SettingsRepository) : ViewModel() {
    /** `null` until the first value is read; the splash screen stays up until then. */
    val settings: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()
    private val openDownloads = Channel<Unit>(Channel.CONFLATED)
    private val openDownloadsRequests = openDownloads.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // On recreation the intent is the same one that was already handled.
        if (savedInstanceState == null) handleIntent(intent)
        splash.setKeepOnScreenCondition { viewModel.settings.value == null }
        enableEdgeToEdge()

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            val dark = isDarkTheme(current.theme)
            LaunchedEffect(dark) {
                // Follow the in-app theme, not just the system one, for status/navigation bar icons.
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LunarTheme(themeMode = current.theme, dynamicColor = current.dynamicColor) {
                CompositionLocalProvider(LocalHapticsEnabled provides current.hapticsEnabled) {
                    AppNavigation(openDownloads = openDownloadsRequests)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) openDownloads.trySend(Unit)
    }

    companion object {
        /** Set by download notifications to open the Downloads screen. */
        const val EXTRA_OPEN_DOWNLOADS = "com.subhranil.lunarexplorer.extra.OPEN_DOWNLOADS"
    }
}

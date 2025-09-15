package com.subhranil.lunarexplorer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.subhranil.lunarexplorer.explorer.ExplorerScreen
import com.subhranil.lunarexplorer.home.HomeScreen
import com.subhranil.lunarexplorer.io.saveBytesToDownloadsWithGuessedMime
import com.subhranil.lunarexplorer.lunarlink.LunarServerRepo
import com.subhranil.lunarexplorer.manager.DownloadManager
import com.subhranil.lunarexplorer.manager.task.DownloadTask
import com.subhranil.lunarexplorer.navigation.NavDestinations
import com.subhranil.lunarexplorer.navigation.RootNavigation
import com.subhranil.lunarexplorer.navigation.RootNavigationViewModel
import com.subhranil.lunarexplorer.settings.SettingsUi
import com.subhranil.lunarexplorer.ui.theme.LunarExplorerTheme
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File
import kotlin.math.floor

class MainActivity : ComponentActivity() {
    private val rootNavViewModel: RootNavigationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            LunarExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RootNavigation(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        viewModel = rootNavViewModel
                    )
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link intents when the app is already open
        handleDeepLink(intent)
    }
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "lunar_explorer" && uri.host == "deeplink") {
            // Parse the path to determine the destination
            when (uri.path) {
                "/downloads" -> {
                    rootNavViewModel.navigateTo(NavDestinations.DownloadsScreenDestination)
                }
            }
        }
    }
}


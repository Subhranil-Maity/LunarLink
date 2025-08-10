package com.subhranil.lunarexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.subhranil.lunarexplorer.explorer.ExplorerScreen
import com.subhranil.lunarexplorer.lunarlink.LunarServerRepo
import com.subhranil.lunarexplorer.repository.FileSystemRepository
import com.subhranil.lunarexplorer.settings.SettingsUi
import com.subhranil.lunarexplorer.ui.theme.LunarExplorerTheme
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LunarExplorerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    SettingsUi(
//                        modifier = Modifier
//                            .padding(innerPadding)
//                            .fillMaxSize()
//                    )
                    ExplorerScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
//                    var data by remember { mutableStateOf("") }
//                    Text(data, style = MaterialTheme.typography.titleLarge)
//                    val context = LocalContext.current
//                    val client = koinInject<HttpClient>()
//                    LaunchedEffect(Unit) {
//                        val fs: FileSystemRepository = LunarServerRepo(
//                            client = client,
//                            baseUrl = "http://192.168.251.178:8080",
//                            userName = "power",
//                            pass = "power",
//                        )
//                        fs.connect()
//                        fs.getMounts().onSuccess { dat ->
//                            data = dat.drives[0].name
//                        }
//                    }
                }

            }
        }
    }
}


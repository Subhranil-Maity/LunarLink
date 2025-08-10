package com.subhranil.lunarexplorer.settings

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.subhranil.lunarexplorer.settings.sub.AppearanceSettingPage
import com.subhranil.lunarexplorer.settings.sub.AppearanceSettings
import com.subhranil.lunarexplorer.settings.sub.DummySettingPage
import com.subhranil.lunarexplorer.settings.sub.DummySettingSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsUi(
    modifier: Modifier = Modifier,
    appSettings: AppSettings = koinInject<AppSettings>(),
    ) {
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<SettingGroup>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    NavigableListDetailPaneScaffold(
        modifier = modifier,
        navigator = scaffoldNavigator,
        listPane = {
            AnimatedPane {
                SettingListPage(
                    appSettings = appSettings,
                    onItemClick = { item ->
                        // Navigate to the detail pane with the passed item
                        scope.launch {
                            scaffoldNavigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail,
                                item
                            )
                        }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                // Show the detail pane content if selected item is available
                scaffoldNavigator.currentDestination?.contentKey?.let { setting ->
                    when (setting) {
                        is AppearanceSettings -> {
                            AppearanceSettingPage(setting = appSettings.appearanceSettings){
                                appSettings.appearanceSettings = it
                            }
                        }

                        is DummySettingSettings -> {
                            DummySettingPage(setting = appSettings.dummySettingSettings) { updatedSetting ->
                                appSettings.dummySettingSettings = updatedSetting
                            }
                        }

                        else -> {
                            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "⚠ Please select the setting page.",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

package com.subhranil.lunarexplorer.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.subhranil.lunarexplorer.MainViewModel
import com.subhranil.lunarexplorer.data.api.ApiFactory
import com.subhranil.lunarexplorer.data.api.LunarApi
import com.subhranil.lunarexplorer.data.api.createHttpClient
import com.subhranil.lunarexplorer.data.servers.KeystorePasswordCipher
import com.subhranil.lunarexplorer.data.servers.PasswordCipher
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.data.session.ServerProbe
import com.subhranil.lunarexplorer.data.session.SessionManager
import com.subhranil.lunarexplorer.data.settings.SettingsRepository
import com.subhranil.lunarexplorer.ui.explorer.ExplorerViewModel
import com.subhranil.lunarexplorer.ui.home.HomeViewModel
import com.subhranil.lunarexplorer.ui.settings.SettingsViewModel
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val SERVERS_STORE = named("servers")
private val SETTINGS_STORE = named("settings")

val appModule = module {
    single { createHttpClient() }
    single {
        val client = get<HttpClient>()
        ApiFactory { baseUrl -> LunarApi(client, baseUrl) }
    }

    // The server store is excluded from backup (see res/xml/data_extraction_rules.xml).
    single(SERVERS_STORE) {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("servers") }
    }
    single(SETTINGS_STORE) {
        PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("settings") }
    }

    single<PasswordCipher> { KeystorePasswordCipher() }
    single { ServerRepository(get(SERVERS_STORE), get()) }
    single { SettingsRepository(get(SETTINGS_STORE)) }
    single { SessionManager(get(), get()) }
    single { ServerProbe(get()) }

    viewModelOf(::MainViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { (serverId: String) -> ExplorerViewModel(serverId, get(), get()) }
}

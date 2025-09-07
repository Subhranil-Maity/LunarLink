package com.subhranil.lunarexplorer.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.ExplorerConfig
import com.subhranil.lunarexplorer.GlobalStateHandler
import com.subhranil.lunarexplorer.home.models.LunarDeviceType
import com.subhranil.lunarexplorer.home.models.LunarHost
import com.subhranil.lunarexplorer.home.models.LunarHostConnectionConfig
import com.subhranil.lunarexplorer.home.store.HomeStorage
import com.subhranil.lunarexplorer.lunarlink.isLunarHostAlive
import com.subhranil.lunarexplorer.settings.sub.DummySettingSettings
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class HomeViewModel(
    val storage: HomeStorage,
    val client: HttpClient,
    val globalStateHandler: GlobalStateHandler
) : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val scope = CoroutineScope(Dispatchers.Unconfined)
            // here separate scope is taken because I want in future other things here will also be parallely ran
            scope.launch {
                loadAndCheckLunarHosts()
            }
            addDummyHost()
        }
    }

    @OptIn(ExperimentalTime::class)
    fun addDummyHost() {
        val list = _state.value.listOfActiveLunarHosts.toMutableList()
        list.add(
            LunarHost(
                host = "127.0.0.1:6969",
                username = "Dummy",
                password = "Dummy",
                deviceType = LunarDeviceType.WINDOWS,
                osVersion = "1.0.0.1",
                deviceName = "Dummy Device",
                lastConnected = Clock.System.now()
            )
        )
        _state.update {
            it.copy(listOfActiveLunarHosts = list)
        }

    }

    suspend fun loadAndCheckLunarHosts() {
        storage.getSavedLunarHosts().forEach { hostConnectionConfig ->
            val list = _state.value.listOfActiveLunarHosts.toMutableList()
            isLunarHostAlive(hostConnectionConfig, client)?.let { host ->
                list.add(host)
                _state.update {
                    it.copy(listOfActiveLunarHosts = list)
                }
            }
        }
    }

    fun onAction(action: HomeActions) {
        when (action) {
            is HomeActions.OnAddServer -> onAddServer(action.host, action.username, action.password)
            HomeActions.OnDismissAddNewServerDialog -> onDismissAddNewServerDialog()
            is HomeActions.OnSelectServer -> onSelectServer(action.host)
            HomeActions.OnShowAddNewServerDialog -> onShowAddNewServerDialog()
        }
    }

    private fun onShowAddNewServerDialog() {
        _state.update {
            it.copy(
                showAddServerDialog = true
            )
        }
    }

    private fun onSelectServer(host: LunarHost) {
        globalStateHandler.config = ExplorerConfig(
            host = host.host,
            username = host.username,
            password = host.password
        )
    }

    private fun onDismissAddNewServerDialog() {
        _state.update {
            it.copy(
                showAddServerDialog = false
            )
        }
    }

    private fun onAddServer(host: String, username: String, password: String) {
        viewModelScope.launch {
            val server = isLunarHostAlive(
                host = LunarHostConnectionConfig(
                    host = host,
                    username = username,
                    password = password,
                    lastConnected = 0L
                ),
                client = get().get()
            )
            server?.let{ it ->
                val list = _state.value.listOfActiveLunarHosts.toMutableList()
                list.add(it)
                _state.update {st ->
                    st.copy(listOfActiveLunarHosts = list)
                }
                storage.saveLunarHosts(list.map { host ->
                    LunarHostConnectionConfig(
                        host = host.host,
                        username = host.username,
                        password = host.password,
                        lastConnected = System.currentTimeMillis() / 1000
                    )
                })
            }
        }
    }
}
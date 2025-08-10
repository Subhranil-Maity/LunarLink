package com.subhranil.lunarexplorer.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.lunarlink.LunarServerRepo
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.repository.FileSystemRepository
import com.subhranil.lunarexplorer.result.FileSystemErrors
import com.subhranil.lunarexplorer.result.LunarResult
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get

class ExplorerViewModel: ViewModel() {
    private val _state = MutableStateFlow(ExplorerState())
    val state = _state.asStateFlow()
    val fs: FileSystemRepository = LunarServerRepo(
        client = get().get<HttpClient>(),
        baseUrl = "http://192.168.251.178:8080",
        userName = "power",
        pass = "power",
    )
    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isConnected = false
                )
            }
            val connect = fs.connect()
            when (connect){
                is LunarResult.Error<Unit, FileSystemErrors> -> {
                    _state.update {
                        it.copy(
                            error = connect.error.toString()
                        )
                    }
                    return@launch
                }
                is LunarResult.Success<*, *> -> {}
            }
            val mounts = fs.getMounts()
            when(mounts){
                is LunarResult.Error<Mounts, FileSystemErrors>   -> {
                    _state.update {
                        it.copy(
                            error = mounts.error.toString()
                        )
                    }
                    return@launch
                }
                is LunarResult.Success<Mounts, FileSystemErrors> -> {
                    _state.update {
                        it.copy(
                            mounts = mounts.data
                        )
                    }
                }
            }

            _state.update {
                it.copy(
                    isConnected = true
                )
            }
        }
    }

    fun onAction(action: ExplorerActions) {
        when (action) {
            ExplorerActions.Refresh -> onRefresh()
            is ExplorerActions.GoToPath -> onGoToPath(action.path)
        }
    }

    private fun onGoToPath(path: String) {
        if (!_state.value.pathHistory.isEmpty()){
            if (_state.value.pathHistory.last() == path){
                return
            }
        }
        val list = _state.value.pathHistory.toMutableList()
        list.add(path)
        _state.update {
            it.copy(
                pathHistory = list
            )
        }
    }

    private fun onRefresh() {

    }

    private inline fun withRefreshing(block: () -> Unit) {
        _state.update {
            it.copy(
                isLoading = true
            )
        }
        block()
        _state.update {
            it.copy(
                isLoading = false
            )
        }
    }
}
package com.subhranil.lunarexplorer.explorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.lunarlink.LunarServerRepo
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.repository.FileSystemRepository
import com.subhranil.lunarexplorer.result.FileSystemErrors
import com.subhranil.lunarexplorer.result.LunarResult
import com.subhranil.lunarexplorer.result.RootError
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get

class ExplorerViewModel : ViewModel() {
    private val _state = MutableStateFlow(ExplorerState())
    val state = _state.asStateFlow()
    var pendingNameRequest by mutableStateOf<PendingNameRequest?>(null)
        private set
    val fs: FileSystemRepository = LunarServerRepo(
        client = get().get<HttpClient>(),
        baseUrl = "http://192.168.54.178:8080",
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
            when (connect) {
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
            when (mounts) {
                is LunarResult.Error<Mounts, FileSystemErrors> -> {
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
            ExplorerActions.OnBackPressed -> onBackPressed()
            is ExplorerActions.OnActionButtonPressed -> onActionButtonPressed(action.actionType)
        }
    }

    private fun onActionButtonPressed(actionType: AppBarActionEnum) {
        when (actionType) {
            AppBarActionEnum.CREATE_FILE -> {
                val path = _state.value.pathHistory.lastOrNull() ?: return
                requestNameForFile(path)
            }

            AppBarActionEnum.CREATE_FOLDER -> {
                val path = _state.value.pathHistory.lastOrNull() ?: return
                requestNameForFolder(path)
            }

            else -> {}
        }

    }


    fun requestNameForFile(basePath: String) {
        pendingNameRequest = PendingNameRequest(
            type = NameRequestType.FILE,
            basePath = basePath
        ) { fileName ->
            createFsFile("${basePath}\\$fileName")
        }
    }

    fun requestNameForFolder(basePath: String) {
        pendingNameRequest = PendingNameRequest(
            type = NameRequestType.FOLDER,
            basePath = basePath
        ) { folderName ->
            createFsFolder("${basePath}\\$folderName")
        }
    }

    fun confirmName(name: String) {
        pendingNameRequest?.onConfirm?.invoke(name)
        pendingNameRequest = null
    }

    fun dismissNameRequest() {
        pendingNameRequest = null
    }

    private fun createFsFile(path: String) {
        viewModelScope.launch {

            withRefreshing {
                withHandledError(fs.createFile(path)) { data ->
                    val list = _state.value.currentFolderContent.toMutableList()
                    list.add(data)
                    _state.update {
                        it.copy(
                            currentFolderContent = list
                        )
                    }
                }
            }
        }
    }

    private fun createFsFolder(path: String) {
        viewModelScope.launch {
            withRefreshing {
                withHandledError(fs.createFolder(path)) { data ->
                    val list = _state.value.currentFolderContent.toMutableList()
                    list.add(data)
                    _state.update {
                        it.copy(
                            currentFolderContent = list
                        )
                    }
                }
            }
        }
    }

    private fun onBackPressed() {
        if (!_state.value.pathHistory.isEmpty()) {
            val list = _state.value.pathHistory.toMutableList()
            list.removeAt(list.lastIndex)
            _state.update {
                it.copy(
                    pathHistory = list
                )
            }
            onRefresh()
        }
    }

    private fun onGoToPath(path: String) {
        if (!_state.value.pathHistory.isEmpty()) {
            if (_state.value.pathHistory.last() == path) {
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
        onRefresh()
    }

    fun onRefresh() {
        // TODO: Add full path history and its content in state to avoid refetching
        if (_state.value.pathHistory.isEmpty()) {
            return
        }
        viewModelScope.launch {
            withRefreshing {
                withHandledError(fs.getFolderContent(_state.value.pathHistory.last())) { content ->
                    _state.update { it ->
                        it.copy(
                            currentFolderContent = content.entries.sortedBy { ent -> ent.entryType },
                            currentFolderInfo = content.metaData
                        )
                    }
                }
            }
        }

    }

    private inline fun <D, E : RootError> withHandledError(
        result: LunarResult<D, E>,
        block: (data: D) -> Unit
    ) {
        if (result.isError()) {
            _state.update {
                it.copy(
                    error = result.getErrorOrNull().toString()
                )
            }
        } else {
            block(result.successOrNull()!!)
        }
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

    fun consumeError() {
        _state.update {
            it.copy(
                error = null
            )
        }
    }
}
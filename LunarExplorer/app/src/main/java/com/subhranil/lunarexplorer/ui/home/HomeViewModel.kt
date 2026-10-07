package com.subhranil.lunarexplorer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.ServerInfo
import com.subhranil.lunarexplorer.data.discovery.DiscoveredServer
import com.subhranil.lunarexplorer.data.discovery.ServerDiscovery
import com.subhranil.lunarexplorer.data.servers.DuplicateServerException
import com.subhranil.lunarexplorer.data.servers.SavedServer
import com.subhranil.lunarexplorer.data.servers.ServerDraft
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.data.servers.normalizeBaseUrl
import com.subhranil.lunarexplorer.data.servers.splitBaseUrl
import com.subhranil.lunarexplorer.data.session.ServerProbe
import com.subhranil.lunarexplorer.ui.UiText
import com.subhranil.lunarexplorer.ui.toUiText
import com.subhranil.lunarexplorer.ui.uiText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ServerStatus { CHECKING, ONLINE, OFFLINE }

data class ServerItem(val server: SavedServer, val status: ServerStatus)

sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Success(val info: ServerInfo) : TestState
    data class Failed(val message: UiText) : TestState
}

data class EditorState(
    val editingId: String? = null,
    val host: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val test: TestState = TestState.Idle,
    val saving: Boolean = false,
    val error: UiText? = null,
) {
    val baseUrl: String? get() = normalizeBaseUrl(host, port)
    val canTest: Boolean get() = baseUrl != null && username.isNotBlank() && test != TestState.Testing
    /** Saving needs a successful test of exactly the current input. */
    val canSave: Boolean get() = test is TestState.Success && !saving
}

data class HomeUiState(
    val isLoaded: Boolean = false,
    val servers: List<ServerItem> = emptyList(),
    /** Servers announced on the local network that aren't saved yet. */
    val nearby: List<DiscoveredServer> = emptyList(),
    val isRefreshing: Boolean = false,
    val editor: EditorState? = null,
    val pendingDelete: SavedServer? = null,
)

private data class Overlay(
    val isRefreshing: Boolean = false,
    val editor: EditorState? = null,
    val pendingDelete: SavedServer? = null,
)

class HomeViewModel(
    private val repository: ServerRepository,
    private val probe: ServerProbe,
    discovery: ServerDiscovery,
) : ViewModel() {

    private val statuses = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    private val overlay = MutableStateFlow(Overlay())
    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    // Discovery runs only while the Home screen collects the state (plus 5 s), so it costs nothing elsewhere.
    val state: StateFlow<HomeUiState> =
        combine(repository.servers, statuses, overlay, discovery.servers()) { servers, statusMap, o, found ->
            val saved = servers.map { it.baseUrl }.toSet()
            HomeUiState(
                isLoaded = true,
                servers = servers
                    .sortedByDescending { it.lastConnectedAt ?: 0 }
                    .map { ServerItem(it, statusMap[it.id] ?: ServerStatus.CHECKING) },
                nearby = found.filter { it.baseUrl != null && it.baseUrl !in saved },
                isRefreshing = o.isRefreshing,
                editor = o.editor,
                pendingDelete = o.pendingDelete,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Check every server once, and any server added later.
        viewModelScope.launch {
            repository.servers.collect { servers ->
                val unchecked = servers.filter { it.id !in statuses.value }
                statuses.update { map -> map + unchecked.map { it.id to ServerStatus.CHECKING } }
                unchecked.forEach { server -> launch { check(server) } }
            }
        }
    }

    fun refresh() {
        if (overlay.value.isRefreshing) return
        viewModelScope.launch {
            overlay.update { it.copy(isRefreshing = true) }
            coroutineScope {
                repository.servers.first().map { async { check(it) } }.awaitAll()
            }
            overlay.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun check(server: SavedServer) {
        statuses.update { it + (server.id to ServerStatus.CHECKING) }
        val status = when (probe.info(server.baseUrl)) {
            is ApiResult.Success -> ServerStatus.ONLINE
            is ApiResult.Failure -> ServerStatus.OFFLINE
        }
        statuses.update { it + (server.id to status) }
    }

    // ---------- editor ----------

    fun openAddServer() = overlay.update { it.copy(editor = EditorState()) }

    /** Opens the editor with a nearby server's address filled in. */
    fun addNearby(server: DiscoveredServer) = overlay.update {
        it.copy(editor = EditorState(host = server.host, port = server.port.toString()))
    }

    fun openEditServer(server: SavedServer) {
        viewModelScope.launch {
            val creds = repository.credentials(server.id) ?: return@launch
            val (host, port) = splitBaseUrl(creds.baseUrl)
            overlay.update {
                it.copy(
                    editor = EditorState(
                        editingId = server.id,
                        host = host,
                        port = port,
                        username = creds.username,
                        password = creds.password,
                    )
                )
            }
        }
    }

    fun dismissEditor() = overlay.update { it.copy(editor = null) }

    fun onHostChange(value: String) = editInput { copy(host = value) }

    fun onPortChange(value: String) {
        if (value.length <= 5 && value.all { it.isDigit() }) editInput { copy(port = value) }
    }

    fun onUsernameChange(value: String) = editInput { copy(username = value) }

    fun onPasswordChange(value: String) = editInput { copy(password = value) }

    fun togglePasswordVisible() = updateEditor { copy(passwordVisible = !passwordVisible) }

    /** Any input change invalidates a previous successful test. */
    private fun editInput(change: EditorState.() -> EditorState) =
        updateEditor { change().copy(test = TestState.Idle, error = null) }

    private fun updateEditor(change: EditorState.() -> EditorState) =
        overlay.update { o -> o.copy(editor = o.editor?.change()) }

    fun testConnection() {
        val editor = overlay.value.editor ?: return
        val baseUrl = editor.baseUrl ?: return
        updateEditor { copy(test = TestState.Testing, error = null) }
        viewModelScope.launch {
            val result = probe.test(baseUrl, editor.username.trim(), editor.password)
            // Ignore the result if the user changed the input meanwhile.
            if (overlay.value.editor?.test != TestState.Testing) return@launch
            updateEditor {
                copy(
                    test = when (result) {
                        is ApiResult.Success -> TestState.Success(result.value)
                        is ApiResult.Failure -> TestState.Failed(result.error.toUiText())
                    }
                )
            }
        }
    }

    fun saveServer() {
        val editor = overlay.value.editor ?: return
        val info = (editor.test as? TestState.Success)?.info ?: run {
            updateEditor { copy(error = uiText(R.string.editor_test_required)) }
            return
        }
        val baseUrl = editor.baseUrl ?: return
        val draft = ServerDraft(info.name, baseUrl, editor.username.trim(), editor.password, info.osType)
        updateEditor { copy(saving = true) }
        viewModelScope.launch {
            try {
                if (editor.editingId == null) {
                    repository.add(draft)
                } else {
                    repository.edit(editor.editingId, draft)
                    // Re-check, the address may have changed.
                    repository.get(editor.editingId)?.let { launch { check(it) } }
                }
                overlay.update { it.copy(editor = null) }
            } catch (e: DuplicateServerException) {
                updateEditor { copy(saving = false, error = uiText(R.string.editor_duplicate)) }
            }
        }
    }

    // ---------- delete ----------

    fun requestDelete(server: SavedServer) = overlay.update { it.copy(pendingDelete = server) }

    fun dismissDelete() = overlay.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val server = overlay.value.pendingDelete ?: return
        overlay.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            repository.delete(server.id)
            statuses.update { it - server.id }
            _messages.send(uiText(R.string.home_server_removed))
        }
    }
}

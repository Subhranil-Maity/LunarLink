package com.subhranil.lunarexplorer.data.session

import com.subhranil.lunarexplorer.data.api.ApiFactory
import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.LunarError
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionState {
    data object Disconnected : SessionState
    data class Connecting(val serverId: String) : SessionState
    data class Connected(val session: ServerSession) : SessionState
    data class Failed(val serverId: String, val error: LunarError) : SessionState
}

/** Owns the single active server session. */
class SessionManager(
    private val servers: ServerRepository,
    private val apis: ApiFactory,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Returns the session for [serverId], connecting (and replacing any other session) if needed. */
    suspend fun connect(serverId: String): ApiResult<ServerSession> = mutex.withLock {
        (_state.value as? SessionState.Connected)?.session?.let {
            if (it.serverId == serverId) return ApiResult.Success(it)
        }
        _state.value = SessionState.Connecting(serverId)
        val creds = servers.credentials(serverId)
            ?: return fail(serverId, LunarError.Unexpected("Server not found"))
        when (val result = ServerSession.open(serverId, apis.create(creds.baseUrl), creds.username, creds.password, clock)) {
            is ApiResult.Failure -> fail(serverId, result.error)
            is ApiResult.Success -> {
                _state.value = SessionState.Connected(result.value)
                servers.markConnected(serverId, clock(), result.value.info.name, result.value.info.osType)
                result
            }
        }
    }

    /** The connected session for [serverId], or `null` when that server isn't the one connected. */
    fun current(serverId: String): ServerSession? =
        (_state.value as? SessionState.Connected)?.session?.takeIf { it.serverId == serverId }

    fun disconnect(serverId: String? = null) {
        val current = _state.value
        val currentId = when (current) {
            is SessionState.Connected -> current.session.serverId
            is SessionState.Connecting -> current.serverId
            is SessionState.Failed -> current.serverId
            SessionState.Disconnected -> return
        }
        if (serverId == null || serverId == currentId) _state.value = SessionState.Disconnected
    }

    private fun fail(serverId: String, error: LunarError): ApiResult.Failure {
        _state.value = SessionState.Failed(serverId, error)
        return ApiResult.Failure(error)
    }
}

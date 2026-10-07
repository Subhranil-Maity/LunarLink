package com.subhranil.lunarexplorer.data.download

import com.subhranil.lunarexplorer.data.api.ApiFactory
import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.LunarError
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.data.session.ServerSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sessions used by downloads. Separate from [com.subhranil.lunarexplorer.data.session.SessionManager]
 * because the explorer disconnects its session when the screen closes, while downloads keep going.
 */
class DownloadSessions(
    private val servers: ServerRepository,
    private val apis: ApiFactory,
) {
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, ServerSession>()

    suspend fun get(serverId: String): ApiResult<ServerSession> = mutex.withLock {
        sessions[serverId]?.let { return ApiResult.Success(it) }
        val creds = servers.credentials(serverId)
            ?: return ApiResult.Failure(LunarError.Unexpected(SERVER_REMOVED))
        val result = ServerSession.open(serverId, apis.create(creds.baseUrl), creds.username, creds.password)
        if (result is ApiResult.Success) sessions[serverId] = result.value
        result
    }

    /** Forgets all sessions, e.g. when the download run ends, so edited credentials are picked up next time. */
    suspend fun clear() = mutex.withLock { sessions.clear() }

    companion object {
        const val SERVER_REMOVED = "The server was removed from this device"
    }
}

package com.subhranil.lunarexplorer.data.session

import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.Entry
import com.subhranil.lunarexplorer.data.api.ErrorCode
import com.subhranil.lunarexplorer.data.api.ListQuery
import com.subhranil.lunarexplorer.data.api.Listing
import com.subhranil.lunarexplorer.data.api.LunarApi
import com.subhranil.lunarexplorer.data.api.LunarError
import com.subhranil.lunarexplorer.data.api.Root
import com.subhranil.lunarexplorer.data.api.ServerInfo
import com.subhranil.lunarexplorer.data.api.UserInfo
import com.subhranil.lunarexplorer.data.api.hasCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A logged-in connection to one server. Keeps the token and transparently logs in again once
 * when it expires, so callers never see `TOKEN_EXPIRED` unless re-login itself fails.
 */
class ServerSession private constructor(
    val serverId: String,
    val info: ServerInfo,
    private val api: LunarApi,
    private val username: String,
    private val password: String,
    private val clock: () -> Long,
    initialToken: String?,
    initialExpiresAt: Long?,
    user: UserInfo?,
) {
    private val mutex = Mutex()
    private var token: String? = initialToken
    private var expiresAt: Long? = initialExpiresAt

    /** The logged-in user, or `null` when the server doesn't require authentication. */
    var user: UserInfo? = user
        private set

    suspend fun roots(): ApiResult<List<Root>> = authed { api.roots(it) }

    suspend fun list(path: String, query: ListQuery): ApiResult<Listing> = authed { api.list(it, path, query) }

    suspend fun stat(path: String, follow: Boolean = false): ApiResult<Entry> = authed { api.stat(it, path, follow) }

    suspend fun createFile(path: String): ApiResult<Entry> = authed { api.createFile(it, path) }

    suspend fun createDir(path: String): ApiResult<Entry> = authed { api.createDir(it, path) }

    suspend fun copy(from: String, to: String, overwrite: Boolean): ApiResult<Entry> =
        authed { api.copy(it, from, to, overwrite) }

    suspend fun move(from: String, to: String, overwrite: Boolean): ApiResult<Entry> =
        authed { api.move(it, from, to, overwrite) }

    suspend fun delete(path: String, permanent: Boolean, recursive: Boolean): ApiResult<Unit> =
        authed { api.delete(it, path, permanent, recursive) }

    private suspend fun <T> authed(block: suspend (token: String?) -> ApiResult<T>): ApiResult<T> {
        if (!info.authRequired) return block(null)
        val current = mutex.withLock {
            val exp = expiresAt
            if (token == null || (exp != null && exp - REFRESH_MARGIN_SECONDS <= clock())) {
                val relogin = login()
                if (relogin is ApiResult.Failure) return relogin
            }
            token
        }
        val result = block(current)
        if (result is ApiResult.Failure && result.error.isAuthExpiry()) {
            val retryToken = mutex.withLock {
                // Another caller may have refreshed the token already.
                if (token != current) token else when (val relogin = login()) {
                    is ApiResult.Failure -> return relogin
                    is ApiResult.Success -> token
                }
            }
            return block(retryToken)
        }
        return result
    }

    /** Must be called with [mutex] held. */
    private suspend fun login(): ApiResult<Unit> = when (val r = api.login(username, password)) {
        is ApiResult.Success -> {
            token = r.value.token
            expiresAt = r.value.expiresAt
            user = r.value.user
            ApiResult.Success(Unit)
        }
        is ApiResult.Failure -> r
    }

    companion object {
        private const val REFRESH_MARGIN_SECONDS = 30

        private fun LunarError.isAuthExpiry() =
            hasCode(ErrorCode.TOKEN_EXPIRED) || hasCode(ErrorCode.INVALID_TOKEN)

        /** Fetches server info and logs in when the server requires it. */
        suspend fun open(
            serverId: String,
            api: LunarApi,
            username: String,
            password: String,
            clock: () -> Long = { System.currentTimeMillis() / 1000 },
        ): ApiResult<ServerSession> {
            val info = when (val r = api.info()) {
                is ApiResult.Success -> r.value
                is ApiResult.Failure -> return r
            }
            if (!info.authRequired) {
                return ApiResult.Success(
                    ServerSession(serverId, info, api, username, password, clock, null, null, null)
                )
            }
            return when (val r = api.login(username, password)) {
                is ApiResult.Failure -> r
                is ApiResult.Success -> ApiResult.Success(
                    ServerSession(
                        serverId, info, api, username, password, clock,
                        r.value.token, r.value.expiresAt, r.value.user,
                    )
                )
            }
        }
    }
}

package com.subhranil.lunarexplorer.data.session

import com.subhranil.lunarexplorer.data.api.ApiFactory
import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.ServerInfo

/** One-off checks used by the home screen and the server editor. */
class ServerProbe(private val apis: ApiFactory) {

    /** Reachability check: fetches public server info. */
    suspend fun info(baseUrl: String): ApiResult<ServerInfo> = apis.create(baseUrl).info()

    /** Full check: server info plus a real login with the given credentials. */
    suspend fun test(baseUrl: String, username: String, password: String): ApiResult<ServerInfo> {
        val api = apis.create(baseUrl)
        val info = when (val r = api.info()) {
            is ApiResult.Success -> r.value
            is ApiResult.Failure -> return r
        }
        if (!info.authRequired) return ApiResult.Success(info)
        return when (val r = api.login(username, password)) {
            is ApiResult.Success -> ApiResult.Success(info)
            is ApiResult.Failure -> r
        }
    }
}

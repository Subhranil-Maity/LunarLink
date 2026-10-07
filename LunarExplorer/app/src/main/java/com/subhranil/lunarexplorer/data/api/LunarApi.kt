package com.subhranil.lunarexplorer.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * An open download response, only valid inside [LunarApi.download]'s callback. [status] is 200 (whole
 * file), 206 (from the requested offset) or 416 (the offset is at or past the end of the file).
 */
class DownloadResponse(val status: Int, val contentLength: Long?, val body: ByteReadChannel)

/** Thrown from a download callback when writing to local storage fails; never mapped to a network error. */
class LocalWriteException(cause: Throwable) : RuntimeException(cause.message, cause)

/**
 * Thin, stateless wrapper around the LunarLink HTTP API. Authentication state lives in
 * [com.subhranil.lunarexplorer.data.session.ServerSession]; every call takes the token explicitly.
 */
/** Creates an API client for a server's base URL. */
fun interface ApiFactory {
    fun create(baseUrl: String): LunarApi
}

class LunarApi(private val client: HttpClient, baseUrl: String) {
    val baseUrl: String = baseUrl.trimEnd('/')

    suspend fun info(): ApiResult<ServerInfo> = call { client.get("$baseUrl/api/info") }

    suspend fun login(username: String, password: String): ApiResult<LoginResponse> = call {
        client.post("$baseUrl/api/auth/login") { json(LoginRequest(username, password)) }
    }

    suspend fun me(token: String?): ApiResult<UserInfo> = call {
        client.get("$baseUrl/api/auth/me") { auth(token) }
    }

    suspend fun roots(token: String?): ApiResult<List<Root>> =
        call<RootsResponse> { client.get("$baseUrl/api/v1/fs/roots") { auth(token) } }.map { it.roots }

    suspend fun stat(token: String?, path: String, follow: Boolean = false): ApiResult<Entry> = call {
        client.get("$baseUrl/api/v1/fs/stat") {
            auth(token)
            parameter("path", path)
            parameter("follow", follow)
        }
    }

    suspend fun list(token: String?, path: String, query: ListQuery = ListQuery()): ApiResult<Listing> = call {
        client.get("$baseUrl/api/v1/fs/list") {
            auth(token)
            parameter("path", path)
            parameter("offset", query.offset)
            query.limit?.let { parameter("limit", it) }
            parameter("sort", query.sort.name.lowercase())
            parameter("order", query.order.name.lowercase())
            parameter("dirs_first", query.dirsFirst)
            parameter("show_hidden", query.showHidden)
        }
    }

    suspend fun search(token: String?, path: String, query: String, showHidden: Boolean, limit: Int? = null): ApiResult<SearchResult> =
        call {
            client.get("$baseUrl/api/v1/fs/search") {
                auth(token)
                parameter("path", path)
                parameter("query", query)
                parameter("show_hidden", showHidden)
                limit?.let { parameter("limit", it) }
            }
        }

    suspend fun createFile(token: String?, path: String): ApiResult<Entry> = call {
        client.post("$baseUrl/api/v1/fs/file") { auth(token); json(PathBody(path)) }
    }

    suspend fun createDir(token: String?, path: String, parents: Boolean = false): ApiResult<Entry> = call {
        client.post("$baseUrl/api/v1/fs/dir") { auth(token); json(DirBody(path, parents)) }
    }

    suspend fun copy(token: String?, from: String, to: String, overwrite: Boolean): ApiResult<Entry> = call {
        client.post("$baseUrl/api/v1/fs/copy") { auth(token); json(TransferBody(from, to, overwrite)) }
    }

    suspend fun move(token: String?, from: String, to: String, overwrite: Boolean): ApiResult<Entry> = call {
        client.post("$baseUrl/api/v1/fs/move") { auth(token); json(TransferBody(from, to, overwrite)) }
    }

    suspend fun delete(token: String?, path: String, permanent: Boolean, recursive: Boolean): ApiResult<Unit> =
        call {
            client.post("$baseUrl/api/v1/fs/delete") { auth(token); json(DeleteBody(path, permanent, recursive)) }
        }

    /**
     * Streams a file starting at [offset]. [consume] runs while the connection is open and must read
     * the body itself; it is only called for 200, 206 and 416 responses. Errors thrown by [consume]
     * other than network failures should be wrapped in [LocalWriteException] so they aren't reported
     * as the server being unreachable.
     */
    suspend fun download(
        token: String?,
        path: String,
        offset: Long,
        consume: suspend (DownloadResponse) -> Unit,
    ): ApiResult<Unit> = catching {
        client.prepareGet("$baseUrl/api/v1/fs/download") {
            auth(token)
            parameter("path", path)
            if (offset > 0) header(HttpHeaders.Range, "bytes=$offset-")
            // Large files take longer than the default request timeout; a stalled socket still times out.
            timeout { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
        }.execute { response ->
            val status = response.status
            if (status.isSuccess() || status == HttpStatusCode.RequestedRangeNotSatisfiable) {
                consume(DownloadResponse(status.value, response.contentLength(), response.bodyAsChannel()))
                ApiResult.Success(Unit)
            } else {
                ApiResult.Failure(parseError(response))
            }
        }
    }

    private fun HttpRequestBuilder.auth(token: String?) {
        token?.let { bearerAuth(it) }
    }

    private inline fun <reified B> HttpRequestBuilder.json(body: B) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun <reified T> call(request: () -> HttpResponse): ApiResult<T> = catching {
        val response = request()
        if (response.status.isSuccess()) {
            @Suppress("UNCHECKED_CAST")
            val value = if (T::class == Unit::class) Unit as T else response.body<T>()
            ApiResult.Success(value)
        } else {
            ApiResult.Failure(parseError(response))
        }
    }

    /** Maps transport and parsing exceptions to [LunarError]s. */
    private suspend inline fun <T> catching(block: () -> ApiResult<T>): ApiResult<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalWriteException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            ApiResult.Failure(LunarError.Timeout)
        } catch (e: ConnectTimeoutException) {
            ApiResult.Failure(LunarError.Timeout)
        } catch (e: SocketTimeoutException) {
            ApiResult.Failure(LunarError.Timeout)
        } catch (e: SerializationException) {
            ApiResult.Failure(LunarError.Unexpected("Invalid response: ${e.message}"))
        } catch (e: IOException) {
            ApiResult.Failure(LunarError.Unreachable)
        } catch (e: Exception) {
            ApiResult.Failure(LunarError.Unexpected(e.message ?: e::class.simpleName.orEmpty()))
        }
    }

    private suspend fun parseError(response: HttpResponse): LunarError {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val body = runCatching { LunarJson.decodeFromString<ErrorEnvelope>(text).error }.getOrNull()
        return if (body != null) {
            LunarError.Api(
                code = ErrorCode.parse(body.code),
                message = body.message,
                status = response.status.value,
                retryAfterSeconds = body.details?.get("retry_after_seconds")?.jsonPrimitive?.longOrNull,
            )
        } else {
            LunarError.Api(ErrorCode.UNKNOWN, text.ifBlank { response.status.description }, response.status.value)
        }
    }
}

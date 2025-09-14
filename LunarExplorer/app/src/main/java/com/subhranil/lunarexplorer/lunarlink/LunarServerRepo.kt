package com.subhranil.lunarexplorer.lunarlink

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.subhranil.lunarexplorer.home.models.LunarHost
import com.subhranil.lunarexplorer.home.models.LunarHostConnectionConfig
import com.subhranil.lunarexplorer.lunarlink.dto.ErrorDto
import com.subhranil.lunarexplorer.lunarlink.dto.FileData
import com.subhranil.lunarexplorer.lunarlink.dto.FolderContentDto
import com.subhranil.lunarexplorer.lunarlink.dto.InfoDto
import com.subhranil.lunarexplorer.lunarlink.dto.LoginRequestDto
import com.subhranil.lunarexplorer.lunarlink.dto.LoginResponseDto
import com.subhranil.lunarexplorer.lunarlink.dto.MetaDataDto
import com.subhranil.lunarexplorer.lunarlink.dto.MountsDto
import com.subhranil.lunarexplorer.lunarlink.dto.map
import com.subhranil.lunarexplorer.lunarlink.dto.toByteArrayUnsigned
import com.subhranil.lunarexplorer.models.FolderInfo
import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.result.FileSystemErrors
import com.subhranil.lunarexplorer.result.FileSystemResult
import com.subhranil.lunarexplorer.result.LunarResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext.get
import kotlin.collections.component1
import kotlin.collections.component2


suspend fun isLunarHostAlive(
    host: LunarHostConnectionConfig,
    client: HttpClient
): LunarHost? {
    return try {
        val response = client.request("${host.host}/api/get_info") {
            method = HttpMethod.Get
        }
        val data = Json.decodeFromString<InfoDto>(response.body())
        data.map(host)
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        null
    }
}

enum class LunarConnectionState {
    CONNECTED,
    DISCONNECTED,
    UNAUTHORISED
}

class LunarServerRepo(
    private val client: HttpClient,
    private val baseUrl: String,
    private val userName: String,
    private val pass: String,
) {
    private var token: String = ""

    @OptIn(InternalAPI::class)
    suspend fun connect(): FileSystemResult<Unit> {
        try {
            val dto = LoginRequestDto(
                username = this@LunarServerRepo.userName,
                password = this@LunarServerRepo.pass
            )
            val response = client.request("$baseUrl/api/auth/login") {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(dto))
            }
            when (response.status.value) {
                HttpStatusCode.OK.value -> {
                    val responseData = Json.decodeFromString<LoginResponseDto>(response.body())
                    token = responseData.token
                }

                HttpStatusCode.Unauthorized.value -> {
                    val errorData = Json.decodeFromString<ErrorDto>(response.body())
                }

                else -> {
                    return LunarResult.Error(FileSystemErrors.LOGIN_FAILED)
                }
            }
        } catch (e: Exception) {
            Log.d("RemoteServerRepo", e.message.toString())
            currentCoroutineContext().ensureActive()
            return LunarResult.Error(FileSystemErrors.CANNOT_CONNECT)
        }
        return LunarResult.Success(Unit)
    }

    private suspend inline fun <reified Dto, reified Response> getResponse(
        response: HttpResponse,
        onSuccess: (Dto) -> Response
    ): FileSystemResult<Response> {
        if (response.status.value == HttpStatusCode.Unauthorized.value) {
            return LunarResult.Error(FileSystemErrors.UNAUTHORISED)
        }
        if (response.status.value != HttpStatusCode.OK.value) {
            val errorData = Json.decodeFromString<ErrorDto>(response.body())
            return LunarResult.Error(getErrorResults(errorData))
        }
        val data = Json.decodeFromString<Dto>(response.body())
        return LunarResult.Success(onSuccess(data))

    }


    private suspend inline fun <reified Dto, reified Response> makeRequest(
        url: String,
        method: HttpMethod,
        parameters: Map<String, String> = emptyMap(),
        noinline customHeaders: HeadersBuilder.() -> Unit = {},
        onSuccess: (Dto) -> Response
    ): FileSystemResult<Response> {
        return try {
            val response = client.request(url) {
                headers(customHeaders)


                this.method = method
                bearerAuth(token = token)
                parameters.forEach { (key, value) ->
                    parameter(key, value)
                }
            }
            getResponse<Dto, Response>(response, onSuccess)
        } catch (e: SerializationException) {
            currentCoroutineContext().ensureActive()
            Log.d("RemoteServerRepo", e.message.toString())
            LunarResult.Error(FileSystemErrors.SERIALISATION_ERROR)
        } catch (e: JsonConvertException) {
            currentCoroutineContext().ensureActive()
            Log.d("RemoteServerRepo", e.message.toString())
            LunarResult.Error(FileSystemErrors.SERIALISATION_ERROR)
        } catch (e: HttpRequestTimeoutException) {
            currentCoroutineContext().ensureActive()
            Log.d("RemoteServerRepo", e.message.toString())
            LunarResult.Error(FileSystemErrors.CANNOT_CONNECT)
        } catch (e: Exception) {
            Log.d("RemoteServerRepo", e.message.toString())
            currentCoroutineContext().ensureActive()
            LunarResult.Error(FileSystemErrors.UNKNOWN_ERROR)
        }
    }

    private suspend fun requestNoBody(
        url: String,
        method: HttpMethod,
        parameters: Map<String, Any?> = emptyMap()
    ): FileSystemResult<Unit> {
        return try {
            val response = client.request(url) {
                this.method = method
                bearerAuth(token = token)
                parameters.forEach { (k, v) -> parameter(k, v) }
            }

            when (response.status) {
                HttpStatusCode.OK -> LunarResult.Success(Unit)
                HttpStatusCode.Unauthorized -> {
                    Toast.makeText(
                        get().get<Context>(),
                        "unauthorised not handled yet",
                        Toast.LENGTH_SHORT
                    ).show()
                    LunarResult.Error(FileSystemErrors.LOGIN_FAILED)
                }

                else -> {
                    val err = Json.decodeFromString<ErrorDto>(response.body())
                    Log.d("RemoteServerRepo", err.message)
                    LunarResult.Error(getErrorResults(err))
                }
            }
        } catch (e: Exception) {
            Log.d("RemoteServerRepo", e.message.toString())
            currentCoroutineContext().ensureActive()
            LunarResult.Error(FileSystemErrors.CANNOT_CONNECT)
        }
    }

    fun currentConnectionState(): LunarConnectionState {
        return if (token.isNotEmpty()) {
            LunarConnectionState.CONNECTED
        } else {
            LunarConnectionState.DISCONNECTED
        }
    }

    suspend fun getMounts(): FileSystemResult<Mounts> = makeRequest<MountsDto, Mounts>(
        url = "$baseUrl/api/v1/fs/mounts",
        method = HttpMethod.Get
    ) {
        it.map()
    }

    suspend fun getMetaData(path: String): FileSystemResult<LunarMetaData> =
        makeRequest<MetaDataDto, LunarMetaData>(
            url = "$baseUrl/api/v1/fs/metadata",
            method = HttpMethod.Get,
            parameters = mapOf("path" to path)
        ) {
            it.map()
        }

    suspend fun getFolderContent(path: String): FileSystemResult<FolderInfo> =
        makeRequest<FolderContentDto, FolderInfo>(
            url = "$baseUrl/api/v1/fs/get_folder_content",
            method = HttpMethod.Get,
            parameters = mapOf("path" to path)
        ) {
            it.map()
        }


    suspend fun createFile(path: String): FileSystemResult<LunarMetaData> =
        makeRequest<MetaDataDto, LunarMetaData>(
            url = "$baseUrl/api/v1/fs/touch",
            method = HttpMethod.Post,
            parameters = mapOf("path" to path)
        ) { dto -> dto.map() }

    suspend fun createFolder(path: String): FileSystemResult<LunarMetaData> =
        makeRequest<MetaDataDto, LunarMetaData>(
            url = "$baseUrl/api/v1/fs/mkdir",
            method = HttpMethod.Post,
            parameters = mapOf("path" to path)
        ) { dto -> dto.map() }

    suspend fun delete(path: String, permanently: Boolean): FileSystemResult<Unit> =
        requestNoBody(
            url = "$baseUrl/api/v1/fs/delete",
            method = HttpMethod.Delete,
            parameters = mapOf("path" to path, "permanent" to permanently)
        )

    suspend fun rename(
        path: String,
        newPath: String,
        shouldOverride: Boolean
    ): FileSystemResult<Unit> =
        requestNoBody(
            url = "$baseUrl/api/v1/fs/rename",
            method = HttpMethod.Patch,
            parameters = mapOf(
                "path" to path,
                "new_path" to newPath,
                "should_overwrite" to shouldOverride
            )
        )

    suspend fun copy(
        path: String,
        newPath: String,
        shouldOverride: Boolean
    ): FileSystemResult<Unit> =
        requestNoBody(
            url = "$baseUrl/api/v1/fs/copy",
            method = HttpMethod.Post,
            parameters = mapOf(
                "path" to path,
                "new_path" to newPath,
                "should_overwrite" to shouldOverride
            )
        )

    suspend fun move(
        path: String,
        newPath: String,
        shouldOverride: Boolean
    ): FileSystemResult<Unit> =
        requestNoBody(
            url = "$baseUrl/api/v1/fs/move",
            method = HttpMethod.Patch,
            parameters = mapOf(
                "path" to path,
                "new_path" to newPath,
                "should_overwrite" to shouldOverride
            )
        )

    suspend fun getCompleteFile(path: String): FileSystemResult<ByteArray> =
        makeRequest<FileData, ByteArray>(
            url = "$baseUrl/api/v1/fs/read_full_file",
            method = HttpMethod.Get,
            parameters = mapOf("path" to path)
        ) {
            it.data.toByteArrayUnsigned()
        }

    suspend fun getFileChunk(
        path: String,
        range: LongRange
    ): FileSystemResult<ByteArray>
    {
        val response = client.request("$baseUrl/api/v1/fs/download_file") {
            this.method = HttpMethod.Get
            bearerAuth(token = token)
            headers {
                range.let { append(HttpHeaders.Range, "bytes=${it.first}-${it.last}") }
            }
            parameter("path", path)
        }

        if (!response.status.isSuccess()) {
            Log.d("TestWork", "${response}}")
//            throw RuntimeException("Failed to fetch file chunk: ${response.status}")
            return LunarResult.Error(FileSystemErrors.UNKNOWN_ERROR)
        }
        return LunarResult.Success(response.body())
    }

    suspend fun getDeviceInfo(): FileSystemResult<InfoDto> =
        makeRequest<InfoDto, InfoDto>(
            url = "$baseUrl/api/get_info",
            method = HttpMethod.Get
        ) {
            it
        }

}


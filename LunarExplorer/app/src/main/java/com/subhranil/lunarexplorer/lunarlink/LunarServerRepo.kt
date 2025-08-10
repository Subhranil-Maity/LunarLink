package com.subhranil.lunarexplorer.lunarlink

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.subhranil.lunarexplorer.lunarlink.dto.ErrorDto
import com.subhranil.lunarexplorer.lunarlink.dto.FolderContentDto
import com.subhranil.lunarexplorer.lunarlink.dto.LoginRequestDto
import com.subhranil.lunarexplorer.lunarlink.dto.LoginResponseDto
import com.subhranil.lunarexplorer.lunarlink.dto.MetaDataDto
import com.subhranil.lunarexplorer.lunarlink.dto.MountsDto
import com.subhranil.lunarexplorer.lunarlink.dto.map
import com.subhranil.lunarexplorer.models.FolderInfo
import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.repository.FileSystemRepository
import com.subhranil.lunarexplorer.result.FileSystemErrors
import com.subhranil.lunarexplorer.result.FileSystemResult
import com.subhranil.lunarexplorer.result.LunarResult
import com.subhranil.lunarexplorer.ui.theme.LunarExplorerTheme
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext.get



class LunarServerRepo(
    private val client: HttpClient,
    private val baseUrl: String,
    private val userName: String,
    private val pass: String,
): FileSystemRepository {
    private var token: String = ""
    @OptIn(InternalAPI::class)
    override suspend fun connect(): FileSystemResult<Unit> {
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
        }
        catch (e: Exception) {
            Log.d("RemoteServerRepo", e.message.toString())
            currentCoroutineContext().ensureActive()
            return LunarResult.Error(FileSystemErrors.CANNOT_CONNECT)
        }
        return LunarResult.Success(Unit)
    }

    override suspend fun getMounts(): FileSystemResult<Mounts> {
        try {
            val response = client.request("$baseUrl/api/v1/fs/mounts") {
                method = HttpMethod.Get
                bearerAuth(token = token)
            }
            when (response.status.value) {
                HttpStatusCode.OK.value -> {
                    val responseData = Json.decodeFromString<MountsDto>(response.body())
                    return LunarResult.Success(responseData.map())
                }
                HttpStatusCode.Unauthorized.value -> {
                    Toast.makeText(get().get<Context>(), "unauthorised not handled yet", Toast.LENGTH_SHORT).show()
                    return LunarResult.Error(FileSystemErrors.LOGIN_FAILED)
                }
                else -> {
                    val errorResponse = Json.decodeFromString<ErrorDto>(response.body())
                    Log.d("RemoteServerRepo", errorResponse.message)
                    return LunarResult.Error(FileSystemErrors.UNKNOWN_ERROR)
                }
            }
        }
        catch (e: Exception) {
            Log.d("RemoteServerRepo", e.message.toString())
            currentCoroutineContext().ensureActive()
            return LunarResult.Error(FileSystemErrors.CANNOT_CONNECT)
        }
    }

    override suspend fun getMetaData(path: String): FileSystemResult<LunarMetaData> {
        try {
            val response = client.request("$baseUrl/api/v1/fs/metadata") {
                method = HttpMethod.Get
                bearerAuth(token = token)
                parameter("path", path)
            }
            when (response.status.value) {
                HttpStatusCode.OK.value -> {
                    val responseData = Json.decodeFromString<MetaDataDto>(response.body())
                    return LunarResult.Success(responseData.map())
                }
                HttpStatusCode.Unauthorized.value -> {
                    Toast.makeText(get().get<Context>(), "unauthorised not handled yet", Toast.LENGTH_SHORT).show()
                    return LunarResult.Error(FileSystemErrors.LOGIN_FAILED)
                }
                else -> {
                    val errorResponse = Json.decodeFromString<ErrorDto>(response.body())
                    Log.d("RemoteServerRepo", errorResponse.message)
                    return LunarResult.Error(FileSystemErrors.UNKNOWN_ERROR)
                }
            }
        }
        catch (e: Exception) {
            Log.d("RemoteServerRepo", e.message.toString())
            currentCoroutineContext().ensureActive()
            return LunarResult.Error(FileSystemErrors.CANNOT_CONNECT)
        }
    }

    override suspend fun getFolderContent(path: String): FileSystemResult<FolderInfo> {
        try {
            val response = client.request("$baseUrl/api/v1/fs/get_folder_content") {
                method = HttpMethod.Get
                bearerAuth(token = token)
                parameter("path", path)
            }
            when (response.status.value) {
                HttpStatusCode.OK.value -> {
                    val responseData = Json.decodeFromString<FolderContentDto>(response.body())
                    return LunarResult.Success(responseData.map())
                }
                HttpStatusCode.Unauthorized.value -> {
                    Toast.makeText(get().get<Context>(), "unauthorised not handled yet", Toast.LENGTH_SHORT).show()
                    return LunarResult.Error(FileSystemErrors.LOGIN_FAILED)
                }
                else -> {
                    val errorResponse = Json.decodeFromString<ErrorDto>(response.body())
                    Log.d("RemoteServerRepo", errorResponse.message)
                    return LunarResult.Error(FileSystemErrors.UNKNOWN_ERROR)
                }
            }
        }
        catch (e: Exception) {
            Log.d("RemoteServerRepo", e.message.toString())
            currentCoroutineContext().ensureActive()
            return LunarResult.Error(FileSystemErrors.CANNOT_CONNECT)
        }
    }
}


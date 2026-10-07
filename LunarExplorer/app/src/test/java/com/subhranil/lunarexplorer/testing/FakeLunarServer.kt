package com.subhranil.lunarexplorer.testing

import com.subhranil.lunarexplorer.data.api.LunarApi
import com.subhranil.lunarexplorer.data.api.createHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * An in-memory LunarLink server behind Ktor's MockEngine. Implements the parts of the API the app
 * uses, following API.md: auth, roots, stat, list, download (with Range), create, copy, move, delete and
 * error envelopes.
 */
class FakeLunarServer(
    val username: String = "user",
    val password: String = "pw",
    val authRequired: Boolean = true,
    val rootPath: String = "/share",
) {
    /** Paths that exist; folders end up as keys with `true`. */
    val nodes = sortedMapOf(rootPath to true)
    var loginCount = 0
        private set
    var validToken: String? = null
        private set

    /** Requests seen, as "METHOD /path". */
    val requests = mutableListOf<String>()

    /** File contents; files without an entry here are 10 zero bytes. */
    val contents = mutableMapOf<String, ByteArray>()

    /** Modification times; the default is 1700000000. */
    val modified = mutableMapOf<String, Long>()

    /** `Range` headers of download requests, `null` when none was sent. */
    val downloadRanges = mutableListOf<String?>()

    /** Ignores `Range` and always sends the whole file, like a server without range support. */
    var ignoreRange = false

    /** Streams download bodies in small chunks with a pause between them, like a slow network. */
    var slowDownloads = false

    /** Sends only this many bytes of the next download, then closes the body, like a dropped connection. */
    var truncateNextDownloadAt: Int? = null

    fun file(path: String) = apply { nodes[path] = false }
    fun file(path: String, content: ByteArray) = apply {
        nodes[path] = false
        contents[path] = content
    }
    fun dir(path: String) = apply { nodes[path] = true }

    /** Makes the current token invalid, like the server's expiry. */
    fun expireToken() {
        validToken = null
    }

    val engine = MockEngine { request -> handle(request) }

    fun api(): LunarApi = LunarApi(createHttpClient(engine), "http://fake")

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.error(status: HttpStatusCode, code: String, message: String = code) =
        json("""{"error":{"code":"$code","message":"$message","details":null}}""", status)

    private fun body(request: HttpRequestData): JsonObject {
        val text = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString() ?: "{}"
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.str(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.bool(key: String) = get(key)?.jsonPrimitive?.boolean ?: false

    private fun entryJson(path: String): String {
        val isDir = nodes[path] == true
        val name = path.substringAfterLast('/')
        val size = if (isDir) 0 else bytes(path).size
        return """{"name":"$name","path":"$path","kind":"${if (isDir) "dir" else "file"}","size":$size,""" +
            """"modified":${modified[path] ?: 1700000000},"hidden":${name.startsWith(".")},"unknown_future_field":42}"""
    }

    private fun bytes(path: String) = contents[path] ?: ByteArray(10)

    private fun MockRequestHandleScope.download(request: HttpRequestData): HttpResponseData {
        val path = request.url.parameters["path"]!!
        if (path !in nodes) return error(HttpStatusCode.NotFound, "NOT_FOUND")
        if (nodes[path] == true) return error(HttpStatusCode.BadRequest, "IS_A_DIRECTORY")
        val data = bytes(path)
        val range = request.headers[HttpHeaders.Range]
        downloadRanges += range
        val start = range?.takeUnless { ignoreRange }?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()
        if (start != null && start >= data.size) return respond(ByteArray(0), HttpStatusCode.RequestedRangeNotSatisfiable)
        var body = if (start != null) data.copyOfRange(start, data.size) else data
        truncateNextDownloadAt?.let {
            body = body.copyOf(it)
            truncateNextDownloadAt = null
        }
        if (slowDownloads) {
            val chunks = body.toList().chunked(1024).map { it.toByteArray() }
            val channel = CoroutineScope(Dispatchers.Default).writer {
                for (chunk in chunks) {
                    channel.writeFully(chunk)
                    channel.flush()
                    delay(20)
                }
            }.channel
            return respond(channel, if (start != null) HttpStatusCode.PartialContent else HttpStatusCode.OK)
        }
        return if (start != null) {
            respond(
                body,
                HttpStatusCode.PartialContent,
                headersOf(HttpHeaders.ContentRange, "bytes $start-${data.size - 1}/${data.size}"),
            )
        } else {
            respond(body, HttpStatusCode.OK)
        }
    }

    private fun children(path: String) = nodes.keys.filter { it.substringBeforeLast('/').ifEmpty { "/" } == path && it != path }

    private fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        requests += "${request.method.value} $path"
        when (path) {
            "/api/info" -> return json(
                """{"name":"Fake","os_type":"linux","os_version":"1","server_version":"0.2.0","api_version":1,"auth_required":$authRequired}"""
            )
            "/api/auth/login" -> {
                val b = body(request)
                if (b.str("username") != username || b.str("password") != password) {
                    return error(HttpStatusCode.Unauthorized, "LOGIN_FAILED")
                }
                loginCount++
                validToken = "token-$loginCount"
                return json(
                    """{"token":"$validToken","token_type":"Bearer","expires_at":4000000000,""" +
                        """"user":{"username":"$username","permissions":["read","write","delete"]}}"""
                )
            }
        }

        if (authRequired) {
            val header = request.headers[HttpHeaders.Authorization]
            if (header == null) return error(HttpStatusCode.Unauthorized, "UNAUTHENTICATED")
            if (header != "Bearer $validToken") return error(HttpStatusCode.Unauthorized, "TOKEN_EXPIRED")
        }

        return when (path) {
            "/api/v1/fs/roots" -> json(
                """{"roots":[{"name":"share","path":"$rootPath","kind":"mount","permissions":["read","write","delete"],"disk":null}]}"""
            )
            "/api/v1/fs/stat" -> {
                val target = request.url.parameters["path"]!!
                if (target !in nodes) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                json(entryJson(target))
            }
            "/api/v1/fs/download" -> download(request)
            "/api/v1/fs/list" -> {
                val dir = request.url.parameters["path"]!!
                if (dir !in nodes) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                val showHidden = request.url.parameters["show_hidden"] != "false"
                val all = children(dir).filter { showHidden || !it.substringAfterLast('/').startsWith(".") }
                val offset = request.url.parameters["offset"]?.toInt() ?: 0
                val limit = request.url.parameters["limit"]?.toInt() ?: all.size
                val page = all.drop(offset).take(limit)
                json(
                    """{"entry":${entryJson(dir)},"items":[${page.joinToString(",") { entryJson(it) }}],""" +
                        """"total":${all.size},"offset":$offset,"limit":$limit}"""
                )
            }
            "/api/v1/fs/file", "/api/v1/fs/dir" -> {
                val target = body(request).str("path")
                if (target in nodes) return error(HttpStatusCode.Conflict, "ALREADY_EXISTS")
                nodes[target] = path.endsWith("dir")
                json(entryJson(target), HttpStatusCode.Created)
            }
            "/api/v1/fs/copy", "/api/v1/fs/move" -> {
                val b = body(request)
                val from = b.str("from")
                val to = b.str("to")
                if (from !in nodes) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                if (from == to) return error(HttpStatusCode.BadRequest, "INVALID_OPERATION")
                if (to in nodes && !b.bool("overwrite")) return error(HttpStatusCode.Conflict, "ALREADY_EXISTS")
                removeTree(to)
                val moved = nodes.filterKeys { it == from || it.startsWith("$from/") }
                moved.forEach { (p, isDir) -> nodes[to + p.removePrefix(from)] = isDir }
                if (path.endsWith("move")) removeTree(from)
                json(entryJson(to), if (path.endsWith("copy")) HttpStatusCode.Created else HttpStatusCode.OK)
            }
            "/api/v1/fs/delete" -> {
                val b = body(request)
                val target = b.str("path")
                if (target !in nodes) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                if (nodes[target] == true && children(target).isNotEmpty() && !b.bool("recursive")) {
                    return error(HttpStatusCode.Conflict, "DIRECTORY_NOT_EMPTY")
                }
                removeTree(target)
                respond("", HttpStatusCode.NoContent)
            }
            else -> error(HttpStatusCode.NotFound, "NOT_FOUND")
        }
    }

    private fun removeTree(path: String) {
        nodes.keys.removeAll { it == path || it.startsWith("$path/") }
    }
}

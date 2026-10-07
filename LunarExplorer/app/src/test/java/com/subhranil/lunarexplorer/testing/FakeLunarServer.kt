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
    /** Optional features reported by `/api/info`. */
    val features: Set<String> = setOf("search", "thumbnails", "jobs"),
) {
    /** Paths that exist; folders end up as keys with `true`. */
    val nodes = sortedMapOf(rootPath to true)
    var loginCount = 0
        private set
    var validToken: String? = null
        private set

    /** Polls a job reports `running` before it finishes. */
    var jobSteps = 2

    /** When set, jobs fail with this error code instead of finishing. */
    var jobFailure: String? = null

    /** Cancel requests seen, by job id. */
    val cancelledJobs = mutableListOf<String>()

    private class FakeJob(val id: String, val move: Boolean, val from: String, val to: String) {
        var polls = 0
        var state = "running"
    }

    private val jobs = mutableMapOf<String, FakeJob>()

    /** Query parameters of the latest `list` request. */
    var lastListParams: Map<String, String> = emptyMap()
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
                """{"name":"Fake","os_type":"linux","os_version":"1","server_version":"0.2.0","api_version":1,"auth_required":$authRequired,""" +
                    """"features":[${features.joinToString(",") { "\"$it\"" }}]}"""
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

        if (path.startsWith("/api/v1/jobs/") && path != "/api/v1/jobs/copy" && path != "/api/v1/jobs/move") {
            val rest = path.removePrefix("/api/v1/jobs/")
            val job = jobs[rest.removeSuffix("/cancel")] ?: return error(HttpStatusCode.NotFound, "NOT_FOUND")
            if (rest.endsWith("/cancel")) {
                cancelledJobs += job.id
                if (job.state == "running") job.state = "cancelled"
                return json(jobJson(job), HttpStatusCode.Accepted)
            }
            if (job.state == "running" && ++job.polls >= jobSteps) {
                val failure = jobFailure
                if (failure != null) {
                    job.state = "failed"
                } else {
                    transfer(job.from, job.to, job.move)
                    job.state = "done"
                }
            }
            return json(jobJson(job))
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
            "/api/v1/fs/thumbnail" -> {
                val target = request.url.parameters["path"]!!
                if (target !in nodes) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                if (!target.endsWith(".jpg")) return error(HttpStatusCode.UnsupportedMediaType, "THUMBNAIL_UNAVAILABLE")
                val size = request.url.parameters["size"]!!
                respond("thumb:$size:$target".toByteArray(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            }
            "/api/v1/fs/search" -> {
                if ("search" !in features) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                val dir = request.url.parameters["path"]!!
                if (dir !in nodes) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                val query = request.url.parameters["query"]!!.trim().lowercase()
                val showHidden = request.url.parameters["show_hidden"] != "false"
                val matches = nodes.keys
                    .filter { it.startsWith("$dir/") }
                    .filter { p -> showHidden || p.removePrefix(dir).split('/').none { it.startsWith(".") } }
                    .filter { it.substringAfterLast('/').lowercase().contains(query) }
                    .sortedBy { p -> p.count { it == '/' } }
                json("""{"items":[${matches.joinToString(",") { entryJson(it) }}],"truncated":false}""")
            }
            "/api/v1/fs/list" -> {
                val dir = request.url.parameters["path"]!!
                lastListParams = request.url.parameters.names().associateWith { request.url.parameters[it]!! }
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
            "/api/v1/fs/copy", "/api/v1/fs/move", "/api/v1/jobs/copy", "/api/v1/jobs/move" -> {
                val isJob = path.startsWith("/api/v1/jobs")
                if (isJob && "jobs" !in features) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                val move = path.endsWith("move")
                val b = body(request)
                val from = b.str("from")
                val to = b.str("to")
                if (from !in nodes) return error(HttpStatusCode.NotFound, "NOT_FOUND")
                if (from == to) return error(HttpStatusCode.BadRequest, "INVALID_OPERATION")
                if (to in nodes && !b.bool("overwrite")) return error(HttpStatusCode.Conflict, "ALREADY_EXISTS")
                if (isJob) {
                    val job = FakeJob("job-${jobs.size + 1}", move, from, to)
                    jobs[job.id] = job
                    return json(jobJson(job), HttpStatusCode.Accepted)
                }
                transfer(from, to, move)
                json(entryJson(to), if (move) HttpStatusCode.OK else HttpStatusCode.Created)
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

    private fun jobJson(job: FakeJob): String {
        val name = job.from.substringAfterLast('/')
        val error = if (job.state == "failed") """{"code":"$jobFailure","message":"failed"}""" else "null"
        val entry = if (job.state == "done") entryJson(job.to) else "null"
        return """{"id":"${job.id}","kind":"${if (job.move) "move" else "copy"}","state":"${job.state}",""" +
            """"from":"${job.from}","to":"${job.to}","bytes_total":${jobSteps * 10},"bytes_done":${job.polls * 10},""" +
            """"files_total":1,"files_done":0,"current":"$name","entry":$entry,"error":$error,"started_at":1}"""
    }

    private fun transfer(from: String, to: String, move: Boolean) {
        removeTree(to)
        val moved = nodes.filterKeys { it == from || it.startsWith("$from/") }
        moved.forEach { (p, isDir) -> nodes[to + p.removePrefix(from)] = isDir }
        if (move) removeTree(from)
    }

    private fun removeTree(path: String) {
        nodes.keys.removeAll { it == path || it.startsWith("$path/") }
    }
}

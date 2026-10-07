package com.subhranil.lunarexplorer.data

import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.EntryKind
import com.subhranil.lunarexplorer.data.api.ErrorCode
import com.subhranil.lunarexplorer.data.api.ListQuery
import com.subhranil.lunarexplorer.data.api.LunarApi
import com.subhranil.lunarexplorer.data.api.LunarError
import com.subhranil.lunarexplorer.data.api.SortKey
import com.subhranil.lunarexplorer.data.api.SortOrder
import com.subhranil.lunarexplorer.data.api.createHttpClient
import com.subhranil.lunarexplorer.data.api.hasCode
import com.subhranil.lunarexplorer.testing.FakeLunarServer
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.readRemaining
import kotlinx.io.readString
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException

class LunarApiTest {

    @Test
    fun parsesInfoAndIgnoresUnknownFields() = runTest {
        val server = FakeLunarServer().dir("/share/docs").file("/share/a.txt")
        val api = server.api()
        val info = (api.info() as ApiResult.Success).value
        assertEquals("Fake", info.name)
        assertTrue(info.authRequired)

        val token = (api.login("user", "pw") as ApiResult.Success).value.token
        val listing = (api.list(token, "/share") as ApiResult.Success).value
        assertEquals(listOf("a.txt", "docs"), listing.items.map { it.name })
        assertEquals(EntryKind.DIR, listing.items[1].kind)
        assertEquals(2, listing.total)
    }

    @Test
    fun sendsListQueryParameters() = runTest {
        var url = ""
        val engine = MockEngine { request ->
            url = request.url.toString()
            respond(
                """{"entry":{"name":"x","path":"/x","kind":"dir"},"items":[],"total":0,"offset":5}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = LunarApi(createHttpClient(engine), "http://h:1/")
        api.list("t", "/x y", ListQuery(offset = 5, limit = 10, sort = SortKey.SIZE, order = SortOrder.DESC, showHidden = false))
        assertTrue(url, url.startsWith("http://h:1/api/v1/fs/list?"))
        listOf("path=%2Fx+y", "offset=5", "limit=10", "sort=size", "order=desc", "show_hidden=false").forEach {
            assertTrue("$url should contain $it", it in url)
        }
    }

    @Test
    fun mapsErrorEnvelope() = runTest {
        val engine = MockEngine {
            respond(
                """{"error":{"code":"TOO_MANY_ATTEMPTS","message":"slow down","details":{"retry_after_seconds":7}}}""",
                HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = LunarApi(createHttpClient(engine), "http://h").login("a", "b")
        val error = (result as ApiResult.Failure).error as LunarError.Api
        assertEquals(ErrorCode.TOO_MANY_ATTEMPTS, error.code)
        assertEquals(7L, error.retryAfterSeconds)
        assertEquals(429, error.status)
    }

    @Test
    fun unknownErrorCodeAndNonJsonBodies() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("info")) {
                respond("""{"error":{"code":"BRAND_NEW","message":"new"}}""", HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("", HttpStatusCode.MethodNotAllowed)
            }
        }
        val api = LunarApi(createHttpClient(engine), "http://h")
        assertEquals(ErrorCode.UNKNOWN, ((api.info() as ApiResult.Failure).error as LunarError.Api).code)
        val other = (api.delete(null, "/x", false, false) as ApiResult.Failure).error as LunarError.Api
        assertEquals(405, other.status)
    }

    @Test
    fun handlesNoContentAndCreated() = runTest {
        val server = FakeLunarServer().file("/share/a.txt")
        val api = server.api()
        val token = (api.login("user", "pw") as ApiResult.Success).value.token
        assertEquals(ApiResult.Success(Unit), api.delete(token, "/share/a.txt", permanent = true, recursive = false))
        val created = api.createDir(token, "/share/new")
        assertEquals("new", (created as ApiResult.Success).value.name)
    }

    @Test
    fun networkFailureIsUnreachable() = runTest {
        val engine = MockEngine { throw ConnectException("refused") }
        val result = LunarApi(createHttpClient(engine), "http://h").info()
        assertEquals(LunarError.Unreachable, (result as ApiResult.Failure).error)
    }

    @Test
    fun malformedBodyIsUnexpected() = runTest {
        val engine = MockEngine {
            respond("not json", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = LunarApi(createHttpClient(engine), "http://h").info()
        assertTrue((result as ApiResult.Failure).error is LunarError.Unexpected)
    }

    @Test
    fun downloadSupportsRangesAndReportsErrors() = runTest {
        val server = FakeLunarServer().file("/share/a.txt", "hello world".toByteArray())
        val api = server.api()
        val token = (api.login("user", "pw") as ApiResult.Success).value.token

        var status = 0
        var body = ""
        assertTrue(api.download(token, "/share/a.txt", 6) { status = it.status; body = it.body.readRemaining().readString() } is ApiResult.Success)
        assertEquals(206, status)
        assertEquals("world", body)

        assertTrue(api.download(token, "/share/a.txt", 11) { status = it.status } is ApiResult.Success)
        assertEquals(416, status)

        var called = false
        val missing = api.download(token, "/share/nope", 0) { called = true }
        assertTrue((missing as ApiResult.Failure).error.hasCode(ErrorCode.NOT_FOUND))
        assertFalse(called)
    }

    @Test
    fun thumbnailsAreRawBytesAndFailuresUseErrorCodes() = runTest {
        val server = FakeLunarServer().file("/share/photo.jpg").file("/share/notes.txt")
        val api = server.api()
        val token = (api.login("user", "pw") as ApiResult.Success).value.token
        val bytes = (api.thumbnail(token, "/share/photo.jpg", 128) as ApiResult.Success).value
        assertEquals("thumb:128:/share/photo.jpg", bytes.decodeToString())
        val failure = api.thumbnail(token, "/share/notes.txt", 128) as ApiResult.Failure
        assertTrue(failure.error.hasCode(ErrorCode.THUMBNAIL_UNAVAILABLE))
        assertEquals(415, (failure.error as LunarError.Api).status)
    }
}

package com.subhranil.lunarexplorer.data

import com.subhranil.lunarexplorer.data.api.ApiResult
import com.subhranil.lunarexplorer.data.api.ErrorCode
import com.subhranil.lunarexplorer.data.api.ListQuery
import com.subhranil.lunarexplorer.data.api.LunarError
import com.subhranil.lunarexplorer.data.api.hasCode
import com.subhranil.lunarexplorer.data.servers.ServerDraft
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.data.session.ServerProbe
import com.subhranil.lunarexplorer.data.session.ServerSession
import com.subhranil.lunarexplorer.data.session.SessionManager
import com.subhranil.lunarexplorer.data.session.SessionState
import com.subhranil.lunarexplorer.testing.FakeCipher
import com.subhranil.lunarexplorer.testing.FakeLunarServer
import com.subhranil.lunarexplorer.testing.tempDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    @Test
    fun relogsInOnceWhenTokenExpires() = runTest {
        val server = FakeLunarServer().dir("/share/docs")
        val session = (ServerSession.open("id", server.api(), "user", "pw") as ApiResult.Success).value
        assertEquals(1, server.loginCount)

        server.expireToken()
        val listing = session.list("/share", ListQuery())
        assertTrue(listing is ApiResult.Success)
        assertEquals(2, server.loginCount)
    }

    @Test
    fun wrongPasswordFailsToOpen() = runTest {
        val result = ServerSession.open("id", FakeLunarServer().api(), "user", "nope")
        assertTrue((result as ApiResult.Failure).error.hasCode(ErrorCode.LOGIN_FAILED))
    }

    @Test
    fun serverWithoutAuthNeverLogsIn() = runTest {
        val server = FakeLunarServer(authRequired = false)
        val session = (ServerSession.open("id", server.api(), "", "") as ApiResult.Success).value
        assertTrue(session.roots() is ApiResult.Success)
        assertEquals(0, server.loginCount)
    }

    @Test
    fun proactivelyRefreshesBeforeExpiry() = runTest {
        val server = FakeLunarServer()
        var now = 0L
        val session = (ServerSession.open("id", server.api(), "user", "pw", clock = { now }) as ApiResult.Success).value
        now = 4_000_000_000 - 10 // within the refresh margin of expires_at
        session.roots()
        assertEquals(2, server.loginCount)
    }

    @Test
    fun probeChecksCredentials() = runTest {
        val server = FakeLunarServer()
        val probe = ServerProbe { server.api() }
        assertTrue(probe.test("http://fake", "user", "pw") is ApiResult.Success)
        assertTrue(probe.test("http://fake", "user", "bad") is ApiResult.Failure)
        assertTrue(probe.info("http://fake") is ApiResult.Success)
    }

    @Test
    fun sessionManagerConnectsReusesAndDisconnects() = runTest {
        val server = FakeLunarServer()
        val repo = ServerRepository(tempDataStore(), FakeCipher())
        val id = repo.add(ServerDraft("Old name", "http://fake", "user", "pw", null))
        val manager = SessionManager(repo, { server.api() }, clock = { 123 })

        val first = (manager.connect(id) as ApiResult.Success).value
        val second = (manager.connect(id) as ApiResult.Success).value
        assertTrue(first === second)
        assertEquals(1, server.loginCount)
        assertTrue(manager.state.value is SessionState.Connected)

        // Connecting records the server's real name and the time.
        val saved = repo.servers.first().single()
        assertEquals("Fake", saved.name)
        assertEquals(123L, saved.lastConnectedAt)
        assertEquals("linux", saved.osType)

        manager.disconnect("other-id")
        assertTrue(manager.state.value is SessionState.Connected)
        manager.disconnect(id)
        assertEquals(SessionState.Disconnected, manager.state.value)
    }

    @Test
    fun sessionManagerReportsFailures() = runTest {
        val repo = ServerRepository(tempDataStore(), FakeCipher())
        val id = repo.add(ServerDraft("x", "http://fake", "user", "wrong", null))
        val manager = SessionManager(repo, { FakeLunarServer().api() })
        val result = manager.connect(id)
        assertTrue(result is ApiResult.Failure)
        val state = manager.state.value as SessionState.Failed
        assertTrue(state.error.hasCode(ErrorCode.LOGIN_FAILED))

        val missing = manager.connect("does-not-exist")
        assertNotNull((missing as ApiResult.Failure).error as? LunarError.Unexpected)
    }
}

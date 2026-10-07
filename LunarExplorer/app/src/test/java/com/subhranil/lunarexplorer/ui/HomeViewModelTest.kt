package com.subhranil.lunarexplorer.ui

import com.subhranil.lunarexplorer.R
import com.subhranil.lunarexplorer.data.api.LunarApi
import com.subhranil.lunarexplorer.data.api.createHttpClient
import com.subhranil.lunarexplorer.data.servers.ServerDraft
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.data.session.ServerProbe
import com.subhranil.lunarexplorer.testing.FakeCipher
import com.subhranil.lunarexplorer.testing.FakeLunarServer
import com.subhranil.lunarexplorer.testing.MainDispatcherRule
import com.subhranil.lunarexplorer.testing.tempDataStore
import com.subhranil.lunarexplorer.ui.home.HomeUiState
import com.subhranil.lunarexplorer.ui.home.HomeViewModel
import com.subhranil.lunarexplorer.ui.home.ServerStatus
import com.subhranil.lunarexplorer.ui.home.TestState
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.ConnectException

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeLunarServer()
    private val repo = ServerRepository(tempDataStore(), FakeCipher())
    private val offlineApi = LunarApi(createHttpClient(MockEngine { throw ConnectException() }), "http://down")
    private val probe = ServerProbe { url -> if ("down" in url) offlineApi else server.api() }

    private fun viewModel() = HomeViewModel(repo, probe)

    private suspend fun HomeViewModel.await(predicate: (HomeUiState) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first(predicate) } }

    @Test
    fun showsOnlineAndOfflineServers() = runTest {
        repo.add(ServerDraft("Up", "http://up", "user", "pw", "linux"))
        repo.add(ServerDraft("Down", "http://down", "user", "pw", "linux"))
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        val state = vm.await { s -> s.servers.size == 2 && s.servers.none { it.status == ServerStatus.CHECKING } }
        assertEquals(ServerStatus.ONLINE, state.servers.first { it.server.name == "Up" }.status)
        // Offline servers stay in the list.
        assertEquals(ServerStatus.OFFLINE, state.servers.first { it.server.name == "Down" }.status)
    }

    @Test
    fun savingRequiresASuccessfulTestOfTheCurrentInput() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        vm.openAddServer()
        vm.onHostChange("192.168.1.20")
        vm.onPortChange("1234")
        vm.onUsernameChange("user")
        vm.onPasswordChange("pw")
        assertFalse(vm.await { it.editor?.password == "pw" }.editor!!.canSave)

        vm.testConnection()
        val tested = vm.await { it.editor?.test is TestState.Success }
        assertTrue(tested.editor!!.canSave)

        // Changing the input invalidates the test.
        vm.onPasswordChange("pw2")
        assertEquals(TestState.Idle, vm.await { it.editor?.password == "pw2" }.editor!!.test)
        vm.saveServer()
        assertEquals(uiText(R.string.editor_test_required), vm.await { it.editor?.error != null }.editor!!.error)

        vm.onPasswordChange("pw")
        vm.testConnection()
        vm.await { it.editor?.test is TestState.Success }
        vm.saveServer()
        val saved = vm.await { it.editor == null && it.servers.isNotEmpty() }
        assertEquals("Fake", saved.servers.single().server.name)
        assertEquals("http://192.168.1.20:1234", saved.servers.single().server.baseUrl)
    }

    @Test
    fun wrongPasswordShowsLoginError() = runTest {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        vm.openAddServer()
        vm.onHostChange("host")
        vm.onUsernameChange("user")
        vm.onPasswordChange("bad")
        vm.testConnection()
        val failed = vm.await { it.editor?.test is TestState.Failed }
        assertEquals(uiText(R.string.error_login_failed), (failed.editor!!.test as TestState.Failed).message)
        assertFalse(failed.editor.canSave)
    }

    @Test
    fun duplicateServerIsRejected() = runTest {
        repo.add(ServerDraft("Fake", "http://host", "user", "pw", "linux"))
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        vm.openAddServer()
        vm.onHostChange("host")
        vm.onUsernameChange("user")
        vm.onPasswordChange("pw")
        vm.testConnection()
        vm.await { it.editor?.test is TestState.Success }
        vm.saveServer()
        val state = vm.await { it.editor?.error != null }
        assertEquals(uiText(R.string.editor_duplicate), state.editor!!.error)
    }

    @Test
    fun editLoadsSavedValuesAndDeleteNeedsConfirmation() = runTest {
        repo.add(ServerDraft("Fake", "http://host:1234", "user", "pw", "linux"))
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val server = vm.await { it.servers.isNotEmpty() }.servers.single().server

        vm.openEditServer(server)
        val editor = vm.await { it.editor != null }.editor!!
        assertEquals("host", editor.host)
        assertEquals("1234", editor.port)
        assertEquals("pw", editor.password)
        vm.dismissEditor()

        vm.requestDelete(server)
        assertEquals(server, vm.await { it.pendingDelete != null }.pendingDelete)
        vm.confirmDelete()
        val after = vm.await { it.servers.isEmpty() }
        assertNull(after.pendingDelete)
    }
}

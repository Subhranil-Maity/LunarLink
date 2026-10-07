package com.subhranil.lunarexplorer.ui

import com.subhranil.lunarexplorer.data.download.DownloadRepository
import com.subhranil.lunarexplorer.data.download.DownloadStore
import com.subhranil.lunarexplorer.data.servers.ServerDraft
import com.subhranil.lunarexplorer.data.servers.ServerRepository
import com.subhranil.lunarexplorer.data.session.SessionManager
import com.subhranil.lunarexplorer.data.settings.SettingsRepository
import com.subhranil.lunarexplorer.testing.FakeCipher
import com.subhranil.lunarexplorer.testing.FakeDownloadFiles
import com.subhranil.lunarexplorer.testing.FakeLunarServer
import com.subhranil.lunarexplorer.testing.FakeScheduler
import com.subhranil.lunarexplorer.testing.MainDispatcherRule
import com.subhranil.lunarexplorer.testing.tempDataStore
import com.subhranil.lunarexplorer.ui.explorer.ClipboardMode
import com.subhranil.lunarexplorer.ui.explorer.ConflictAction
import com.subhranil.lunarexplorer.ui.explorer.Connection
import com.subhranil.lunarexplorer.ui.explorer.ExplorerDialog
import com.subhranil.lunarexplorer.ui.explorer.ExplorerUiState
import com.subhranil.lunarexplorer.ui.explorer.ExplorerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExplorerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeLunarServer()
        .file("/share/a.txt")
        .file("/share/.hidden")
        .dir("/share/docs")
        .file("/share/docs/b.txt")

    private suspend fun ExplorerViewModel.await(predicate: (ExplorerUiState) -> Boolean): ExplorerUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first(predicate) } }

    private fun ExplorerUiState.names() = folder?.items?.map { it.name }

    private val downloadStore = DownloadStore(tempDataStore())
    private val downloads = DownloadRepository(downloadStore, FakeDownloadFiles(), FakeScheduler(), CoroutineScope(Dispatchers.Default))

    private fun viewModel(password: String = "pw"): ExplorerViewModel {
        val repo = ServerRepository(tempDataStore(), FakeCipher())
        val id = runBlocking { repo.add(ServerDraft("Fake", "http://fake", "user", password, "linux")) }
        val sessions = SessionManager(repo, { server.api() })
        return ExplorerViewModel(id, sessions, SettingsRepository(tempDataStore()), downloads)
    }

    private suspend fun openShare(vm: ExplorerViewModel): ExplorerUiState {
        val ready = vm.await { it.connection == Connection.Ready }
        vm.openRoot(ready.roots.single())
        return vm.await { it.folder?.isLoading == false && it.folder.isRefreshing.not() }
    }

    @Test
    fun connectsAndBrowsesWithHistory() = runTest {
        val vm = viewModel()
        val share = openShare(vm)
        assertEquals("Fake", share.serverName)
        assertEquals(listOf(".hidden", "a.txt", "docs"), share.names())

        vm.openEntry(share.folder!!.items.first { it.name == "docs" })
        val docs = vm.await { it.folder?.path == "/share/docs" && it.folder.isLoading.not() }
        assertEquals(listOf("b.txt"), docs.names())
        assertEquals(listOf("/share"), docs.history)

        assertTrue(vm.back())
        vm.await { it.folder?.path == "/share" && it.history.isEmpty() }
        assertTrue(vm.back())
        assertNull(vm.state.value.folder)
        assertFalse(vm.back())
    }

    @Test
    fun wrongPasswordShowsRetryableFailure() = runTest {
        val vm = viewModel(password = "bad")
        val state = vm.await { it.connection is Connection.Failed }
        assertTrue(state.roots.isEmpty())
    }

    @Test
    fun hiddenFilesCanBeHidden() = runTest {
        val vm = viewModel()
        openShare(vm)
        vm.setShowHidden(false)
        val state = vm.await { it.folder?.isRefreshing == false && it.names()?.contains(".hidden") == false }
        assertEquals(listOf("a.txt", "docs"), state.names())
    }

    @Test
    fun tappingAFileOpensActionsAndSelectionModeTogglesInstead() = runTest {
        val vm = viewModel()
        val share = openShare(vm)
        val file = share.folder!!.items.first { it.name == "a.txt" }
        vm.openEntry(file)
        assertEquals(ExplorerDialog.Actions(file), vm.state.value.dialog)
        vm.dismissDialog()

        vm.toggleSelection(file)
        assertTrue(vm.state.value.isSelecting)
        vm.openEntry(share.folder.items.first { it.name == "docs" })
        assertEquals(2, vm.state.value.selection.size)
        assertEquals("/share", vm.state.value.folder?.path) // didn't navigate while selecting
        vm.clearSelection()
        assertFalse(vm.state.value.isSelecting)
    }

    @Test
    fun searchFiltersLoadedItems() = runTest {
        val vm = viewModel()
        openShare(vm)
        vm.openSearch()
        vm.setSearchQuery("DOC")
        assertEquals(listOf("docs"), vm.state.value.visibleItems.map { it.name })
        vm.closeSearch()
        assertEquals(3, vm.state.value.visibleItems.size)
    }

    @Test
    fun createAndRename() = runTest {
        val vm = viewModel()
        openShare(vm)
        vm.create("new folder", folder = true)
        vm.await { it.names()?.contains("new folder") == true }
        assertEquals(true, server.nodes["/share/new folder"])

        val file = vm.state.value.folder!!.items.first { it.name == "a.txt" }
        vm.rename(file, "renamed.txt")
        vm.await { it.names()?.contains("renamed.txt") == true }
        assertFalse("/share/a.txt" in server.nodes)
    }

    @Test
    fun copyIntoSameFolderMakesACopyWithoutAsking() = runTest {
        val vm = viewModel()
        val share = openShare(vm)
        vm.copyToClipboard(share.folder!!.items.filter { it.name == "a.txt" })
        assertEquals(ClipboardMode.COPY, vm.state.value.clipboard?.mode)

        vm.paste()
        vm.await { it.progress == null && it.names()?.contains("a (1).txt") == true }
        assertTrue("/share/a.txt" in server.nodes)
        assertNull(vm.state.value.clipboard)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun pasteWithConflictKeepBoth() = runTest {
        server.file("/share/docs/a.txt")
        val vm = viewModel()
        val share = openShare(vm)
        vm.copyToClipboard(share.folder!!.items.filter { it.name == "a.txt" })
        vm.openEntry(share.folder.items.first { it.name == "docs" })
        vm.await { it.folder?.path == "/share/docs" && it.folder.isLoading.not() }

        vm.paste()
        assertEquals(ExplorerDialog.Conflict("a.txt"), vm.await { it.dialog is ExplorerDialog.Conflict }.dialog)
        vm.resolveConflict(ConflictAction.KEEP_BOTH, applyToAll = false)

        vm.await { it.progress == null && it.names()?.contains("a (1).txt") == true }
        assertTrue("/share/docs/a.txt" in server.nodes)
        assertTrue("/share/a.txt" in server.nodes)
    }

    @Test
    fun pasteWithConflictReplaceAndSkip() = runTest {
        server.file("/share/docs/a.txt")
        val vm = viewModel()
        val share = openShare(vm)
        val items = share.folder!!.items.filter { it.name == "a.txt" || it.name == ".hidden" }
        server.file("/share/docs/.hidden")
        vm.moveToClipboard(items)
        vm.openEntry(share.folder.items.first { it.name == "docs" })
        vm.await { it.folder?.path == "/share/docs" && it.folder.isLoading.not() }

        vm.paste()
        vm.await { it.dialog == ExplorerDialog.Conflict(".hidden") }
        vm.resolveConflict(ConflictAction.SKIP, applyToAll = false)
        vm.await { it.dialog == ExplorerDialog.Conflict("a.txt") }
        vm.resolveConflict(ConflictAction.REPLACE, applyToAll = false)
        vm.await { it.progress == null }

        assertFalse("/share/a.txt" in server.nodes) // moved, replacing the target
        assertTrue("/share/.hidden" in server.nodes) // skipped
    }

    @Test
    fun cannotPasteFolderIntoItself() = runTest {
        val vm = viewModel()
        val share = openShare(vm)
        val docs = share.folder!!.items.first { it.name == "docs" }
        vm.copyToClipboard(listOf(docs))
        vm.openEntry(docs)
        vm.await { it.folder?.path == "/share/docs" && it.folder.isLoading.not() }
        vm.paste()
        vm.await { it.progress == null && it.clipboard == null }
        assertFalse(server.nodes.keys.any { it.startsWith("/share/docs/docs") })
        assertFalse(server.requests.any { it.endsWith("/copy") })
    }

    @Test
    fun deleteFolderIsRecursive() = runTest {
        val vm = viewModel()
        val share = openShare(vm)
        vm.delete(share.folder!!.items.filter { it.name == "docs" }, permanent = true)
        vm.await { it.progress == null && it.names()?.contains("docs") == false }
        assertFalse(server.nodes.keys.any { it.startsWith("/share/docs") })
    }

    @Test
    fun downloadQueuesFilesAndSkipsFolders() = runTest {
        val vm = viewModel()
        val share = openShare(vm)
        vm.toggleSelection(share.folder!!.items.single { it.name == "a.txt" })
        vm.toggleSelection(share.folder.items.single { it.name == "docs" })
        vm.download(vm.state.value.selectedEntries)

        val queued = withContext(Dispatchers.Default) {
            withTimeout(5_000) { downloadStore.records.first { it.isNotEmpty() } }
        }
        assertEquals(listOf("/share/a.txt"), queued.map { it.remotePath })
        assertEquals("Fake", queued.single().serverName)
        assertTrue(vm.state.value.selection.isEmpty())
    }
}

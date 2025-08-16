package com.subhranil.lunarexplorer.explorer

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.subhranil.lunarexplorer.explorer.clipboard.NameConflictRequest
import com.subhranil.lunarexplorer.explorer.message.SnackbarMessage
import com.subhranil.lunarexplorer.explorer.options.ExplorerOptionType
import com.subhranil.lunarexplorer.explorer.options.ExplorerOptions
import com.subhranil.lunarexplorer.fs.CollisionType
import com.subhranil.lunarexplorer.fs.findCollisions
import com.subhranil.lunarexplorer.fs.generateNonConflictingPath
import com.subhranil.lunarexplorer.fs.getBasePath
import com.subhranil.lunarexplorer.fs.getNewPath
import com.subhranil.lunarexplorer.io.saveBytesToDownloadsWithGuessedMime
import com.subhranil.lunarexplorer.lunarlink.LunarServerRepo
import com.subhranil.lunarexplorer.models.EntryType
import com.subhranil.lunarexplorer.models.FolderInfo
import com.subhranil.lunarexplorer.models.LunarMetaData
import com.subhranil.lunarexplorer.models.Mounts
import com.subhranil.lunarexplorer.models.isDir
import com.subhranil.lunarexplorer.repository.FileSystemRepository
import com.subhranil.lunarexplorer.result.FileSystemErrors
import com.subhranil.lunarexplorer.result.LunarResult
import com.subhranil.lunarexplorer.result.RootError
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext.get

class ExplorerViewModel : ViewModel() {
    private val _state = MutableStateFlow(ExplorerState())
    val state = _state.asStateFlow()
    var pendingNameRequest by mutableStateOf<PendingNameRequest?>(null)
        private set

    val fs: FileSystemRepository = LunarServerRepo(
        client = get().get<HttpClient>(),
        baseUrl = "http://192.168.110.178:8080",
        userName = "power",
        pass = "power",
    )

    init {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isConnected = false
                )
            }
            val connect = fs.connect()
            when (connect) {
                is LunarResult.Error<Unit, FileSystemErrors> -> {
                    _state.update {
                        it.copy(
                            error = connect.error.toString()
                        )
                    }
                    return@launch
                }

                is LunarResult.Success<*, *> -> {}
            }
            val mounts = fs.getMounts()
            when (mounts) {
                is LunarResult.Error<Mounts, FileSystemErrors> -> {
                    _state.update {
                        it.copy(
                            error = mounts.error.toString()
                        )
                    }
                    return@launch
                }

                is LunarResult.Success<Mounts, FileSystemErrors> -> {
                    _state.update {
                        it.copy(
                            mounts = mounts.data
                        )
                    }
                }
            }

            _state.update {
                it.copy(
                    isConnected = true
                )
            }
        }
    }

    fun onAction(action: ExplorerActions) {
        when (action) {
            ExplorerActions.Refresh -> onRefresh()
            is ExplorerActions.GoToPath -> onGoToPath(action.path)
            ExplorerActions.OnBackPressed -> onBackPressed()
            is ExplorerActions.OnActionButtonPressed -> onActionButtonPressed(action.actionType)
            is ExplorerActions.OnSearchTextChange -> onSearchTextChange(action.search)
            ExplorerActions.OnSearchDisMissed -> onSearchDismissed()
            is ExplorerActions.OnItemClick -> onItemClicked(action.metaData)
            is ExplorerActions.OnLongPressed -> onLongPressed(action.metaData)
            ExplorerActions.OnOptionsDismiss -> onExtraOptionDismissed()
            is ExplorerActions.OnOptionClicked -> onOptionClicked(
                action.explorerOptions,
                action.metaData
            )
            ExplorerActions.OnPaste -> onPaste()
            ExplorerActions.OnClearSnackBar -> clearSnackbar()
        }
    }

    private fun onPaste() {
        _state.value.clipboard?.let { clipboardState ->
            when (clipboardState.actionType) {
                ClipboardActionType.COPY -> doCopy(clipboardState)
                ClipboardActionType.MOVE -> doMove(clipboardState)
            }
        }
        clearClipBoard()
    }
    private fun copyItem(
        metadata: LunarMetaData,
        currentPath: String,
        shouldOverride: Boolean = false,
        generateUniqueName: Boolean = false
    ) {
        viewModelScope.launch {
            val targetPath = if (generateUniqueName) {
                generateNonConflictingPath(
                    baseDir = currentPath,
                    originalName = metadata.name,
                    existingItems = _state.value.currentFolderContent
                )
            } else {
                getNewPath(metadata, currentPath)
            }

            withHandledError(
                fs.copy(
                    path = metadata.absolutePath,
                    newPath = targetPath,
                    shouldOverride = shouldOverride
                )
            ) {
                // success handling if needed
            }
        }
    }
    private fun moveItem(
        metadata: LunarMetaData,
        destinationPath: String,
        shouldOverride: Boolean = false
    ) {
        viewModelScope.launch {
            withHandledError(
                fs.move(
                    path = metadata.absolutePath,
                    newPath = getNewPath(metadata, destinationPath),
                    shouldOverride = shouldOverride
                )
            ) {
                // success
            }
        }
    }
    private fun doMove(clipboardState: ClipboardState) {
        if (_state.value.pathHistory.isEmpty()) return

        val currentPath = _state.value.pathHistory.last()
        var applyToAllAction: ((LunarMetaData) -> Unit)? = null

        clipboardState.items.forEach { metadata ->
            val collision = findCollisions(
                metaData = metadata,
                currentPath = currentPath,
                currentFolderContents = _state.value.currentFolderContent
            )

            when (collision) {
                CollisionType.NONE -> {
                    moveItem(metadata, currentPath)
                }

                CollisionType.PASTE_INTO_SELF -> {
                    // Skip silently
                }

                CollisionType.NAME_CONFLICT -> {
                    if (applyToAllAction != null) {
                        applyToAllAction?.invoke(metadata)
                    } else {
                        _state.update {
                            it.copy(
                                pendingNameConflict = NameConflictRequest(
                                    originalName = metadata.name,
                                    originalPath = metadata.absolutePath,
                                    destinationPath = currentPath,
                                    onReplace = { all ->
                                        moveItem(metadata, currentPath, shouldOverride = true)
                                        if (all) applyToAllAction = { m ->
                                            moveItem(m, currentPath, shouldOverride = true)
                                        }
                                        clearPendingConflict()
                                    },
                                    onKeepBoth = { all ->
                                        val newPath = generateNonConflictingPath(
                                            baseDir = currentPath,
                                            originalName = metadata.name,
                                            existingItems = _state.value.currentFolderContent
                                        )
                                        moveItem(metadata, newPath)
                                        if (all) applyToAllAction = { m ->
                                            val np = generateNonConflictingPath(
                                                baseDir = currentPath,
                                                originalName = m.name,
                                                existingItems = _state.value.currentFolderContent
                                            )
                                            moveItem(m, np)
                                        }
                                        clearPendingConflict()
                                    },
                                    onSkip = { all ->
                                        if (all) applyToAllAction = { /* skip */ }
                                        clearPendingConflict()
                                    }
                                )
                            )
                        }
                        return
                    }
                }
            }
        }

        onRefresh()
    }
    private fun clearPendingConflict() {
        _state.update { it.copy(pendingNameConflict = null) }
    }



    private fun doCopy(clipboardState: ClipboardState, startIndex: Int = 0, applyToAll: ((LunarMetaData) -> Unit)? = null) {
        if (_state.value.pathHistory.isEmpty()) return

        val currentPath = _state.value.pathHistory.last()
        var applyToAllAction = applyToAll

        val items = clipboardState.items
        if (startIndex >= items.size) {
            onRefresh()
            return
        }

        val metadata = items[startIndex]
        val collision = findCollisions(metadata, currentPath, _state.value.currentFolderContent)

        when (collision) {
            CollisionType.NONE -> {
                copyItem(metadata, currentPath)
                doCopy(clipboardState, startIndex + 1, applyToAllAction)
            }

            CollisionType.PASTE_INTO_SELF -> {
                doCopy(clipboardState, startIndex + 1, applyToAllAction)
            }

            CollisionType.NAME_CONFLICT -> {
                if (applyToAllAction != null) {
                    applyToAllAction(metadata)
                    doCopy(clipboardState, startIndex + 1, applyToAllAction)
                } else {
                    _state.update {
                        it.copy(
                            pendingNameConflict = NameConflictRequest(
                                originalName = metadata.name,
                                originalPath = metadata.absolutePath,
                                destinationPath = currentPath,
                                onReplace = { all ->
                                    copyItem(metadata, currentPath, shouldOverride = true)
                                    if (all) applyToAllAction = { m ->
                                        copyItem(m, currentPath, shouldOverride = true)
                                    }
                                    _state.update { s -> s.copy(pendingNameConflict = null) }
                                    doCopy(clipboardState, startIndex + 1, applyToAllAction)
                                },
                                onKeepBoth = { all ->
                                    copyItem(metadata, currentPath, generateUniqueName = true)
                                    if (all) applyToAllAction = { m ->
                                        copyItem(m, currentPath, generateUniqueName = true)
                                    }
                                    _state.update { s -> s.copy(pendingNameConflict = null) }
                                    doCopy(clipboardState, startIndex + 1, applyToAllAction)
                                },
                                onSkip = { all ->
                                    if (all) applyToAllAction = { /* skip */ }
                                    _state.update { s -> s.copy(pendingNameConflict = null) }
                                    doCopy(clipboardState, startIndex + 1, applyToAllAction)
                                }
                            )
                        )
                    }
                }
            }
        }
    }




    private fun onOptionClicked(
        explorerOptions: ExplorerOptions,
        metaData: LunarMetaData
    ) {
        when (explorerOptions.type) {
            ExplorerOptionType.MOVE -> onSingleMoveSelected(metaData)
            ExplorerOptionType.COPY -> onSingleCopySelected(metaData)
            ExplorerOptionType.DELETE -> onDelete(metaData)
            ExplorerOptionType.RENAME -> onRename(metaData)
            ExplorerOptionType.DOWNLOAD -> onDownload(metaData)
        }
        onExtraOptionDismissed()
    }

    private fun onDownload(metaData: LunarMetaData) {
        if (metaData.entryType == EntryType.DIRECTORY) return
        Toast.makeText(get().get(), "WARNING: This feature is Experimental", Toast.LENGTH_SHORT).show()
        if (metaData.size >= 15000000){
            Toast.makeText(get().get(), "File May Be too Big TO Download At ONCE", Toast.LENGTH_SHORT).show()

        }
        viewModelScope.launch {
            withHandledError(fs.getCompleteFile(metaData.absolutePath)){
//                Toast.makeText(get().get(), it.size.toString(), Toast.LENGTH_SHORT).show()
                try {
                    val savedUri = saveBytesToDownloadsWithGuessedMime(get().get(), it, metaData.name)
                    // savedUri is a content:// URI pointing to the file in Downloads
                    showSnackbar("Downloaded")
                } catch (e: Exception) {
                    // handle error
                    e.printStackTrace()
                    showSnackbar("Download Failed")
                }
            }
        }
    }

    private fun onRename(metaData: LunarMetaData) {
        val basePath = getBasePath(metaData)
        pendingNameRequest = PendingNameRequest(
            type = if(metaData.entryType == EntryType.DIRECTORY){
                NameRequestType.FOLDER
            }else{
                NameRequestType.FILE
            },
            basePath = basePath,
            action = NameRequestAction.RENAME,
            currentName = metaData.name
        ){ name->
            viewModelScope.launch {
                withHandledError(fs.rename(metaData.absolutePath, "$basePath/$name",false)){
                    showSnackbar("Renamed from ${metaData.name} to $name")
                    onRefresh()
                }
            }
        }
    }

    private fun onDelete(metaData: LunarMetaData) {
        viewModelScope.launch {
            withHandledError(fs.delete(metaData.absolutePath, false)){
                val list = _state.value.currentFolderContent.toMutableList()
                list.removeIf { it.absolutePath == metaData.absolutePath }
                _state.update {
                    it.copy(
                        currentFolderContent = list
                    )
                }
                showSnackbar("${metaData.name} Deleted", Icons.Default.Check)
            }
        }
    }
    fun showSnackbar(text: String, icon: ImageVector? = null) {
        _state.update {
            it.copy(snackbarMessage = SnackbarMessage(text, icon))
        }
    }

    fun clearSnackbar() {
        _state.update {
            it.copy(snackbarMessage = null)
        }
    }

    private fun onSingleMoveSelected(metaData: LunarMetaData) {
        clearClipBoard()
        _state.update {
            it.copy(
                clipboard = ClipboardState(
                    actionType = ClipboardActionType.MOVE,
                    items = listOf(metaData)
                )
            )
        }
    }

    private fun clearClipBoard() {
        _state.update {
            it.copy(
                clipboard = null
            )
        }
    }

    private fun onSingleCopySelected(metaData: LunarMetaData): Unit {
        clearClipBoard()
        _state.update {
            it.copy(
                clipboard = ClipboardState(
                    actionType = ClipboardActionType.COPY,
                    items = listOf(metaData)
                )
            )
        }
    }

    private fun onExtraOptionDismissed() {
        if (_state.value.showOptions == null) {
            return
        }
        _state.update {
            it.copy(
                showOptions = null
            )
        }
    }

    private fun onLongPressed(metaData: LunarMetaData) {
        showExtraOptions(metaData)
    }

    private fun onItemClicked(metaData: LunarMetaData) {
        if (metaData.entryType == EntryType.DIRECTORY) {
//            onAction(ExplorerActions.GoToPath(.absolutePath))
            onGoToPath(metaData.absolutePath)
        } else {
            showExtraOptions(metaData)
        }
    }

    private fun showExtraOptions(metaData: LunarMetaData) {
        _state.update {
            it.copy(
                showOptions = metaData
            )
        }
    }

    private fun onSearchDismissed() {
        _state.update {
            it.copy(
                showSearch = false,
                filteredFolderContent = emptyList()
            )
        }
    }

    private fun onSearchTextChange(search: String) {
        _state.update {
            it.copy(
                filteredFolderContent = if (search.isBlank()) {
                    it.currentFolderContent
                } else {
                    it.currentFolderContent.filter { data ->
                        data.name.contains(search, ignoreCase = true)
                    }
                }
            )
        }
    }


    private fun onActionButtonPressed(actionType: AppBarActionEnum) {
        when (actionType) {
            AppBarActionEnum.CREATE_FILE -> {
                val path = _state.value.pathHistory.lastOrNull() ?: return
                requestNameForFile(path)
            }

            AppBarActionEnum.CREATE_FOLDER -> {
                val path = _state.value.pathHistory.lastOrNull() ?: return
                requestNameForFolder(path)
            }

            AppBarActionEnum.SEARCH -> {
                _state.update {
                    it.copy(
                        showSearch = true
                    )
                }
            }

            else -> {}
        }

    }


    fun requestNameForFile(basePath: String) {
        pendingNameRequest = PendingNameRequest(
            type = NameRequestType.FILE,
            basePath = basePath
        ) { fileName ->
            createFsFile("${basePath}\\$fileName")
        }
    }

    fun requestNameForFolder(basePath: String) {
        pendingNameRequest = PendingNameRequest(
            type = NameRequestType.FOLDER,
            basePath = basePath
        ) { folderName ->
            createFsFolder("${basePath}\\$folderName")
        }
    }

    fun confirmName(name: String) {
        pendingNameRequest?.onConfirm?.invoke(name)
        pendingNameRequest = null
    }

    fun dismissNameRequest() {
        pendingNameRequest = null
    }

    private fun createFsFile(path: String) {
        viewModelScope.launch {

            withRefreshing {
                withHandledError(fs.createFile(path)) { data ->
                    val list = _state.value.currentFolderContent.toMutableList()
                    list.add(data)
                    _state.update {
                        it.copy(
                            currentFolderContent = list
                        )
                    }
                }
            }
        }
    }

    private fun createFsFolder(path: String) {
        viewModelScope.launch {
            withRefreshing {
                withHandledError(fs.createFolder(path)) { data ->
                    val list = _state.value.currentFolderContent.toMutableList()
                    list.add(data)
                    _state.update {
                        it.copy(
                            currentFolderContent = list
                        )
                    }
                }
            }
        }
    }

    private fun onBackPressed() {
        onExtraOptionDismissed()
        if (!_state.value.pathHistory.isEmpty()) {
            val list = _state.value.pathHistory.toMutableList()
            list.removeAt(list.lastIndex)
            _state.update {
                it.copy(
                    pathHistory = list
                )
            }
            onRefresh()
        }
    }

    private fun onGoToPath(path: String) {
        onSearchDismissed()
        if (!_state.value.pathHistory.isEmpty()) {
            if (_state.value.pathHistory.last() == path) {
                return
            }
        }
        val list = _state.value.pathHistory.toMutableList()
        list.add(path)
        _state.update {
            it.copy(
                pathHistory = list
            )
        }
        onRefresh()
    }

    fun onRefresh() {
        // TODO: Add full path history and its content in state to avoid refetching
        onExtraOptionDismissed()
        if (_state.value.pathHistory.isEmpty()) {
            return
        }
        viewModelScope.launch {
            withRefreshing {
                withHandledError(fs.getFolderContent(_state.value.pathHistory.last())) { content ->
                    _state.update { it ->
                        it.copy(
                            currentFolderContent = content.entries.sortedBy { ent -> ent.entryType },
                            currentFolderInfo = content.metaData,
                        )
                    }
                }
            }
        }

    }

    private inline fun <D, E : RootError> withHandledError(
        result: LunarResult<D, E>,
        block: (data: D) -> Unit
    ) {
        if (result.isError()) {
            _state.update {
                it.copy(
                    error = result.getErrorOrNull().toString()
                )
            }
        } else {
            block(result.successOrNull()!!)
        }
    }

    private inline fun withRefreshing(block: () -> Unit) {
        _state.update {
            it.copy(
                isLoading = true
            )
        }
        block()
        _state.update {
            it.copy(
                isLoading = false
            )
        }
    }

    fun consumeError() {
        _state.update {
            it.copy(
                error = null
            )
        }
    }
}
package io.github.realmlabs.yggdrasil.application.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.realmlabs.yggdrasil.application.workflow.ZNodeWorkflowService
import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ConnectionProfileRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZooKeeperConnectionTester
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

class YggdrasilStateHolder(
    private val connectionProfileRepository: ConnectionProfileRepository? = null,
    private val zooKeeperConnectionTester: ZooKeeperConnectionTester? = null,
    private val zNodeRepository: ZNodeRepository? = null,
    initialState: AppState = AppState(),
) {
    var state by mutableStateOf(initialState)
        private set

    suspend fun loadConnections() {
        val repository = connectionProfileRepository ?: return

        state = state.copy(isLoadingConnections = true, statusMessage = "Loading connections")
        when (val result = repository.loadProfiles()) {
            is OperationResult.Success -> {
                state = state.copy(
                    connections = result.value,
                    activeConnectionId = state.activeConnectionId?.takeIf { activeId ->
                        result.value.any { it.id == activeId }
                    },
                    isLoadingConnections = false,
                    statusMessage = if (result.value.isEmpty()) {
                        "No saved connections"
                    } else {
                        "Loaded ${result.value.size} connection${if (result.value.size == 1) "" else "s"}"
                    },
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(isLoadingConnections = false, statusMessage = result.error.message)
            }
        }
    }

    fun setConnections(connections: List<ConnectionProfile>) {
        val activeConnectionId = state.activeConnectionId?.takeIf { activeId ->
            connections.any { it.id == activeId }
        }
        if (activeConnectionId == null) {
            state.activeConnectionId?.let { zNodeRepository?.closeConnection(it) }
        }

        state = state.copy(
            connections = connections,
            activeConnectionId = activeConnectionId,
            nodeSelection = if (activeConnectionId == null) NodeSelectionState.None else state.nodeSelection,
            znodeChildren = if (activeConnectionId == null) emptyMap() else state.znodeChildren,
            nodeDetail = if (activeConnectionId == null) ZNodeDetailState.None else state.nodeDetail,
            deletePreview = if (activeConnectionId == null) DeletePreviewState.None else state.deletePreview,
            watchState = if (activeConnectionId == null) ZNodeWatchState() else state.watchState,
            searchState = if (activeConnectionId == null) ZNodeSearchState.Idle else state.searchState,
            exportState = if (activeConnectionId == null) ZNodeExportState.Idle else state.exportState,
            importState = if (activeConnectionId == null) ZNodeImportState.Idle else state.importState,
            compareState = if (activeConnectionId == null) ZNodeCompareState.Idle else state.compareState,
        )
    }

    suspend fun selectConnection(connectionId: ConnectionId) {
        if (state.connections.none { it.id == connectionId }) return
        state.activeConnectionId
            ?.takeIf { it != connectionId }
            ?.let { zNodeRepository?.closeConnection(it) }

        val connection = state.connections.first { it.id == connectionId }
        state = state.copy(
            activeConnectionId = connectionId,
            nodeSelection = NodeSelectionState.None,
            znodeChildren = emptyMap(),
            nodeDetail = ZNodeDetailState.None,
            deletePreview = DeletePreviewState.None,
            watchState = ZNodeWatchState(),
            searchState = ZNodeSearchState.Idle,
            exportState = ZNodeExportState.Idle,
            importState = ZNodeImportState.Idle,
            compareState = ZNodeCompareState.Idle,
            statusMessage = "Selected ${connection.name}",
        )
        selectPath(ZNodePath.Root)
    }

    suspend fun createConnection(draft: ConnectionProfileDraft) {
        val repository = connectionProfileRepository ?: return
        val profile = when (val result = draft.toProfile(ConnectionId(generateConnectionId()))) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> {
                reportError(result.error)
                return
            }
        }

        when (val result = repository.saveProfile(profile)) {
            is OperationResult.Success -> {
                state.activeConnectionId
                    ?.takeIf { it != profile.id }
                    ?.let { zNodeRepository?.closeConnection(it) }
                val nextConnections = state.connections
                    .filterNot { it.id == profile.id }
                    .plus(profile)
                    .sortedBy { it.name.lowercase() }

                state = state.copy(
                    connections = nextConnections,
                    activeConnectionId = profile.id,
                    nodeSelection = NodeSelectionState.None,
                    znodeChildren = emptyMap(),
                    nodeDetail = ZNodeDetailState.None,
                    watchState = ZNodeWatchState(),
                    searchState = ZNodeSearchState.Idle,
                    exportState = ZNodeExportState.Idle,
                    importState = ZNodeImportState.Idle,
                    compareState = ZNodeCompareState.Idle,
                    statusMessage = "Saved ${profile.name}",
                )
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    suspend fun deleteConnection(connectionId: ConnectionId) {
        val repository = connectionProfileRepository ?: return
        val connection = state.connections.firstOrNull { it.id == connectionId } ?: return

        when (val result = repository.deleteProfile(connectionId)) {
            is OperationResult.Success -> {
                zNodeRepository?.closeConnection(connectionId)
                state = state.copy(
                    connections = state.connections.filterNot { it.id == connectionId },
                    activeConnectionId = state.activeConnectionId?.takeIf { it != connectionId },
                    connectionStatuses = state.connectionStatuses - connectionId,
                    nodeSelection = if (state.activeConnectionId == connectionId) {
                        NodeSelectionState.None
                    } else {
                        state.nodeSelection
                    },
                    znodeChildren = if (state.activeConnectionId == connectionId) emptyMap() else state.znodeChildren,
                    nodeDetail = if (state.activeConnectionId == connectionId) ZNodeDetailState.None else state.nodeDetail,
                    deletePreview = if (state.activeConnectionId == connectionId) DeletePreviewState.None else state.deletePreview,
                    watchState = if (state.activeConnectionId == connectionId) ZNodeWatchState() else state.watchState,
                    searchState = if (state.activeConnectionId == connectionId) ZNodeSearchState.Idle else state.searchState,
                    exportState = if (state.activeConnectionId == connectionId) ZNodeExportState.Idle else state.exportState,
                    importState = if (state.activeConnectionId == connectionId) ZNodeImportState.Idle else state.importState,
                    compareState = if (state.activeConnectionId == connectionId) ZNodeCompareState.Idle else state.compareState,
                    statusMessage = "Deleted ${connection.name}",
                )
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    suspend fun testConnection(connectionId: ConnectionId) {
        val tester = zooKeeperConnectionTester ?: return
        val profile = state.connections.firstOrNull { it.id == connectionId } ?: return

        state = state.copy(
            connectionStatuses = state.connectionStatuses + (connectionId to ConnectionRuntimeStatus.Connecting),
            statusMessage = "Testing ${profile.name}",
        )

        when (val result = tester.testConnection(profile)) {
            is OperationResult.Success -> {
                state = state.copy(
                    connectionStatuses = state.connectionStatuses + (connectionId to ConnectionRuntimeStatus.Connected),
                    statusMessage = "Connected to ${profile.name}",
                )
                if (state.activeConnectionId == connectionId && state.selectedPath == null) {
                    selectPath(ZNodePath.Root)
                }
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    connectionStatuses = state.connectionStatuses + (connectionId to ConnectionRuntimeStatus.Failed(result.error)),
                    statusMessage = result.error.message,
                )
            }
        }
    }

    suspend fun selectPath(path: ZNodePath) {
        val currentDetail = state.nodeDetail
        state = state.copy(
            nodeSelection = NodeSelectionState.Loading(path),
            nodeDetail = if (currentDetail is ZNodeDetailState.Loaded) state.nodeDetail else ZNodeDetailState.Loading(
                path
            ),
            deletePreview = DeletePreviewState.None,
            watchState = ZNodeWatchState(watchedPath = path),
            statusMessage = "Loading $path",
        )
        loadDetail(path)
        loadChildren(path)
        if (state.nodeSelection is NodeSelectionState.Loading && state.selectedPath == path) {
            state = state.copy(
                nodeSelection = NodeSelectionState.SelectedPath(path),
                statusMessage = "Loaded $path",
            )
        }
    }

    suspend fun loadChildren(path: ZNodePath) {
        loadChildren(
            path = path,
            showLoadingState = true,
            updateStatus = true,
            prefetchVisibleChildren = true,
        )
    }

    private suspend fun loadChildren(
        path: ZNodePath,
        showLoadingState: Boolean,
        updateStatus: Boolean,
        prefetchVisibleChildren: Boolean,
    ) {
        val repository = zNodeRepository ?: return
        val profile = state.activeConnection ?: return
        val currentChildrenState = state.znodeChildren[path]

        if (showLoadingState || updateStatus) {
            state = state.copy(
                znodeChildren = if (!showLoadingState || currentChildrenState is ZNodeChildrenState.Loaded) {
                    state.znodeChildren
                } else {
                    state.znodeChildren + (path to ZNodeChildrenState.Loading)
                },
                statusMessage = if (updateStatus) "Loading children for $path" else state.statusMessage,
            )
        }

        when (val result = repository.loadChildren(profile, path)) {
            is OperationResult.Success -> {
                state = state.copy(
                    znodeChildren = state.znodeChildren + (path to ZNodeChildrenState.Loaded(result.value)),
                    connectionStatuses = state.connectionStatuses + (profile.id to ConnectionRuntimeStatus.Connected),
                    statusMessage = if (updateStatus) {
                        "Loaded ${result.value.size} child${if (result.value.size == 1) "" else "ren"} for $path"
                    } else {
                        state.statusMessage
                    },
                )
                if (prefetchVisibleChildren) {
                    prefetchChildrenOfVisibleNodes(result.value)
                }
            }

            is OperationResult.Failure -> {
                if (showLoadingState || updateStatus || currentChildrenState !is ZNodeChildrenState.Loaded) {
                    state = state.copy(
                        znodeChildren = if (currentChildrenState is ZNodeChildrenState.Loaded) {
                            state.znodeChildren
                        } else {
                            state.znodeChildren + (path to ZNodeChildrenState.Failed(result.error))
                        },
                        statusMessage = if (updateStatus) result.error.message else state.statusMessage,
                    )
                }
            }
        }
    }

    private suspend fun prefetchChildrenOfVisibleNodes(visibleNodes: List<ZNodeSummary>) {
        visibleNodes
            .filter { it.hasChildren }
            .filter { child ->
                state.znodeChildren[child.path] !is ZNodeChildrenState.Loaded &&
                        state.znodeChildren[child.path] !is ZNodeChildrenState.Loading
            }
            .forEach { child ->
                loadChildren(
                    path = child.path,
                    showLoadingState = false,
                    updateStatus = false,
                    prefetchVisibleChildren = false,
                )
            }
    }

    suspend fun refreshSelectedPath() {
        val path = state.selectedPath ?: return
        loadDetail(path)
        loadChildren(path)
    }

    suspend fun createNode(request: CreateZNodeRequest) {
        val repository = zNodeRepository ?: return
        val profile = requireWritableProfile() ?: return
        if (request.path == ZNodePath.Root) {
            reportError(AppError.Validation("Cannot create the root znode."))
            return
        }

        state = state.copy(statusMessage = "Creating ${request.path}")
        when (val result = repository.createNode(profile, request)) {
            is OperationResult.Success -> {
                result.value.parent?.let { loadChildren(it) }
                selectPath(result.value)
                state = state.copy(statusMessage = "Created ${result.value}")
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    suspend fun updateSelectedNodeData(data: ByteArray, expectedVersion: Int) {
        val repository = zNodeRepository ?: return
        val profile = requireWritableProfile() ?: return
        val path = state.selectedPath ?: return

        state = state.copy(statusMessage = "Saving data for $path")
        val request = UpdateZNodeDataRequest(
            path = path,
            data = data,
            expectedVersion = expectedVersion,
        )

        when (val result = repository.updateData(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    nodeDetail = ZNodeDetailState.Loaded(result.value),
                    statusMessage = "Saved data for $path",
                )
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    suspend fun previewDeleteSelectedNode(recursive: Boolean) {
        val repository = zNodeRepository ?: return
        val profile = requireWritableProfile() ?: return
        val path = state.selectedPath ?: return
        if (path == ZNodePath.Root) {
            val error = AppError.Validation("Deleting the root znode is not supported.")
            state = state.copy(deletePreview = DeletePreviewState.Failed(path, error), statusMessage = error.message)
            return
        }

        state = state.copy(
            deletePreview = DeletePreviewState.Loading(path, recursive),
            statusMessage = "Previewing delete for $path",
        )
        val request = DeleteZNodeRequest(path = path, recursive = recursive)

        when (val result = repository.previewDelete(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    deletePreview = DeletePreviewState.Loaded(result.value),
                    statusMessage = "Previewed delete for ${result.value.paths.size} node${if (result.value.paths.size == 1) "" else "s"}",
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    deletePreview = DeletePreviewState.Failed(path, result.error),
                    statusMessage = result.error.message,
                )
            }
        }
    }

    suspend fun deletePreviewedNode(confirmation: String) {
        val repository = zNodeRepository ?: return
        val profile = requireWritableProfile() ?: return
        val preview = (state.deletePreview as? DeletePreviewState.Loaded)?.preview ?: return
        if (preview.requiresFullPathConfirmation && confirmation != preview.rootPath.value) {
            reportError(AppError.Validation("Type ${preview.rootPath} to confirm deletion."))
            return
        }

        state = state.copy(statusMessage = "Deleting ${preview.rootPath}")
        val request = DeleteZNodeRequest(
            path = preview.rootPath,
            recursive = preview.recursive,
        )

        when (val result = repository.deleteNode(profile, request)) {
            is OperationResult.Success -> {
                val parent = preview.rootPath.parent
                state = state.copy(
                    nodeSelection = NodeSelectionState.None,
                    nodeDetail = ZNodeDetailState.None,
                    deletePreview = DeletePreviewState.None,
                    watchState = ZNodeWatchState(),
                    statusMessage = "Deleted ${preview.rootPath}",
                )
                parent?.let { loadChildren(it) }
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    fun clearDeletePreview() {
        state = state.copy(deletePreview = DeletePreviewState.None)
    }

    suspend fun updateSelectedAcl(acl: List<ZNodeAcl>, expectedAversion: Int) {
        val repository = zNodeRepository ?: return
        val profile = requireWritableProfile() ?: return
        val path = state.selectedPath ?: return
        val validationError = acl.firstOrNull { entry ->
            entry.scheme.isBlank() || entry.id.isBlank() || entry.permissions.isEmpty()
        }
        if (acl.isEmpty() || validationError != null) {
            reportError(AppError.Validation("ACL entries require scheme, id, and at least one permission."))
            return
        }

        state = state.copy(statusMessage = "Saving ACL for $path")
        val request = UpdateZNodeAclRequest(
            path = path,
            acl = acl,
            expectedAversion = expectedAversion,
        )

        when (val result = repository.updateAcl(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    nodeDetail = ZNodeDetailState.Loaded(result.value),
                    statusMessage = "Saved ACL for $path",
                )
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    suspend fun searchZNodes(request: ZNodeSearchRequest) {
        val repository = zNodeRepository ?: return
        val profile = state.activeConnection ?: run {
            reportError(AppError.Connection("Select a ZooKeeper connection first."))
            return
        }
        val service = ZNodeWorkflowService(repository)

        state = state.copy(
            searchState = ZNodeSearchState.Running(request),
            statusMessage = "Searching from ${request.rootPath}",
        )
        when (val result = service.search(profile, request) { scanned ->
            state = state.copy(
                searchState = ZNodeSearchState.Running(request, scanned),
                statusMessage = "Searching from ${request.rootPath} · scanned $scanned",
            )
        }) {
            is OperationResult.Success -> {
                state = state.copy(
                    searchState = ZNodeSearchState.Loaded(result.value),
                    statusMessage = "Search found ${result.value.hits.size} hit${if (result.value.hits.size == 1) "" else "s"} · scanned ${result.value.scannedNodes}",
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    searchState = ZNodeSearchState.Failed(request, result.error),
                    statusMessage = result.error.message,
                )
            }
        }
    }

    fun markSearchCanceled() {
        val running = state.searchState as? ZNodeSearchState.Running ?: return
        state = state.copy(
            searchState = ZNodeSearchState.Loaded(
                ZNodeSearchReport(
                    request = running.request,
                    hits = emptyList(),
                    scannedNodes = running.scannedNodes,
                    stopReason = ZNodeTraversalStopReason.Canceled,
                ),
            ),
            statusMessage = "Search canceled · scanned ${running.scannedNodes}",
        )
    }

    suspend fun exportSelectedSubtree(
        includeAcl: Boolean,
        dataEncoding: ZNodeDataEncoding,
    ) {
        val repository = zNodeRepository ?: return
        val profile = state.activeConnection ?: run {
            reportError(AppError.Connection("Select a ZooKeeper connection first."))
            return
        }
        val path = state.selectedPath ?: run {
            reportError(AppError.Validation("Select a root path to export."))
            return
        }
        val request = ZNodeExportRequest(rootPath = path, includeAcl = includeAcl, dataEncoding = dataEncoding)
        val service = ZNodeWorkflowService(repository)

        state = state.copy(exportState = ZNodeExportState.Running(request), statusMessage = "Exporting $path")
        when (val result = service.exportSubtree(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    exportState = ZNodeExportState.Loaded(result.value),
                    statusMessage = "Exported ${result.value.exportedNodes} node${if (result.value.exportedNodes == 1) "" else "s"} from $path",
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    exportState = ZNodeExportState.Failed(request, result.error),
                    statusMessage = result.error.message,
                )
            }
        }
    }

    suspend fun importZNodeTree(request: ZNodeImportRequest) {
        val repository = zNodeRepository ?: return
        val profile = requireWritableProfile() ?: return
        val service = ZNodeWorkflowService(repository)

        state = state.copy(
            importState = ZNodeImportState.Running(request),
            statusMessage = if (request.dryRun) "Planning import" else "Importing znodes",
        )
        when (val result = service.importSubtree(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    importState = ZNodeImportState.Loaded(result.value),
                    statusMessage = if (request.dryRun) {
                        "Import dry run planned ${result.value.operations.size} operation${if (result.value.operations.size == 1) "" else "s"}"
                    } else {
                        "Import completed · applied ${result.value.appliedCount}, failed ${result.value.failureCount}"
                    },
                )
                state.selectedPath?.let { refreshSelectedPath() }
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    importState = ZNodeImportState.Failed(request, result.error),
                    statusMessage = result.error.message,
                )
            }
        }
    }

    suspend fun compareConnections(request: ZNodeCompareRequest) {
        val repository = zNodeRepository ?: return
        val leftProfile = state.connections.firstOrNull { it.id == request.leftConnectionId }
        val rightProfile = state.connections.firstOrNull { it.id == request.rightConnectionId }
        if (leftProfile == null || rightProfile == null) {
            reportError(AppError.Validation("Select two saved connections to compare."))
            return
        }
        val service = ZNodeWorkflowService(repository)

        state = state.copy(
            compareState = ZNodeCompareState.Running(request),
            statusMessage = "Comparing ${leftProfile.name} and ${rightProfile.name}",
        )
        when (val result = service.compare(leftProfile, rightProfile, request) { scanned ->
            state = state.copy(
                compareState = ZNodeCompareState.Running(request, scanned),
                statusMessage = "Comparing trees · scanned $scanned",
            )
        }) {
            is OperationResult.Success -> {
                state = state.copy(
                    compareState = ZNodeCompareState.Loaded(result.value),
                    statusMessage = "Compare found ${result.value.differences.size} difference${if (result.value.differences.size == 1) "" else "s"} · scanned ${result.value.scannedNodes}",
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    compareState = ZNodeCompareState.Failed(request, result.error),
                    statusMessage = result.error.message,
                )
            }
        }
    }

    fun markCompareCanceled() {
        val running = state.compareState as? ZNodeCompareState.Running ?: return
        state = state.copy(
            compareState = ZNodeCompareState.Loaded(
                ZNodeCompareReport(
                    request = running.request,
                    differences = emptyList(),
                    scannedNodes = running.scannedNodes,
                    stopReason = ZNodeTraversalStopReason.Canceled,
                ),
            ),
            statusMessage = "Compare canceled · scanned ${running.scannedNodes}",
        )
    }

    fun watchSelectedPath(): Flow<ZNodeWatchEvent>? {
        val repository = zNodeRepository ?: return null
        val profile = state.activeConnection ?: return null
        val path = state.selectedPath ?: return null
        return repository.watch(profile, path)
    }

    suspend fun processWatchEvent(event: ZNodeWatchEvent) {
        state = state.copy(
            watchState = state.watchState.copy(lastEvent = event, error = null),
            statusMessage = "Watch event ${event.type} on ${event.path}",
        )
        refreshSelectedPath()
    }

    fun reportWatchError(error: AppError) {
        state = state.copy(
            watchState = state.watchState.copy(error = error),
            statusMessage = error.message,
        )
    }

    fun clearSelection() {
        state = state.copy(
            nodeSelection = NodeSelectionState.None,
            nodeDetail = ZNodeDetailState.None,
            deletePreview = DeletePreviewState.None,
            watchState = ZNodeWatchState(),
            statusMessage = "Selection cleared",
        )
    }

    fun reportError(error: AppError) {
        state = state.copy(statusMessage = error.message)
    }

    private fun requireWritableProfile(): ConnectionProfile? {
        val profile = state.activeConnection
        return when {
            profile == null -> {
                reportError(AppError.Connection("Select a ZooKeeper connection first."))
                null
            }

            profile.mode != ConnectionMode.ReadWrite -> {
                reportError(AppError.Validation("This connection is read only."))
                null
            }

            else -> profile
        }
    }

    private fun generateConnectionId(): String =
        "conn-${Random.nextLong(0, Long.MAX_VALUE).toString(16)}"

    private suspend fun loadDetail(path: ZNodePath) {
        val repository = zNodeRepository ?: return
        val profile = state.activeConnection ?: return
        val currentDetail = state.nodeDetail

        state = state.copy(
            nodeDetail = if (currentDetail is ZNodeDetailState.Loaded) {
                currentDetail
            } else {
                ZNodeDetailState.Loading(path)
            },
            statusMessage = "Loading detail for $path",
        )

        when (val result = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> {
                state = state.copy(
                    nodeDetail = ZNodeDetailState.Loaded(result.value),
                    connectionStatuses = state.connectionStatuses + (profile.id to ConnectionRuntimeStatus.Connected),
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    nodeSelection = NodeSelectionState.Failed(path, result.error),
                    nodeDetail = ZNodeDetailState.Failed(path, result.error),
                    statusMessage = result.error.message,
                )
            }
        }
    }
}

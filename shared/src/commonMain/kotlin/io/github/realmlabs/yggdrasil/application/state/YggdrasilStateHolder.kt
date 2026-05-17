package io.github.realmlabs.yggdrasil.application.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.realmlabs.yggdrasil.application.workflow.ZNodeWorkflowService
import io.github.realmlabs.yggdrasil.application.workflow.ZkCliCommandService
import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

class YggdrasilStateHolder(
    private val appSettingsRepository: AppSettingsRepository? = null,
    private val connectionProfileRepository: ConnectionProfileRepository? = null,
    private val credentialRepository: CredentialRepository? = null,
    private val zooKeeperConnectionTester: ZooKeeperConnectionTester? = null,
    private val zNodeRepository: ZNodeRepository? = null,
    initialState: AppState = AppState(),
) {
    var state by mutableStateOf(initialState)
        private set

    suspend fun loadSettings() {
        val repository = appSettingsRepository ?: return
        when (val result = repository.loadSettings()) {
            is OperationResult.Success -> {
                state = state.copy(settings = result.value)
            }

            is OperationResult.Failure -> {
                state = state.copy(statusMessage = StatusMessage.Error(result.error))
            }
        }
    }

    suspend fun loadConnections() {
        val repository = connectionProfileRepository ?: return

        state = state.copy(isLoadingConnections = true, statusMessage = StatusMessage.LoadingConnections)
        when (val result = repository.loadProfiles()) {
            is OperationResult.Success -> {
                state = state.copy(
                    connections = result.value,
                    activeConnectionId = state.activeConnectionId?.takeIf { activeId ->
                        result.value.any { it.id == activeId }
                    },
                    isLoadingConnections = false,
                    statusMessage = if (result.value.isEmpty()) {
                        StatusMessage.NoSavedConnections
                    } else {
                        StatusMessage.LoadedConnections(result.value.size)
                    },
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(isLoadingConnections = false, statusMessage = StatusMessage.Error(result.error))
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
            zkCliState = if (activeConnectionId == null) ZkCliState.Idle else state.zkCliState,
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
            zkCliState = ZkCliState.Idle,
            statusMessage = StatusMessage.SelectedConnection(connection.name),
        )
        if (state.settings.startAtRoot) {
            selectPath(ZNodePath.Root)
        }
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

        when (val result = saveConnectionSecretsIfNeeded(profile, draft)) {
            is OperationResult.Success -> Unit
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
                    zkCliState = ZkCliState.Idle,
                    statusMessage = StatusMessage.SavedConnection(profile.name),
                )
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    suspend fun updateConnection(
        connectionId: ConnectionId,
        draft: ConnectionProfileDraft,
    ) {
        val repository = connectionProfileRepository ?: return
        val existing = state.connections.firstOrNull { it.id == connectionId } ?: return
        val profile = when (val result = draft.toProfile(connectionId)) {
            is OperationResult.Success -> result.value.copy(
                tags = existing.tags,
            )

            is OperationResult.Failure -> {
                reportError(result.error)
                return
            }
        }

        when (val result = saveConnectionSecretsIfNeeded(profile, draft)) {
            is OperationResult.Success -> Unit
            is OperationResult.Failure -> {
                reportError(result.error)
                return
            }
        }

        when (val result = repository.saveProfile(profile)) {
            is OperationResult.Success -> {
                val nextConnections = state.connections
                    .filterNot { it.id == connectionId }
                    .plus(profile)
                    .sortedBy { it.name.lowercase() }
                val isActive = state.activeConnectionId == connectionId
                if (isActive) {
                    zNodeRepository?.closeConnection(connectionId)
                }

                state = state.copy(
                    connections = nextConnections,
                    connectionStatuses = state.connectionStatuses + (connectionId to ConnectionRuntimeStatus.Disconnected),
                    nodeSelection = if (isActive) NodeSelectionState.None else state.nodeSelection,
                    znodeChildren = if (isActive) emptyMap() else state.znodeChildren,
                    nodeDetail = if (isActive) ZNodeDetailState.None else state.nodeDetail,
                    deletePreview = if (isActive) DeletePreviewState.None else state.deletePreview,
                    watchState = if (isActive) ZNodeWatchState() else state.watchState,
                    searchState = if (isActive) ZNodeSearchState.Idle else state.searchState,
                    exportState = if (isActive) ZNodeExportState.Idle else state.exportState,
                    importState = if (isActive) ZNodeImportState.Idle else state.importState,
                    compareState = if (isActive) ZNodeCompareState.Idle else state.compareState,
                    zkCliState = if (isActive) ZkCliState.Idle else state.zkCliState,
                    statusMessage = StatusMessage.UpdatedConnection(profile.name),
                )
                if (isActive) {
                    selectPath(ZNodePath.Root)
                }
                deleteStaleCredentials(existing, profile)
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
                deleteCredentials(connection.credentialRefs())
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
                    zkCliState = if (state.activeConnectionId == connectionId) ZkCliState.Idle else state.zkCliState,
                    statusMessage = StatusMessage.DeletedConnection(connection.name),
                )
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    private suspend fun saveConnectionSecretsIfNeeded(
        profile: ConnectionProfile,
        draft: ConnectionProfileDraft,
    ): OperationResult<Unit> {
        val credentialsToSave = buildList {
            val sshCredentialRef = profile.sshTunnel?.credentialRef
            if (sshCredentialRef != null && draft.sshSecret.isNotBlank()) {
                add(sshCredentialRef to draft.sshSecret)
            }

            val digestCredentialRef = (profile.security as? ConnectionSecurity.Digest)?.credentialRef
            if (digestCredentialRef != null && draft.zkDigestPassword.isNotBlank()) {
                add(digestCredentialRef to draft.zkDigestPassword)
            }
        }
        if (credentialsToSave.isEmpty()) return OperationResult.Success(Unit)

        val repository = credentialRepository ?: return OperationResult.Failure(
            AppError.Storage("Credential storage is not available."),
        )
        credentialsToSave.forEach { (ref, secret) ->
            when (val result = repository.saveCredential(ref, secret)) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> return result
            }
        }

        return OperationResult.Success(Unit)
    }

    private suspend fun deleteStaleCredentials(
        previous: ConnectionProfile,
        next: ConnectionProfile,
    ) {
        deleteCredentials(previous.credentialRefs() - next.credentialRefs().toSet())
    }

    private suspend fun deleteCredentials(refs: Collection<String>) {
        refs.forEach { ref ->
            credentialRepository?.deleteCredential(ref)
        }
    }

    private fun ConnectionProfile.credentialRefs(): List<String> =
        listOfNotNull(
            sshTunnel?.credentialRef,
            (security as? ConnectionSecurity.Digest)?.credentialRef,
        )

    suspend fun testConnection(connectionId: ConnectionId) {
        val tester = zooKeeperConnectionTester ?: return
        val profile = state.connections.firstOrNull { it.id == connectionId } ?: return

        state = state.copy(
            connectionStatuses = state.connectionStatuses + (connectionId to ConnectionRuntimeStatus.Connecting),
            statusMessage = StatusMessage.TestingConnection(profile.name),
        )

        when (val result = tester.testConnection(profile)) {
            is OperationResult.Success -> {
                state = state.copy(
                    connectionStatuses = state.connectionStatuses + (connectionId to ConnectionRuntimeStatus.Connected),
                    statusMessage = StatusMessage.ConnectedTo(profile.name),
                )
                if (state.activeConnectionId == connectionId && state.selectedPath == null) {
                    selectPath(ZNodePath.Root)
                }
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    connectionStatuses = state.connectionStatuses + (connectionId to ConnectionRuntimeStatus.Failed(result.error)),
                    statusMessage = StatusMessage.Error(result.error),
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
            statusMessage = StatusMessage.LoadingPath(path),
        )
        path.ancestorPaths().forEach { ancestor ->
            if (state.znodeChildren[ancestor] !is ZNodeChildrenState.Loaded) {
                loadChildren(
                    path = ancestor,
                    showLoadingState = false,
                    updateStatus = false,
                    prefetchVisibleChildren = false,
                )
            }
        }
        loadDetail(path)
        loadChildren(path)
        if (state.nodeSelection is NodeSelectionState.Loading && state.selectedPath == path) {
            state = state.copy(
                nodeSelection = NodeSelectionState.SelectedPath(path),
                statusMessage = StatusMessage.LoadedPath(path),
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
                statusMessage = if (updateStatus) StatusMessage.LoadingChildren(path) else state.statusMessage,
            )
        }

        when (val result = repository.loadChildren(profile, path)) {
            is OperationResult.Success -> {
                state = state.copy(
                    znodeChildren = state.znodeChildren + (path to ZNodeChildrenState.Loaded(result.value)),
                    connectionStatuses = state.connectionStatuses + (profile.id to ConnectionRuntimeStatus.Connected),
                    statusMessage = if (updateStatus) {
                        StatusMessage.LoadedChildren(result.value.size, path)
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
                        statusMessage = if (updateStatus) StatusMessage.Error(result.error) else state.statusMessage,
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

        state = state.copy(statusMessage = StatusMessage.CreatingNode(request.path))
        when (val result = repository.createNode(profile, request)) {
            is OperationResult.Success -> {
                result.value.parent?.let { loadChildren(it) }
                selectPath(result.value)
                state = state.copy(statusMessage = StatusMessage.CreatedNode(result.value))
            }

            is OperationResult.Failure -> reportError(result.error)
        }
    }

    suspend fun updateSelectedNodeData(data: ByteArray, expectedVersion: Int) {
        val repository = zNodeRepository ?: return
        val profile = requireWritableProfile() ?: return
        val path = state.selectedPath ?: return

        state = state.copy(statusMessage = StatusMessage.SavingData(path))
        val request = UpdateZNodeDataRequest(
            path = path,
            data = data,
            expectedVersion = expectedVersion,
        )

        when (val result = repository.updateData(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    nodeDetail = ZNodeDetailState.Loaded(result.value),
                    statusMessage = StatusMessage.SavedData(path),
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
            state = state.copy(
                deletePreview = DeletePreviewState.Failed(path, error),
                statusMessage = StatusMessage.Error(error)
            )
            return
        }

        state = state.copy(
            deletePreview = DeletePreviewState.Loading(path, recursive),
            statusMessage = StatusMessage.PreviewingDelete(path),
        )
        val request = DeleteZNodeRequest(path = path, recursive = recursive)

        when (val result = repository.previewDelete(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    deletePreview = DeletePreviewState.Loaded(result.value),
                    statusMessage = StatusMessage.PreviewedDelete(result.value.paths.size),
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    deletePreview = DeletePreviewState.Failed(path, result.error),
                    statusMessage = StatusMessage.Error(result.error),
                )
            }
        }
    }

    suspend fun deletePreviewedNode() {
        val repository = zNodeRepository ?: return
        val profile = requireWritableProfile() ?: return
        val preview = (state.deletePreview as? DeletePreviewState.Loaded)?.preview ?: return

        state = state.copy(statusMessage = StatusMessage.DeletingNode(preview.rootPath))
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
                    statusMessage = StatusMessage.DeletedNode(preview.rootPath),
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

        state = state.copy(statusMessage = StatusMessage.SavingAcl(path))
        val request = UpdateZNodeAclRequest(
            path = path,
            acl = acl,
            expectedAversion = expectedAversion,
        )

        when (val result = repository.updateAcl(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    nodeDetail = ZNodeDetailState.Loaded(result.value),
                    statusMessage = StatusMessage.SavedAcl(path),
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
            statusMessage = StatusMessage.SearchingFrom(request.rootPath),
        )
        when (val result = service.search(profile, request) { scanned ->
            state = state.copy(
                searchState = ZNodeSearchState.Running(request, scanned),
                statusMessage = StatusMessage.SearchingProgress(request.rootPath, scanned),
            )
        }) {
            is OperationResult.Success -> {
                state = state.copy(
                    searchState = ZNodeSearchState.Loaded(result.value),
                    statusMessage = StatusMessage.SearchFound(result.value.hits.size, result.value.scannedNodes),
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    searchState = ZNodeSearchState.Failed(request, result.error),
                    statusMessage = StatusMessage.Error(result.error),
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
            statusMessage = StatusMessage.SearchCanceled(running.scannedNodes),
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

        state =
            state.copy(exportState = ZNodeExportState.Running(request), statusMessage = StatusMessage.Exporting(path))
        when (val result = service.exportSubtree(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    exportState = ZNodeExportState.Loaded(result.value),
                    statusMessage = StatusMessage.Exported(result.value.exportedNodes, path),
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    exportState = ZNodeExportState.Failed(request, result.error),
                    statusMessage = StatusMessage.Error(result.error),
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
            statusMessage = if (request.dryRun) StatusMessage.PlanningImport else StatusMessage.ImportingZNodes,
        )
        when (val result = service.importSubtree(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    importState = ZNodeImportState.Loaded(result.value),
                    statusMessage = if (request.dryRun) {
                        StatusMessage.ImportDryRunPlanned(result.value.operations.size)
                    } else {
                        StatusMessage.ImportCompleted(result.value.appliedCount, result.value.failureCount)
                    },
                )
                state.selectedPath?.let { refreshSelectedPath() }
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    importState = ZNodeImportState.Failed(request, result.error),
                    statusMessage = StatusMessage.Error(result.error),
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
            statusMessage = StatusMessage.ComparingConnections(leftProfile.name, rightProfile.name),
        )
        when (val result = service.compare(leftProfile, rightProfile, request) { scanned ->
            state = state.copy(
                compareState = ZNodeCompareState.Running(request, scanned),
                statusMessage = StatusMessage.ComparingProgress(scanned),
            )
        }) {
            is OperationResult.Success -> {
                state = state.copy(
                    compareState = ZNodeCompareState.Loaded(result.value),
                    statusMessage = StatusMessage.CompareFound(
                        result.value.differences.size,
                        result.value.scannedNodes
                    ),
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    compareState = ZNodeCompareState.Failed(request, result.error),
                    statusMessage = StatusMessage.Error(result.error),
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
            statusMessage = StatusMessage.CompareCanceled(running.scannedNodes),
        )
    }

    suspend fun executeZkCliCommand(request: ZkCliCommandRequest) {
        val repository = zNodeRepository ?: return
        val profile = state.activeConnection ?: run {
            reportError(AppError.Connection("Select a ZooKeeper connection first."))
            return
        }
        val service = ZkCliCommandService(repository)

        state = state.copy(
            zkCliState = ZkCliState.Running(request),
            statusMessage = StatusMessage.RunningZkCommand,
        )
        when (val result = service.execute(profile, request)) {
            is OperationResult.Success -> {
                state = state.copy(
                    zkCliState = ZkCliState.Loaded(result.value),
                    statusMessage = StatusMessage.ZkCommandCompleted,
                )
                refreshAfterZkCliCommand(request.commandLine)
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    zkCliState = ZkCliState.Failed(request, result.error),
                    statusMessage = StatusMessage.Error(result.error),
                )
            }
        }
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
            statusMessage = StatusMessage.WatchEvent(event.type.name, event.path),
        )
        refreshSelectedPath()
    }

    fun reportWatchError(error: AppError) {
        state = state.copy(
            watchState = state.watchState.copy(error = error),
            statusMessage = StatusMessage.Error(error),
        )
    }

    fun clearSelection() {
        state = state.copy(
            nodeSelection = NodeSelectionState.None,
            nodeDetail = ZNodeDetailState.None,
            deletePreview = DeletePreviewState.None,
            watchState = ZNodeWatchState(),
            statusMessage = StatusMessage.SelectionCleared,
        )
    }

    fun reportError(error: AppError) {
        state = state.copy(statusMessage = StatusMessage.Error(error))
    }

    suspend fun updateSettings(settings: AppSettings) {
        state = state.copy(settings = settings, statusMessage = StatusMessage.SettingsUpdated)
        val repository = appSettingsRepository ?: return
        when (val result = repository.saveSettings(settings)) {
            is OperationResult.Success -> Unit
            is OperationResult.Failure -> state = state.copy(statusMessage = StatusMessage.Error(result.error))
        }
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
            statusMessage = StatusMessage.LoadingDetail(path),
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
                    statusMessage = StatusMessage.Error(result.error),
                )
            }
        }
    }

    private suspend fun refreshAfterZkCliCommand(commandLine: String) {
        val command = commandLine.trim().substringBefore(" ")
        if (command in setOf("create", "set", "delete", "rmr", "deleteall", "setAcl")) {
            state.selectedPath?.let { refreshSelectedPath() }
        }
    }
}

private fun ZNodePath.ancestorPaths(): List<ZNodePath> =
    generateSequence(parent) { it.parent }.toList().asReversed()

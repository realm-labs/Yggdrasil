package io.github.realmlabs.yggdrasil.application.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ConnectionProfileRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZooKeeperConnectionTester
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

data class AppState(
    val connections: List<ConnectionProfile> = emptyList(),
    val activeConnectionId: ConnectionId? = null,
    val connectionStatuses: Map<ConnectionId, ConnectionRuntimeStatus> = emptyMap(),
    val nodeSelection: NodeSelectionState = NodeSelectionState.None,
    val znodeChildren: Map<ZNodePath, ZNodeChildrenState> = emptyMap(),
    val nodeDetail: ZNodeDetailState = ZNodeDetailState.None,
    val watchState: ZNodeWatchState = ZNodeWatchState(),
    val themePreference: ThemePreference = ThemePreference.System,
    val isLoadingConnections: Boolean = false,
    val statusMessage: String = "Ready",
) {
    val activeConnection: ConnectionProfile?
        get() = connections.firstOrNull { it.id == activeConnectionId }

    val isReadOnly: Boolean
        get() = activeConnection?.mode != ConnectionMode.ReadWrite

    val selectedPath: ZNodePath?
        get() = when (nodeSelection) {
            NodeSelectionState.None -> null
            is NodeSelectionState.SelectedPath -> nodeSelection.path
            is NodeSelectionState.Loading -> nodeSelection.path
            is NodeSelectionState.Failed -> nodeSelection.path
        }
}

sealed interface ConnectionRuntimeStatus {
    data object Disconnected : ConnectionRuntimeStatus
    data object Connecting : ConnectionRuntimeStatus
    data object Connected : ConnectionRuntimeStatus
    data object Suspended : ConnectionRuntimeStatus
    data object Lost : ConnectionRuntimeStatus
    data class Failed(val error: AppError) : ConnectionRuntimeStatus
}

sealed interface NodeSelectionState {
    data object None : NodeSelectionState
    data class SelectedPath(val path: ZNodePath) : NodeSelectionState
    data class Loading(val path: ZNodePath) : NodeSelectionState
    data class Failed(val path: ZNodePath, val error: AppError) : NodeSelectionState
}

sealed interface ZNodeChildrenState {
    data object Unloaded : ZNodeChildrenState
    data object Loading : ZNodeChildrenState
    data class Loaded(val children: List<ZNodeSummary>) : ZNodeChildrenState
    data class Failed(val error: AppError) : ZNodeChildrenState
}

sealed interface ZNodeDetailState {
    data object None : ZNodeDetailState
    data class Loading(val path: ZNodePath) : ZNodeDetailState
    data class Loaded(val detail: ZNodeDetail) : ZNodeDetailState
    data class Failed(val path: ZNodePath, val error: AppError) : ZNodeDetailState
}

data class ZNodeWatchState(
    val watchedPath: ZNodePath? = null,
    val lastEvent: ZNodeWatchEvent? = null,
    val error: AppError? = null,
) {
    val isRegistered: Boolean
        get() = watchedPath != null && error == null
}

enum class ThemePreference {
    System,
    Light,
    Dark,
}

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

        state = state.copy(
            connections = connections,
            activeConnectionId = activeConnectionId,
            nodeSelection = if (activeConnectionId == null) NodeSelectionState.None else state.nodeSelection,
            znodeChildren = if (activeConnectionId == null) emptyMap() else state.znodeChildren,
            nodeDetail = if (activeConnectionId == null) ZNodeDetailState.None else state.nodeDetail,
            watchState = if (activeConnectionId == null) ZNodeWatchState() else state.watchState,
        )
    }

    fun selectConnection(connectionId: ConnectionId) {
        if (state.connections.none { it.id == connectionId }) return

        state = state.copy(
            activeConnectionId = connectionId,
            nodeSelection = NodeSelectionState.None,
            znodeChildren = emptyMap(),
            nodeDetail = ZNodeDetailState.None,
            watchState = ZNodeWatchState(),
            statusMessage = "Selected ${state.connections.first { it.id == connectionId }.name}",
        )
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
                    watchState = if (state.activeConnectionId == connectionId) ZNodeWatchState() else state.watchState,
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
        state = state.copy(
            nodeSelection = NodeSelectionState.Loading(path),
            nodeDetail = ZNodeDetailState.Loading(path),
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
        val repository = zNodeRepository ?: return
        val profile = state.activeConnection ?: return

        state = state.copy(
            znodeChildren = state.znodeChildren + (path to ZNodeChildrenState.Loading),
            statusMessage = "Loading children for $path",
        )

        when (val result = repository.loadChildren(profile, path)) {
            is OperationResult.Success -> {
                state = state.copy(
                    znodeChildren = state.znodeChildren + (path to ZNodeChildrenState.Loaded(result.value)),
                    connectionStatuses = state.connectionStatuses + (profile.id to ConnectionRuntimeStatus.Connected),
                    statusMessage = "Loaded ${result.value.size} child${if (result.value.size == 1) "" else "ren"} for $path",
                )
            }

            is OperationResult.Failure -> {
                state = state.copy(
                    znodeChildren = state.znodeChildren + (path to ZNodeChildrenState.Failed(result.error)),
                    statusMessage = result.error.message,
                )
            }
        }
    }

    suspend fun refreshSelectedPath() {
        val path = state.selectedPath ?: return
        loadDetail(path)
        loadChildren(path)
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
            watchState = ZNodeWatchState(),
            statusMessage = "Selection cleared",
        )
    }

    fun reportError(error: AppError) {
        state = state.copy(statusMessage = error.message)
    }

    private fun generateConnectionId(): String =
        "conn-${Random.nextLong(0, Long.MAX_VALUE).toString(16)}"

    private suspend fun loadDetail(path: ZNodePath) {
        val repository = zNodeRepository ?: return
        val profile = state.activeConnection ?: return

        state = state.copy(
            nodeDetail = ZNodeDetailState.Loading(path),
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

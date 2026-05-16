package io.github.realmlabs.yggdrasil.application.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.ConnectionId
import io.github.realmlabs.yggdrasil.domain.model.ConnectionMode
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfileDraft
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath
import io.github.realmlabs.yggdrasil.domain.repository.ConnectionProfileRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZooKeeperConnectionTester
import kotlin.random.Random

data class AppState(
    val connections: List<ConnectionProfile> = emptyList(),
    val activeConnectionId: ConnectionId? = null,
    val connectionStatuses: Map<ConnectionId, ConnectionRuntimeStatus> = emptyMap(),
    val nodeSelection: NodeSelectionState = NodeSelectionState.None,
    val themePreference: ThemePreference = ThemePreference.System,
    val isLoadingConnections: Boolean = false,
    val statusMessage: String = "Ready",
) {
    val activeConnection: ConnectionProfile?
        get() = connections.firstOrNull { it.id == activeConnectionId }

    val isReadOnly: Boolean
        get() = activeConnection?.mode != ConnectionMode.ReadWrite
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

enum class ThemePreference {
    System,
    Light,
    Dark,
}

class YggdrasilStateHolder(
    private val connectionProfileRepository: ConnectionProfileRepository? = null,
    private val zooKeeperConnectionTester: ZooKeeperConnectionTester? = null,
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
        )
    }

    fun selectConnection(connectionId: ConnectionId) {
        if (state.connections.none { it.id == connectionId }) return

        state = state.copy(
            activeConnectionId = connectionId,
            nodeSelection = NodeSelectionState.None,
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

    fun selectPath(path: ZNodePath) {
        state = state.copy(
            nodeSelection = NodeSelectionState.SelectedPath(path),
            statusMessage = "Selected $path",
        )
    }

    fun markPathLoading(path: ZNodePath) {
        state = state.copy(
            nodeSelection = NodeSelectionState.Loading(path),
            statusMessage = "Loading $path",
        )
    }

    fun clearSelection() {
        state = state.copy(
            nodeSelection = NodeSelectionState.None,
            statusMessage = "Selection cleared",
        )
    }

    fun reportError(error: AppError) {
        state = state.copy(statusMessage = error.message)
    }

    private fun generateConnectionId(): String =
        "conn-${Random.nextLong(0, Long.MAX_VALUE).toString(16)}"
}

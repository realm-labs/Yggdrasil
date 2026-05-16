package io.github.realmlabs.yggdrasil.application.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.ConnectionId
import io.github.realmlabs.yggdrasil.domain.model.ConnectionMode
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath

data class AppState(
    val connections: List<ConnectionProfile> = emptyList(),
    val activeConnectionId: ConnectionId? = null,
    val connectionStatuses: Map<ConnectionId, ConnectionRuntimeStatus> = emptyMap(),
    val nodeSelection: NodeSelectionState = NodeSelectionState.None,
    val themePreference: ThemePreference = ThemePreference.System,
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

class YggdrasilStateHolder(initialState: AppState = AppState()) {
    var state by mutableStateOf(initialState)
        private set

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
}

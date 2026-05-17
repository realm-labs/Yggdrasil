package io.github.realmlabs.yggdrasil.application.state

import io.github.realmlabs.yggdrasil.domain.model.ConnectionId
import io.github.realmlabs.yggdrasil.domain.model.ConnectionMode
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath

data class AppState(
    val connections: List<ConnectionProfile> = emptyList(),
    val activeConnectionId: ConnectionId? = null,
    val connectionStatuses: Map<ConnectionId, ConnectionRuntimeStatus> = emptyMap(),
    val nodeSelection: NodeSelectionState = NodeSelectionState.None,
    val znodeChildren: Map<ZNodePath, ZNodeChildrenState> = emptyMap(),
    val nodeDetail: ZNodeDetailState = ZNodeDetailState.None,
    val deletePreview: DeletePreviewState = DeletePreviewState.None,
    val watchState: ZNodeWatchState = ZNodeWatchState(),
    val searchState: ZNodeSearchState = ZNodeSearchState.Idle,
    val exportState: ZNodeExportState = ZNodeExportState.Idle,
    val importState: ZNodeImportState = ZNodeImportState.Idle,
    val compareState: ZNodeCompareState = ZNodeCompareState.Idle,
    val zkCliState: ZkCliState = ZkCliState.Idle,
    val settings: AppSettings = AppSettings(),
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

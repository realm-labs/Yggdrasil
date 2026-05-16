package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ZNodeDetailState
import io.github.realmlabs.yggdrasil.domain.model.*

@Composable
fun AppShell(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    onCreateConnection: (ConnectionProfileDraft) -> Unit,
    onDeleteConnection: (ConnectionId) -> Unit,
    onTestConnection: (ConnectionId) -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
    onRefreshSelectedPath: () -> Unit,
    onCreateNode: (CreateZNodeRequest) -> Unit,
    onUpdateNodeData: (ByteArray, Int) -> Unit,
    onPreviewDeleteNode: (Boolean) -> Unit,
    onDeletePreviewedNode: (String) -> Unit,
    onClearDeletePreview: () -> Unit,
    onUpdateAcl: (List<ZNodeAcl>, Int) -> Unit,
    onSearch: (ZNodeSearchRequest) -> Unit,
    onCancelSearch: () -> Unit,
    onExport: (Boolean, ZNodeDataEncoding) -> Unit,
    onImport: (ZNodeImportRequest) -> Unit,
    onCompare: (ZNodeCompareRequest) -> Unit,
    onCancelCompare: () -> Unit,
    onClearSelection: () -> Unit,
) {
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showCreateNodeDialog by remember { mutableStateOf(false) }
    var showDeleteNodeDialog by remember { mutableStateOf(false) }
    var showAclDialog by remember { mutableStateOf(false) }
    var showCommandDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                onNewConnection = { showConnectionDialog = true },
                onCommand = { showCommandDialog = true },
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                ConnectionPane(
                    state = state,
                    onSelectConnection = onSelectConnection,
                    onDeleteConnection = onDeleteConnection,
                    onTestConnection = onTestConnection,
                    modifier = Modifier.width(260.dp).fillMaxHeight(),
                )
                DividerLine(vertical = true)
                TreePane(
                    state = state,
                    onSelectPath = onSelectPath,
                    onRefreshSelectedPath = onRefreshSelectedPath,
                    onCreateNode = { showCreateNodeDialog = true },
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                )
                DividerLine(vertical = true)
                NodeDetailPane(
                    state = state,
                    onUpdateNodeData = onUpdateNodeData,
                    onDeleteNode = { showDeleteNodeDialog = true },
                    onClearSelection = onClearSelection,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                DividerLine(vertical = true)
                InspectorPane(
                    state = state,
                    onEditAcl = { showAclDialog = true },
                    modifier = Modifier.width(280.dp).fillMaxHeight(),
                )
            }
            DividerLine(vertical = false)
            StatusBar(state)
        }
    }

    if (showConnectionDialog) {
        ConnectionDialog(
            onDismiss = { showConnectionDialog = false },
            onSave = { draft ->
                onCreateConnection(draft)
                showConnectionDialog = false
            },
        )
    }

    if (showCreateNodeDialog) {
        CreateNodeDialog(
            selectedPath = state.selectedPath,
            onDismiss = { showCreateNodeDialog = false },
            onCreate = { request ->
                onCreateNode(request)
                showCreateNodeDialog = false
            },
        )
    }

    if (showDeleteNodeDialog) {
        DeleteNodeDialog(
            state = state,
            onPreview = onPreviewDeleteNode,
            onDelete = { confirmation ->
                onDeletePreviewedNode(confirmation)
                showDeleteNodeDialog = false
            },
            onDismiss = {
                onClearDeletePreview()
                showDeleteNodeDialog = false
            },
        )
    }

    if (showAclDialog) {
        val detail = (state.nodeDetail as? ZNodeDetailState.Loaded)?.detail
        if (detail != null) {
            AclEditorDialog(
                detail = detail,
                onDismiss = { showAclDialog = false },
                onSave = { acl ->
                    onUpdateAcl(acl, detail.stat.aversion)
                    showAclDialog = false
                },
            )
        } else {
            showAclDialog = false
        }
    }

    if (showCommandDialog) {
        CommandWorkflowDialog(
            state = state,
            onDismiss = { showCommandDialog = false },
            onSearch = onSearch,
            onCancelSearch = onCancelSearch,
            onExport = onExport,
            onImport = onImport,
            onCompare = onCompare,
            onCancelCompare = onCancelCompare,
            onSelectPath = onSelectPath,
            onSelectConnection = onSelectConnection,
        )
    }
}

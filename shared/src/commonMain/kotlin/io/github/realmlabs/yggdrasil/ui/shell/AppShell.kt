package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.*
import io.github.realmlabs.yggdrasil.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.text.toString

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
    onClearSelection: () -> Unit,
) {
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showCreateNodeDialog by remember { mutableStateOf(false) }
    var showDeleteNodeDialog by remember { mutableStateOf(false) }
    var showAclDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                onNewConnection = { showConnectionDialog = true },
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
}

@Composable
private fun TopBar(
    state: AppState,
    onNewConnection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Yggdrasil",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        EnvironmentPill(text = state.activeConnection?.name ?: "No connection")
        if (state.isReadOnly) {
            EnvironmentPill(text = "Read only")
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = {}) {
            Text("Command")
        }
        Button(onClick = onNewConnection) {
            Text("New connection")
        }
    }
}

@Composable
private fun ConnectionPane(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    onDeleteConnection: (ConnectionId) -> Unit,
    onTestConnection: (ConnectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Connections",
        modifier = modifier,
    ) {
        if (state.isLoadingConnections) {
            EmptyPanelMessage(
                title = "Loading connections",
                body = "Saved connection profiles are being loaded.",
            )
            return@Panel
        }

        if (state.connections.isEmpty()) {
            EmptyPanelMessage(
                title = "No saved connections",
                body = "Create a ZooKeeper connection to start browsing znodes.",
            )
            return@Panel
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.connections.forEach { connection ->
                ConnectionRow(
                    connection = connection,
                    status = state.connectionStatuses[connection.id] ?: ConnectionRuntimeStatus.Disconnected,
                    selected = connection.id == state.activeConnectionId,
                    onClick = { onSelectConnection(connection.id) },
                    onTest = { onTestConnection(connection.id) },
                    onDelete = { onDeleteConnection(connection.id) },
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    connection: ConnectionProfile,
    status: ConnectionRuntimeStatus,
    selected: Boolean,
    onClick: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = connection.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusDot(status)
        }
        Text(
            text = connection.connectionString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (connection.mode == ConnectionMode.ReadWrite) "Read/write" else "Read only",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onTest,
                modifier = Modifier.weight(1f),
                enabled = status != ConnectionRuntimeStatus.Connecting,
            ) {
                Text(if (status == ConnectionRuntimeStatus.Connecting) "Testing" else "Test")
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun ConnectionDialog(
    onDismiss: () -> Unit,
    onSave: (ConnectionProfileDraft) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var connectionString by remember { mutableStateOf("") }
    var chroot by remember { mutableStateOf("") }
    var readWrite by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New ZooKeeper connection")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = connectionString,
                    onValueChange = { connectionString = it },
                    label = { Text("Connection string") },
                    placeholder = { Text("localhost:2181") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = chroot,
                    onValueChange = { chroot = it },
                    label = { Text("Chroot") },
                    placeholder = { Text("/app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeButton(
                        text = "Read only",
                        selected = !readWrite,
                        onClick = { readWrite = false },
                        modifier = Modifier.weight(1f),
                    )
                    ModeButton(
                        text = "Read/write",
                        selected = readWrite,
                        onClick = { readWrite = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                validationMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val draft = ConnectionProfileDraft(
                        name = name,
                        connectionString = connectionString,
                        chroot = chroot,
                        mode = if (readWrite) ConnectionMode.ReadWrite else ConnectionMode.ReadOnly,
                    )
                    when (val validation = draft.toProfile(ConnectionId("validation"))) {
                        is OperationResult.Success -> onSave(draft)
                        is OperationResult.Failure -> validationMessage = validation.error.message
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun CreateNodeDialog(
    selectedPath: ZNodePath?,
    onDismiss: () -> Unit,
    onCreate: (CreateZNodeRequest) -> Unit,
) {
    val defaultPath = selectedPath?.let { path ->
        if (path == ZNodePath.Root) "/new-node" else "${path.value}/new-node"
    } ?: "/new-node"
    var pathText by remember { mutableStateOf(defaultPath) }
    var dataText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ZNodeCreateMode.Persistent) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create znode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = dataText,
                    onValueChange = { dataText = it },
                    label = { Text("Data") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton(
                            text = "Persistent",
                            selected = mode == ZNodeCreateMode.Persistent,
                            onClick = { mode = ZNodeCreateMode.Persistent },
                            modifier = Modifier.weight(1f),
                        )
                        ModeButton(
                            text = "Ephemeral",
                            selected = mode == ZNodeCreateMode.Ephemeral,
                            onClick = { mode = ZNodeCreateMode.Ephemeral },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton(
                            text = "Persistent seq",
                            selected = mode == ZNodeCreateMode.PersistentSequential,
                            onClick = { mode = ZNodeCreateMode.PersistentSequential },
                            modifier = Modifier.weight(1f),
                        )
                        ModeButton(
                            text = "Ephemeral seq",
                            selected = mode == ZNodeCreateMode.EphemeralSequential,
                            onClick = { mode = ZNodeCreateMode.EphemeralSequential },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                validationMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (val parsedPath = ZNodePath.parse(pathText)) {
                        is OperationResult.Success -> onCreate(
                            CreateZNodeRequest(
                                path = parsedPath.value,
                                data = dataText.encodeToByteArray(),
                                mode = mode,
                            ),
                        )

                        is OperationResult.Failure -> validationMessage = parsedPath.error.message
                    }
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DeleteNodeDialog(
    state: AppState,
    onPreview: (Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedPath = state.selectedPath
    var recursive by remember(selectedPath) { mutableStateOf(false) }
    var confirmation by remember(selectedPath) { mutableStateOf("") }
    val preview = state.deletePreview as? DeletePreviewState.Loaded
    val requiresConfirmation = preview?.preview?.requiresFullPathConfirmation == true
    val canDelete = preview != null && (!requiresConfirmation || confirmation == preview.preview.rootPath.value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete znode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = selectedPath?.value ?: "No selected path",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = recursive,
                        onCheckedChange = { recursive = it },
                    )
                    Text("Recursive delete")
                }
                OutlinedButton(
                    onClick = { onPreview(recursive) },
                    enabled = selectedPath != null,
                ) {
                    Text("Preview")
                }
                when (val previewState = state.deletePreview) {
                    DeletePreviewState.None -> Text(
                        text = "Preview the delete before confirming.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is DeletePreviewState.Loading -> Text(
                        text = "Loading delete preview for ${previewState.path}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    is DeletePreviewState.Failed -> Text(
                        text = previewState.error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    is DeletePreviewState.Loaded -> {
                        Text(
                            text = "${previewState.preview.paths.size} node${if (previewState.preview.paths.size == 1) "" else "s"} will be deleted.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .verticalScroll(rememberScrollState())
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            previewState.preview.paths.forEach { path ->
                                Text(
                                    text = path.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (previewState.preview.requiresFullPathConfirmation) {
                            TextField(
                                value = confirmation,
                                onValueChange = { confirmation = it },
                                label = { Text("Type ${previewState.preview.rootPath} to confirm") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDelete(confirmation) },
                enabled = canDelete,
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private data class AclDraft(
    val scheme: String,
    val id: String,
    val permissions: Set<ZNodePermission>,
) {
    fun toAcl(): ZNodeAcl =
        ZNodeAcl(
            scheme = scheme.trim(),
            id = id.trim(),
            permissions = permissions,
        )
}

@Composable
private fun AclEditorDialog(
    detail: ZNodeDetail,
    onDismiss: () -> Unit,
    onSave: (List<ZNodeAcl>) -> Unit,
) {
    var entries by remember(detail.path, detail.stat.aversion) {
        mutableStateOf(
            detail.acl.map { acl ->
                AclDraft(
                    scheme = acl.scheme,
                    id = acl.id,
                    permissions = acl.permissions,
                )
            },
        )
    }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    fun updateEntry(index: Int, transform: (AclDraft) -> AclDraft) {
        entries = entries.mapIndexed { currentIndex, entry ->
            if (currentIndex == index) transform(entry) else entry
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ACL") },
        text = {
            Column(
                modifier = Modifier.height(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        entries = entries + AclDraft("world", "anyone", setOf(ZNodePermission.Read))
                    }) {
                        Text("World read")
                    }
                    OutlinedButton(onClick = {
                        entries = entries + AclDraft("digest", "user:password-hash", ZNodePermission.entries.toSet())
                    }) {
                        Text("Digest admin")
                    }
                    OutlinedButton(onClick = {
                        entries =
                            entries + AclDraft("ip", "127.0.0.1", setOf(ZNodePermission.Read, ZNodePermission.Write))
                    }) {
                        Text("IP rw")
                    }
                }
                entries.forEachIndexed { index, entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = entry.scheme,
                                onValueChange = { value -> updateEntry(index) { it.copy(scheme = value) } },
                                label = { Text("Scheme") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            TextField(
                                value = entry.id,
                                onValueChange = { value -> updateEntry(index) { it.copy(id = value) } },
                                label = { Text("Id") },
                                modifier = Modifier.weight(2f),
                                singleLine = true,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ZNodePermission.entries.forEach { permission ->
                                PermissionToggle(
                                    permission = permission,
                                    checked = permission in entry.permissions,
                                    onCheckedChange = { checked ->
                                        updateEntry(index) {
                                            it.copy(
                                                permissions = if (checked) {
                                                    it.permissions + permission
                                                } else {
                                                    it.permissions - permission
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                            TextButton(onClick = {
                                entries = entries.filterIndexed { currentIndex, _ -> currentIndex != index }
                            }) {
                                Text("Remove")
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    entries = entries + AclDraft("world", "anyone", setOf(ZNodePermission.Read))
                }) {
                    Text("Add ACL")
                }
                validationMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val acl = entries.map(AclDraft::toAcl)
                    if (acl.isEmpty() || acl.any { it.scheme.isBlank() || it.id.isBlank() || it.permissions.isEmpty() }) {
                        validationMessage = "ACL entries require scheme, id, and at least one permission."
                    } else {
                        onSave(acl)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PermissionToggle(
    permission: ZNodePermission,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = permission.name.take(1).lowercase(),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text)
        }
    }
}

@Composable
private fun TreePane(
    state: AppState,
    onSelectPath: (ZNodePath) -> Unit,
    onRefreshSelectedPath: () -> Unit,
    onCreateNode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Znodes",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCreateNode,
                    enabled = state.activeConnection != null && !state.isReadOnly,
                ) {
                    Text("New")
                }
                OutlinedButton(
                    onClick = onRefreshSelectedPath,
                    enabled = state.selectedPath != null,
                ) {
                    Text("Refresh")
                }
            }
        },
    ) {
        val activeConnection = state.activeConnection
        if (activeConnection == null) {
            EmptyPanelMessage(
                title = "No active connection",
                body = "Select a saved connection before loading the znode tree.",
            )
            return@Panel
        }

        var expandedPaths by remember(state.activeConnectionId) {
            mutableStateOf(setOf(ZNodePath.Root))
        }

        fun togglePath(path: ZNodePath, expandable: Boolean) {
            if (!expandable) {
                onSelectPath(path)
                return
            }

            if (path in expandedPaths) {
                expandedPaths = expandedPaths - path
            } else {
                expandedPaths = expandedPaths + path
                onSelectPath(path)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val rootChildrenState = state.znodeChildren[ZNodePath.Root]
            TreeNodeRow(
                path = ZNodePath.Root,
                depth = 0,
                selected = state.selectedPath == ZNodePath.Root,
                childrenState = rootChildrenState,
                expandable = rootChildrenState !is ZNodeChildrenState.Loaded || rootChildrenState.children.isNotEmpty(),
                expanded = ZNodePath.Root in expandedPaths,
                onClick = {
                    togglePath(
                        path = ZNodePath.Root,
                        expandable = rootChildrenState !is ZNodeChildrenState.Loaded || rootChildrenState.children.isNotEmpty(),
                    )
                },
            )
            TreeChildren(
                state = state,
                expandedPaths = expandedPaths,
                parent = ZNodePath.Root,
                depth = 1,
                onTogglePath = ::togglePath,
            )
        }
    }
}

@Composable
private fun TreeChildren(
    state: AppState,
    expandedPaths: Set<ZNodePath>,
    parent: ZNodePath,
    depth: Int,
    onTogglePath: (ZNodePath, Boolean) -> Unit,
) {
    if (parent !in expandedPaths) return

    when (val childrenState = state.znodeChildren[parent]) {
        null,
        ZNodeChildrenState.Unloaded -> if (parent == ZNodePath.Root) {
            TreeStateMessage(
                text = "Loading root children",
                depth = depth,
            )
        }

        ZNodeChildrenState.Loading -> TreeStateMessage(
            text = "Loading...",
            depth = depth,
        )

        is ZNodeChildrenState.Failed -> TreeStateMessage(
            text = childrenState.error.message,
            depth = depth,
            isError = true,
        )

        is ZNodeChildrenState.Loaded -> {
            if (childrenState.children.isEmpty()) {
                TreeStateMessage(
                    text = "No children",
                    depth = depth,
                )
            }
            childrenState.children.forEach { child ->
                val childChildrenState = state.znodeChildren[child.path]
                val expandable =
                    child.hasChildren || childChildrenState is ZNodeChildrenState.Loaded && childChildrenState.children.isNotEmpty()
                TreeNodeRow(
                    path = child.path,
                    depth = depth,
                    selected = state.selectedPath == child.path,
                    childrenState = childChildrenState,
                    expandable = expandable,
                    expanded = child.path in expandedPaths,
                    onClick = { onTogglePath(child.path, expandable) },
                )
                TreeChildren(
                    state = state,
                    expandedPaths = expandedPaths,
                    parent = child.path,
                    depth = depth + 1,
                    onTogglePath = onTogglePath,
                )
            }
        }
    }
}

@Composable
private fun TreeNodeRow(
    path: ZNodePath,
    depth: Int,
    selected: Boolean,
    childrenState: ZNodeChildrenState?,
    expandable: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val isLoading = childrenState == ZNodeChildrenState.Loading
    val isFailed = childrenState is ZNodeChildrenState.Failed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(
                PaddingValues(
                    start = (10 + depth * 16).dp,
                    top = 8.dp,
                    end = 10.dp,
                    bottom = 8.dp,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TreeDisclosureIcon(
            expandable = expandable,
            expanded = expanded,
            loading = isLoading,
            failed = isFailed,
        )
        Text(
            text = if (path == ZNodePath.Root) path.value else path.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TreeDisclosureIcon(
    expandable: Boolean,
    expanded: Boolean,
    loading: Boolean,
    failed: Boolean,
) {
    val color = when {
        failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.size(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = color,
            )

            failed -> Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )

            expandable -> Canvas(Modifier.size(14.dp)) {
                val strokeWidth = 1.7.dp.toPx()
                if (expanded) {
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.25f, size.height * 0.40f),
                        end = Offset(size.width * 0.50f, size.height * 0.65f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.50f, size.height * 0.65f),
                        end = Offset(size.width * 0.75f, size.height * 0.40f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                } else {
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.40f, size.height * 0.25f),
                        end = Offset(size.width * 0.65f, size.height * 0.50f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.65f, size.height * 0.50f),
                        end = Offset(size.width * 0.40f, size.height * 0.75f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeStateMessage(
    text: String,
    depth: Int,
    isError: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (10 + depth * 16).dp, top = 4.dp, end = 10.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun NodeDetailPane(
    state: AppState,
    onUpdateNodeData: (ByteArray, Int) -> Unit,
    onDeleteNode: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Node data",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDeleteNode,
                    enabled = state.selectedPath != null && !state.isReadOnly,
                ) {
                    Text("Delete")
                }
                OutlinedButton(onClick = onClearSelection) {
                    Text("Clear")
                }
            }
        },
    ) {
        when (val detailState = state.nodeDetail) {
            ZNodeDetailState.None -> EmptyPanelMessage(
                title = "No znode selected",
                body = "Choose a path from the tree to inspect data, stat, and ACL details.",
            )

            is ZNodeDetailState.Loading -> EmptyPanelMessage(
                title = "Loading ${detailState.path}",
                body = "The node detail request is in progress.",
            )

            is ZNodeDetailState.Loaded -> NodeDataViewer(
                detail = detailState.detail,
                readOnly = state.isReadOnly,
                onUpdateNodeData = onUpdateNodeData,
            )
            is ZNodeDetailState.Failed -> EmptyPanelMessage(
                title = "Could not load ${detailState.path}",
                body = detailState.error.message,
            )
        }
    }
}

@Composable
private fun NodeDataViewer(
    detail: ZNodeDetail,
    readOnly: Boolean,
    onUpdateNodeData: (ByteArray, Int) -> Unit,
) {
    var selectedFormat by remember(detail.path, detail.stat.version) {
        mutableStateOf(detail.detectedFormat.toViewFormat())
    }
    var editing by remember(detail.path, detail.stat.version) { mutableStateOf(false) }
    var editText by remember(detail.path, detail.stat.version) { mutableStateOf(detail.data.toTextPreview()) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = detail.path.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DataFormatButton(
                label = "Text",
                selected = selectedFormat == ZNodeDataFormat.Text,
                onClick = { selectedFormat = ZNodeDataFormat.Text },
            )
            DataFormatButton(
                label = "JSON",
                selected = selectedFormat == ZNodeDataFormat.Json,
                onClick = { selectedFormat = ZNodeDataFormat.Json },
            )
            DataFormatButton(
                label = "Hex",
                selected = selectedFormat == ZNodeDataFormat.Hex,
                onClick = { selectedFormat = ZNodeDataFormat.Hex },
            )
            Spacer(Modifier.weight(1f))
            if (editing) {
                OutlinedButton(onClick = {
                    editing = false
                    editText = detail.data.toTextPreview()
                }) {
                    Text("Cancel")
                }
                Button(onClick = {
                    onUpdateNodeData(editText.encodeToByteArray(), detail.stat.version)
                    editing = false
                }) {
                    Text("Save")
                }
            } else {
                Button(
                    onClick = {
                        selectedFormat = ZNodeDataFormat.Text
                        editText = detail.data.toTextPreview()
                        editing = true
                    },
                    enabled = !readOnly,
                ) {
                    Text("Edit")
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (editing) {
                TextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = detail.renderData(selectedFormat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DataFormatButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}

@Composable
private fun InspectorPane(
    state: AppState,
    onEditAcl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Inspector",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val detail = (state.nodeDetail as? ZNodeDetailState.Loaded)?.detail
            InspectorSection(
                title = "Stat",
                rows = detail?.stat?.toInspectorRows() ?: emptyStatRows(),
            )
            InspectorSection(
                title = "ACL",
                rows = detail?.acl?.toInspectorRows() ?: listOf("Entries" to "-", "Mode" to state.modeLabel()),
                trailing = {
                    OutlinedButton(
                        onClick = onEditAcl,
                        enabled = detail != null && !state.isReadOnly,
                    ) {
                        Text("Edit")
                    }
                },
            )
            InspectorSection(
                title = "Watch",
                rows = listOf(
                    "State" to when {
                        state.watchState.error != null -> "Failed"
                        state.watchState.isRegistered -> "Registered"
                        else -> "Not registered"
                    },
                    "Path" to (state.watchState.watchedPath?.value ?: "-"),
                    "Last event" to (state.watchState.lastEvent?.let { "${it.type} ${it.path}" } ?: "-"),
                ),
            )
        }
    }
}

@Composable
private fun InspectorSection(
    title: String,
    rows: List<Pair<String, String>>,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            trailing?.invoke()
        }
        rows.forEach { (label, value) ->
            Row {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val PrettyJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

private fun ZNodeDataFormat.toViewFormat(): ZNodeDataFormat =
    when (this) {
        ZNodeDataFormat.Json -> ZNodeDataFormat.Json
        ZNodeDataFormat.Hex,
        ZNodeDataFormat.Base64,
        ZNodeDataFormat.Unknown -> ZNodeDataFormat.Hex

        ZNodeDataFormat.Text,
        ZNodeDataFormat.Yaml,
        ZNodeDataFormat.Properties -> ZNodeDataFormat.Text
    }

private fun ZNodeDetail.renderData(format: ZNodeDataFormat): String {
    if (data.isEmpty()) return "(empty data)"

    return when (format) {
        ZNodeDataFormat.Json -> renderJsonData()
        ZNodeDataFormat.Hex -> data.toHexPreview()
        else -> data.toTextPreview()
    }
}

private fun ZNodeDetail.renderJsonData(): String {
    val text = data.toTextPreview()
    return try {
        PrettyJson.encodeToString<JsonElement>(PrettyJson.parseToJsonElement(text))
    } catch (_: Exception) {
        "Invalid JSON\n\n$text"
    }
}

private fun ByteArray.toTextPreview(): String {
    val text = decodeToString()
    val preview = text.take(MaxDataPreviewChars)
    val suffix = if (text.length > MaxDataPreviewChars) {
        "\n\n... truncated ${text.length - MaxDataPreviewChars} characters"
    } else {
        ""
    }
    return preview + suffix
}

private fun ByteArray.toHexPreview(): String {
    val bytes = take(MaxHexPreviewBytes)
    val lines = bytes.chunked(16).mapIndexed { index, chunk ->
        val offset = (index * 16).toString(16).padStart(8, '0')
        val hex = chunk.joinToString(" ") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        "$offset  $hex"
    }
    val suffix = if (size > MaxHexPreviewBytes) {
        "\n... truncated ${size - MaxHexPreviewBytes} bytes"
    } else {
        ""
    }
    return lines.joinToString("\n") + suffix
}

private fun ZNodeStat.toInspectorRows(): List<Pair<String, String>> =
    listOf(
        "Data size" to "$dataLength bytes",
        "Data version" to version.toString(),
        "cversion" to cversion.toString(),
        "aversion" to aversion.toString(),
        "Children" to numChildren.toString(),
        "ctime" to ctimeMillis.toString(),
        "mtime" to mtimeMillis.toString(),
        "Ephemeral owner" to ephemeralOwner.toZxidLabel(),
        "czxid" to czxid.toZxidLabel(),
        "mzxid" to mzxid.toZxidLabel(),
        "pzxid" to pzxid.toZxidLabel(),
    )

private fun emptyStatRows(): List<Pair<String, String>> =
    listOf(
        "Data size" to "-",
        "Data version" to "-",
        "cversion" to "-",
        "aversion" to "-",
        "Children" to "-",
        "ctime" to "-",
        "mtime" to "-",
        "Ephemeral owner" to "-",
        "czxid" to "-",
        "mzxid" to "-",
        "pzxid" to "-",
    )

private fun List<ZNodeAcl>.toInspectorRows(): List<Pair<String, String>> =
    buildList {
        add("Entries" to size.toString())
        if (isEmpty()) {
            add("Permissions" to "-")
            return@buildList
        }
        this@toInspectorRows.take(4).forEachIndexed { index, acl ->
            add("ACL ${index + 1}" to "${acl.scheme}:${acl.id} ${acl.permissions.toPermissionLabel()}")
        }
        if (size > 4) {
            add("More" to "${size - 4} hidden")
        }
    }

private fun Set<ZNodePermission>.toPermissionLabel(): String =
    listOfNotNull(
        "r".takeIf { ZNodePermission.Read in this },
        "w".takeIf { ZNodePermission.Write in this },
        "c".takeIf { ZNodePermission.Create in this },
        "d".takeIf { ZNodePermission.Delete in this },
        "a".takeIf { ZNodePermission.Admin in this },
    ).joinToString("")

private fun Long.toZxidLabel(): String =
    if (this == 0L) "0" else "0x${toString(16)}"

private fun AppState.modeLabel(): String =
    if (isReadOnly) "Read only" else "Read/write"

private const val MaxDataPreviewChars = 64 * 1024
private const val MaxHexPreviewBytes = 16 * 1024

@Composable
private fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun EmptyPanelMessage(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusBar(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.statusMessage,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Idle",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(status: ConnectionRuntimeStatus) {
    val color = when (status) {
        ConnectionRuntimeStatus.Connected -> Color(0xFF2E7D32)
        ConnectionRuntimeStatus.Connecting -> Color(0xFFF9A825)
        ConnectionRuntimeStatus.Suspended -> Color(0xFFF57C00)
        ConnectionRuntimeStatus.Lost -> Color(0xFFC62828)
        ConnectionRuntimeStatus.Disconnected -> MaterialTheme.colorScheme.outline
        is ConnectionRuntimeStatus.Failed -> Color(0xFFC62828)
    }

    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

@Composable
private fun EnvironmentPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DividerLine(vertical: Boolean) {
    Box(
        modifier = if (vertical) {
            Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        } else {
            Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        },
    )
}

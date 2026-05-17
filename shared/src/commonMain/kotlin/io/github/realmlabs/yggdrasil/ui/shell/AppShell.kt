package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.github.realmlabs.yggdrasil.application.state.*
import io.github.realmlabs.yggdrasil.application.workflow.completeZkCliCommandLine
import io.github.realmlabs.yggdrasil.domain.model.*
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*

@Composable
fun AppShell(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    onCreateConnection: (ConnectionProfileDraft) -> Unit,
    onUpdateConnection: (ConnectionId, ConnectionProfileDraft) -> Unit,
    onDeleteConnection: (ConnectionId) -> Unit,
    onTestConnection: (ConnectionId) -> Unit,
    onDisconnectActiveConnection: () -> Unit,
    onReconnectActiveConnection: () -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
    onRefreshSelectedPath: () -> Unit,
    onCreateNode: (CreateZNodeRequest) -> Unit,
    onUpdateNodeData: (ByteArray, Int) -> Unit,
    onPreviewDeleteNode: (Boolean) -> Unit,
    onDeletePreviewedNode: () -> Unit,
    onClearDeletePreview: () -> Unit,
    onUpdateAcl: (List<ZNodeAcl>, Int) -> Unit,
    onSearch: (ZNodeSearchRequest) -> Unit,
    onCancelSearch: () -> Unit,
    onExport: (Boolean, ZNodeDataEncoding) -> Unit,
    onImport: (ZNodeImportRequest) -> Unit,
    onCompare: (ZNodeCompareRequest) -> Unit,
    onCancelCompare: () -> Unit,
    onExecuteZkCli: (ZkCliCommandRequest) -> Unit,
    onClearSelection: () -> Unit,
    onSetWatchEnabled: (Boolean) -> Unit,
    onClearWatchEvents: () -> Unit,
    onToggleFavoritePath: (ZNodePath) -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
) {
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showCreateNodeDialog by remember { mutableStateOf(false) }
    var showDeleteNodeDialog by remember { mutableStateOf(false) }
    var showAclDialog by remember { mutableStateOf(false) }
    var showCommandDialog by remember { mutableStateOf(false) }
    var editingConnection by remember { mutableStateOf<ConnectionProfile?>(null) }
    var topSearch by remember { mutableStateOf("") }
    var terminalCommand by remember { mutableStateOf(TextFieldValue("")) }
    var terminalEntries by remember { mutableStateOf<List<TerminalEntry>>(emptyList()) }
    var lastTerminalStateKey by remember { mutableStateOf("") }
    var inspectorExpanded by remember { mutableStateOf(state.settings.inspectorExpandedByDefault) }
    var showSettings by remember { mutableStateOf(false) }
    var treePaneWidth by remember { mutableStateOf(316.dp) }
    var inspectorPaneWidth by remember { mutableStateOf(300.dp) }
    var terminalHeight by remember { mutableStateOf(220.dp) }
    var treeRevealRequestId by remember { mutableStateOf(0) }
    var treeRevealRequest by remember { mutableStateOf<ZNodeTreeRevealRequest?>(null) }
    val terminalSuccessOutput = when (val cliState = state.zkCliState) {
        is ZkCliState.Loaded -> localizedZkCliOutput(cliState.result.output)
        else -> null
    }
    val terminalErrorOutput = (state.zkCliState as? ZkCliState.Failed)?.error?.localized()
    val zkCliCompletionPaths = remember(state.znodeChildren, state.selectedPath) {
        state.knownZkCliPaths()
    }

    LaunchedEffect(state.settings.inspectorExpandedByDefault) {
        inspectorExpanded = state.settings.inspectorExpandedByDefault
    }

    LaunchedEffect(state.activeConnectionId, state.settings.clearTerminalOnConnectionChange) {
        if (state.settings.clearTerminalOnConnectionChange) {
            terminalEntries = emptyList()
        }
        treeRevealRequest = null
    }

    LaunchedEffect(state.zkCliState, terminalSuccessOutput, terminalErrorOutput) {
        when (val cliState = state.zkCliState) {
            is ZkCliState.Loaded -> {
                val key = "ok:${cliState.result.commandLine}:${cliState.result.output.hashCode()}"
                if (key != lastTerminalStateKey) {
                    terminalEntries = terminalEntries + TerminalEntry(
                        command = cliState.result.commandLine,
                        output = terminalSuccessOutput ?: cliState.result.output,
                    )
                    lastTerminalStateKey = key
                }
            }

            is ZkCliState.Failed -> {
                val key = "err:${cliState.request.commandLine}:${cliState.error.message}"
                if (key != lastTerminalStateKey) {
                    terminalEntries = terminalEntries + TerminalEntry(
                        command = cliState.request.commandLine,
                        output = terminalErrorOutput ?: cliState.error.message,
                        isError = true,
                    )
                    lastTerminalStateKey = key
                }
            }

            else -> Unit
        }
    }

    fun revealTreePath(path: ZNodePath) {
        treeRevealRequestId += 1
        treeRevealRequest = ZNodeTreeRevealRequest(treeRevealRequestId, path)
        onSelectPath(path)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (showSettings) {
            SettingsPage(
                settings = state.settings,
                onSettingsChange = onUpdateSettings,
                onClose = { showSettings = false },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
            YggdrasilTopBar(
                state = state,
                onSettings = { showSettings = true },
                onCommand = { showCommandDialog = true },
                onNewConnection = { showConnectionDialog = true },
                onEditConnection = { editingConnection = it },
                onDeleteConnection = onDeleteConnection,
                onTestConnection = onTestConnection,
                onDisconnectActiveConnection = onDisconnectActiveConnection,
                onReconnectActiveConnection = onReconnectActiveConnection,
                search = topSearch,
                onSearchChange = { topSearch = it },
                onRunSearch = {
                    val root = state.selectedPath ?: ZNodePath.Root
                    onSearch(
                        ZNodeSearchRequest(
                            rootPath = root,
                            query = topSearch,
                            searchPath = true,
                            searchData = true,
                        ),
                    )
                },
                onSelectSearchResult = ::revealTreePath,
                onSelectConnection = onSelectConnection,
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                TreePane(
                    state = state,
                    onSelectPath = onSelectPath,
                    onRefreshSelectedPath = onRefreshSelectedPath,
                    onToggleFavoritePath = onToggleFavoritePath,
                    revealRequest = treeRevealRequest,
                    modifier = Modifier.width(treePaneWidth).fillMaxHeight(),
                )
                ResizableDivider(
                    vertical = true,
                    onDrag = { delta ->
                        treePaneWidth = (treePaneWidth + delta).coerceIn(220.dp, 520.dp)
                    },
                )
                NodeDetailPane(
                    state = state,
                    onCreateNode = { showCreateNodeDialog = true },
                    onUpdateNodeData = onUpdateNodeData,
                    onDeleteNode = { showDeleteNodeDialog = true },
                    onClearSelection = onClearSelection,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                if (inspectorExpanded) {
                    ResizableDivider(
                        vertical = true,
                        onDrag = { delta ->
                            inspectorPaneWidth = (inspectorPaneWidth - delta).coerceIn(240.dp, 560.dp)
                        },
                    )
                } else {
                    DividerLine(vertical = true)
                }
                InspectorPane(
                    state = state,
                    expanded = inspectorExpanded,
                    onToggleExpanded = { inspectorExpanded = !inspectorExpanded },
                    onEditAcl = { showAclDialog = true },
                    onSetWatchEnabled = onSetWatchEnabled,
                    onClearWatchEvents = onClearWatchEvents,
                    modifier = Modifier.width(if (inspectorExpanded) inspectorPaneWidth else 56.dp).fillMaxHeight(),
                )
            }
                if (state.settings.embeddedTerminalEnabled) {
                    ResizableDivider(
                        vertical = false,
                        onDrag = { delta ->
                            terminalHeight = (terminalHeight - delta).coerceIn(140.dp, 460.dp)
                        },
                    )
                    ZkCliTerminal(
                        state = state,
                        settings = state.settings,
                        entries = terminalEntries,
                        terminalHeight = terminalHeight,
                        command = terminalCommand,
                        onCommandChange = { terminalCommand = it },
                        onCompleteCommand = {
                            val completion = completeZkCliCommandLine(
                                commandLine = terminalCommand.text,
                                cursor = terminalCommand.selection.start,
                                knownPaths = zkCliCompletionPaths,
                            )
                            terminalCommand = TextFieldValue(
                                text = completion.commandLine,
                                selection = TextRange(completion.cursor),
                            )
                        },
                        onExecute = {
                            if (terminalCommand.text.isNotBlank()) {
                                onExecuteZkCli(ZkCliCommandRequest(terminalCommand.text))
                                terminalCommand = TextFieldValue("")
                            }
                        },
                        onClear = { terminalEntries = emptyList() },
                    )
                }
            }
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

    editingConnection?.let { connection ->
        ConnectionDialog(
            profile = connection,
            onDismiss = { editingConnection = null },
            onSave = { draft ->
                onUpdateConnection(connection.id, draft)
                editingConnection = null
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
            onDelete = {
                onDeletePreviewedNode()
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
            onExecuteZkCli = onExecuteZkCli,
            onSelectPath = ::revealTreePath,
            onSelectConnection = onSelectConnection,
            onRefreshSelectedPath = onRefreshSelectedPath,
            onReconnectActiveConnection = onReconnectActiveConnection,
            onOpenSettings = { showSettings = true },
            onUpdateSettings = onUpdateSettings,
        )
    }
}

@Composable
private fun YggdrasilTopBar(
    state: AppState,
    search: String,
    onSearchChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onSelectSearchResult: (ZNodePath) -> Unit,
    onSelectConnection: (ConnectionId) -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (ConnectionProfile) -> Unit,
    onDeleteConnection: (ConnectionId) -> Unit,
    onTestConnection: (ConnectionId) -> Unit,
    onDisconnectActiveConnection: () -> Unit,
    onReconnectActiveConnection: () -> Unit,
    onSettings: () -> Unit,
    onCommand: () -> Unit,
) {
    val strings = Res.string
    var searchResultsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(state.searchState) {
        if (search.isNotBlank() && state.searchState !is ZNodeSearchState.Idle) {
            searchResultsExpanded = true
        }
    }
    fun runTopSearch() {
        if (search.isBlank()) return
        searchResultsExpanded = true
        onRunSearch()
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
    ) {
        Spacer(Modifier.height(ShellMetrics.TitleBarTopInset))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionStatusPill(
                state = state,
                onSelectConnection = onSelectConnection,
                onNewConnection = onNewConnection,
                onEditConnection = onEditConnection,
                onDeleteConnection = onDeleteConnection,
                onTestConnection = onTestConnection,
                onDisconnectActiveConnection = onDisconnectActiveConnection,
                onReconnectActiveConnection = onReconnectActiveConnection,
            )
            ModeStatusPill(state)
            BreadcrumbPath(
                path = state.selectedPath,
                modifier = Modifier.weight(1f),
            )
            Box {
                ShellTextInput(
                    value = search,
                    onValueChange = {
                        onSearchChange(it)
                        searchResultsExpanded = false
                    },
                    placeholder = stringResource(strings.top_search_placeholder),
                    modifier = Modifier
                        .width(340.dp)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                runTopSearch()
                                true
                            } else {
                                false
                            }
                        },
                    leading = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailing = {
                        Text(
                            text = "↵",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                TopSearchResultsMenu(
                    expanded = searchResultsExpanded,
                    searchState = state.searchState,
                    onDismiss = { searchResultsExpanded = false },
                    onSelectPath = { path ->
                        searchResultsExpanded = false
                        onSelectSearchResult(path)
                    },
                )
            }
            Button(
                onClick = onCommand,
                modifier = Modifier.width(132.dp).height(ShellMetrics.ControlHeight),
                shape = ShellMetrics.FieldShape,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Text(stringResource(strings.command_button))
            }
            IconGlyphButton(label = stringResource(strings.common_settings), onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(strings.common_settings),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun TopSearchResultsMenu(
    expanded: Boolean,
    searchState: ZNodeSearchState,
    onDismiss: () -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
) {
    val strings = Res.string
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
        modifier = Modifier
            .width(460.dp)
            .heightIn(max = 360.dp)
            .clip(ShellMetrics.CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), ShellMetrics.CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
    ) {
        when (searchState) {
            ZNodeSearchState.Idle -> Text(
                text = stringResource(strings.command_no_search_body),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is ZNodeSearchState.Running -> Text(
                text = stringResource(strings.command_scanned_nodes, searchState.scannedNodes),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is ZNodeSearchState.Failed -> Text(
                text = searchState.error.localized(),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            is ZNodeSearchState.Loaded -> {
                Text(
                    text = stringResource(
                        strings.command_hits_summary,
                        searchState.report.hits.size,
                        searchState.report.scannedNodes,
                        searchState.report.stopReason.name,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (searchState.report.hits.isEmpty()) {
                    Text(
                        text = stringResource(strings.command_no_search_body),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val pathLabel = stringResource(strings.common_path)
                    val dataLabel = stringResource(strings.common_data)
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 292.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        searchState.report.hits.forEach { hit ->
                            TopSearchResultRow(
                                path = hit.path,
                                body = buildString {
                                    if (hit.matchedPath) append(pathLabel)
                                    if (hit.matchedPath && hit.matchedData) append(" · ")
                                    if (hit.matchedData) append(hit.dataPreview ?: dataLabel)
                                },
                                onClick = { onSelectPath(hit.path) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopSearchResultRow(
    path: ZNodePath,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShellMetrics.FieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = path.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (ConnectionProfile) -> Unit,
    onDeleteConnection: (ConnectionId) -> Unit,
    onTestConnection: (ConnectionId) -> Unit,
    onDisconnectActiveConnection: () -> Unit,
    onReconnectActiveConnection: () -> Unit,
) {
    val strings = Res.string
    val defaultGroup = stringResource(strings.connection_default_group)
    var expanded by remember { mutableStateOf(false) }
    val connection = state.activeConnection
    val status = connection?.let { state.connectionStatuses[it.id] } ?: ConnectionRuntimeStatus.Disconnected
    Box {
        Row(
            modifier = Modifier
                .clip(ShellMetrics.FieldShape)
                .widthIn(max = 260.dp)
                .height(ShellMetrics.ControlHeight)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), ShellMetrics.FieldShape)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(status)
            Text(
                text = connection?.name ?: stringResource(strings.connection_none),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            connection?.connectionString?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(390.dp)
                .clip(ShellMetrics.CardShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), ShellMetrics.CardShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
        ) {
            if (state.connections.isEmpty()) {
                Text(
                    text = stringResource(strings.connection_none_saved),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.connections
                    .groupBy { it.tags.firstOrNull()?.takeIf(String::isNotBlank) ?: defaultGroup }
                    .toSortedMap()
                    .forEach { (group, profiles) ->
                        Text(
                            text = group,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        profiles.forEach { profile ->
                            ConnectionMenuRow(
                                profile = profile,
                                status = state.connectionStatuses[profile.id] ?: ConnectionRuntimeStatus.Disconnected,
                                selected = profile.id == state.activeConnectionId,
                                onSelect = {
                                    expanded = false
                                    onSelectConnection(profile.id)
                                },
                                onTest = {
                                    expanded = false
                                    onTestConnection(profile.id)
                                },
                                onEdit = {
                                    expanded = false
                                    onEditConnection(profile)
                                },
                                onDelete = {
                                    expanded = false
                                    onDeleteConnection(profile.id)
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                }
            }
            DividerLine(vertical = false)
            if (state.activeConnection != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                ) {
                    OutlinedButton(onClick = {
                        expanded = false
                        onReconnectActiveConnection()
                    }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(strings.connection_reconnect))
                    }
                    OutlinedButton(onClick = {
                        expanded = false
                        onDisconnectActiveConnection()
                    }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(strings.connection_disconnect))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(ShellMetrics.FieldShape)
                    .clickable {
                        expanded = false
                        onNewConnection()
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(strings.connection_add),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ConnectionMenuRow(
    profile: ConnectionProfile,
    status: ConnectionRuntimeStatus,
    selected: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val strings = Res.string
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(ShellMetrics.FieldShape)
            .background(background)
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(status)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (profile.mode == ConnectionMode.ReadWrite) {
                        stringResource(strings.mode_read_write)
                    } else {
                        stringResource(strings.mode_read_only)
                    },
                    modifier = Modifier
                        .clip(ShellMetrics.TreeRowShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = profile.connectionString,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ConnectionMenuActionButton(
            label = if (status == ConnectionRuntimeStatus.Connecting) {
                stringResource(strings.connection_testing)
            } else {
                stringResource(strings.connection_test)
            },
            icon = Icons.Outlined.Refresh,
            enabled = status != ConnectionRuntimeStatus.Connecting,
            onClick = onTest,
        )
        ConnectionMenuActionButton(
            label = stringResource(strings.connection_edit),
            icon = Icons.Outlined.Edit,
            onClick = onEdit,
        )
        ConnectionMenuActionButton(
            label = stringResource(strings.connection_delete),
            icon = Icons.Outlined.Delete,
            destructive = true,
            onClick = onDelete,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionMenuActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                    destructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ModeStatusPill(state: AppState) {
    val strings = Res.string
    EnvironmentPill(text = if (state.isReadOnly) stringResource(strings.mode_read_only) else stringResource(strings.mode_read_write))
}

@Composable
private fun BreadcrumbPath(
    path: ZNodePath?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val parts = (path ?: ZNodePath.Root).value.split("/").filter { it.isNotBlank() }
        Text("/", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val visibleParts = remember(parts) {
            when {
                parts.size <= 4 -> parts.map { BreadcrumbPart.Value(it) }
                else -> listOf(
                    BreadcrumbPart.Value(parts.first()),
                    BreadcrumbPart.Ellipsis,
                ) + parts.takeLast(2).map { BreadcrumbPart.Value(it) }
            }
        }
        visibleParts.forEachIndexed { index, part ->
            Text("›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val label = when (part) {
                BreadcrumbPart.Ellipsis -> "…"
                is BreadcrumbPart.Value -> part.text
            }
            val isLast = index == visibleParts.lastIndex
            Text(
                text = label,
                modifier = Modifier.widthIn(max = if (isLast) 170.dp else 130.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private sealed interface BreadcrumbPart {
    data object Ellipsis : BreadcrumbPart
    data class Value(val text: String) : BreadcrumbPart
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun IconGlyphButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(ShellMetrics.IconButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                content()
            }
        }
    }
}

@Composable
private fun ZkCliTerminal(
    state: AppState,
    settings: AppSettings,
    entries: List<TerminalEntry>,
    terminalHeight: Dp,
    command: TextFieldValue,
    onCommandChange: (TextFieldValue) -> Unit,
    onCompleteCommand: () -> Unit,
    onExecute: () -> Unit,
    onClear: () -> Unit,
) {
    val strings = Res.string
    var expanded by remember { mutableStateOf(settings.terminalExpandedByDefault) }
    LaunchedEffect(settings.terminalExpandedByDefault) {
        expanded = settings.terminalExpandedByDefault
    }
    val terminalBackground = when (settings.terminalThemePreference) {
        TerminalThemePreference.Auto -> MaterialTheme.colorScheme.surface
        TerminalThemePreference.Light -> Color(0xFFFBFCFC)
        TerminalThemePreference.Dark -> Color(0xFF151716)
    }
    val terminalTextColor = when (settings.terminalThemePreference) {
        TerminalThemePreference.Dark -> Color(0xFFE8ECEA)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val terminalMutedColor = when (settings.terminalThemePreference) {
        TerminalThemePreference.Dark -> Color(0xFF9BA8A3)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val terminalTextStyle = MaterialTheme.typography.bodySmall.copy(fontSize = settings.terminalFontSize.sp)
    val canExecute = command.text.isNotBlank() && state.activeConnection != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (expanded) terminalHeight else 48.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Row(
                modifier = Modifier
                    .height(ShellMetrics.ControlHeight)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(strings.terminal_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.weight(1f))
            if (expanded) {
                TextButton(onClick = onClear) { Text(stringResource(strings.common_clear)) }
            }
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(ShellMetrics.ControlHeight),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) stringResource(strings.terminal_collapse) else stringResource(
                        strings.terminal_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!expanded) return@Column

        DividerLine(vertical = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(terminalBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(strings.terminal_empty),
                    style = terminalTextStyle,
                    color = terminalMutedColor,
                    fontFamily = FontFamily.Monospace,
                )
            }
            entries.takeLast(12).forEachIndexed { index, entry ->
                Text(
                    text = buildString {
                        if (settings.terminalShowTimestamps) {
                            append("09:")
                            append((15 + index).toString().padStart(2, '0'))
                            append(":21   ")
                        }
                        append("[zk: ${state.activeConnection?.connectionString ?: "-"}] ${entry.command}")
                    },
                    style = terminalTextStyle,
                    fontFamily = FontFamily.Monospace,
                    color = terminalMutedColor,
                )
                Text(
                    text = entry.output,
                    style = terminalTextStyle,
                    fontFamily = FontFamily.Monospace,
                    color = if (entry.isError) MaterialTheme.colorScheme.error else terminalTextColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(terminalBackground)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShellTextInput(
                value = command,
                onValueChange = onCommandChange,
                placeholder = stringResource(strings.terminal_placeholder),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.Tab -> {
                                onCompleteCommand()
                                true
                            }

                            Key.Enter -> {
                                if (canExecute) {
                                    onExecute()
                                }
                                canExecute
                            }

                            else -> false
                        }
                    },
                monospace = true,
            )
            Box(
                modifier = Modifier
                    .height(ShellMetrics.ControlHeight)
                    .clip(ShellMetrics.FieldShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), ShellMetrics.FieldShape)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(strings.terminal_cursor))
            }
            Button(
                onClick = onExecute,
                enabled = canExecute,
                modifier = Modifier.height(ShellMetrics.ControlHeight),
                shape = ShellMetrics.FieldShape,
            ) {
                Text(stringResource(strings.terminal_execute))
            }
        }
    }
}

private data class TerminalEntry(
    val command: String,
    val output: String,
    val isError: Boolean = false,
)

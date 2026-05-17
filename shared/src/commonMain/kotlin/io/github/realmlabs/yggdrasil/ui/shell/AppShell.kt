package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.realmlabs.yggdrasil.application.state.*
import io.github.realmlabs.yggdrasil.domain.model.*

@Composable
fun AppShell(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    onCreateConnection: (ConnectionProfileDraft) -> Unit,
    onUpdateConnection: (ConnectionId, ConnectionProfileDraft) -> Unit,
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
    onExecuteZkCli: (ZkCliCommandRequest) -> Unit,
    onClearSelection: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
) {
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showCreateNodeDialog by remember { mutableStateOf(false) }
    var showDeleteNodeDialog by remember { mutableStateOf(false) }
    var showAclDialog by remember { mutableStateOf(false) }
    var showCommandDialog by remember { mutableStateOf(false) }
    var editingConnection by remember { mutableStateOf<ConnectionProfile?>(null) }
    var topSearch by remember { mutableStateOf("") }
    var terminalCommand by remember { mutableStateOf("") }
    var terminalEntries by remember { mutableStateOf<List<TerminalEntry>>(emptyList()) }
    var lastTerminalStateKey by remember { mutableStateOf("") }
    var inspectorExpanded by remember { mutableStateOf(state.settings.inspectorExpandedByDefault) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.settings.inspectorExpandedByDefault) {
        inspectorExpanded = state.settings.inspectorExpandedByDefault
    }

    LaunchedEffect(state.activeConnectionId, state.settings.clearTerminalOnConnectionChange) {
        if (state.settings.clearTerminalOnConnectionChange) {
            terminalEntries = emptyList()
        }
    }

    LaunchedEffect(state.zkCliState) {
        when (val cliState = state.zkCliState) {
            is ZkCliState.Loaded -> {
                val key = "ok:${cliState.result.commandLine}:${cliState.result.output.hashCode()}"
                if (key != lastTerminalStateKey) {
                    terminalEntries = terminalEntries + TerminalEntry(
                        command = cliState.result.commandLine,
                        output = cliState.result.output,
                    )
                    lastTerminalStateKey = key
                }
            }

            is ZkCliState.Failed -> {
                val key = "err:${cliState.request.commandLine}:${cliState.error.message}"
                if (key != lastTerminalStateKey) {
                    terminalEntries = terminalEntries + TerminalEntry(
                        command = cliState.request.commandLine,
                        output = cliState.error.message,
                        isError = true,
                    )
                    lastTerminalStateKey = key
                }
            }

            else -> Unit
        }
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
                search = topSearch,
                onSearchChange = { topSearch = it },
                onRunSearch = {
                    val root = state.selectedPath ?: ZNodePath.Root
                    onSearch(
                        ZNodeSearchRequest(
                            rootPath = root,
                            query = topSearch,
                            searchPath = state.settings.defaultSearchPath,
                            searchData = state.settings.defaultSearchData,
                        ),
                    )
                },
                onSelectConnection = onSelectConnection,
            )
            Row(Modifier.weight(1f).fillMaxWidth()) {
                TreePane(
                    state = state,
                    onSelectPath = onSelectPath,
                    onRefreshSelectedPath = onRefreshSelectedPath,
                    onCreateNode = { showCreateNodeDialog = true },
                    modifier = Modifier.width(316.dp).fillMaxHeight(),
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
                    expanded = inspectorExpanded,
                    onToggleExpanded = { inspectorExpanded = !inspectorExpanded },
                    onEditAcl = { showAclDialog = true },
                    modifier = Modifier.width(if (inspectorExpanded) 300.dp else 56.dp).fillMaxHeight(),
                )
            }
            DividerLine(vertical = false)
                if (state.settings.embeddedTerminalEnabled) {
                    ZkCliTerminal(
                        state = state,
                        settings = state.settings,
                        entries = terminalEntries,
                        command = terminalCommand,
                        onCommandChange = { terminalCommand = it },
                        onExecute = {
                            if (terminalCommand.isNotBlank()) {
                                onExecuteZkCli(ZkCliCommandRequest(terminalCommand))
                                terminalCommand = ""
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
            requireDangerousConfirmation = state.settings.requireDangerousConfirmation,
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
            onExecuteZkCli = onExecuteZkCli,
            onSelectPath = onSelectPath,
            onSelectConnection = onSelectConnection,
        )
    }
}

@Composable
private fun YggdrasilTopBar(
    state: AppState,
    search: String,
    onSearchChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onSelectConnection: (ConnectionId) -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (ConnectionProfile) -> Unit,
    onDeleteConnection: (ConnectionId) -> Unit,
    onTestConnection: (ConnectionId) -> Unit,
    onSettings: () -> Unit,
    onCommand: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandMark()
        Text(
            text = "Yggdrasil",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        ConnectionStatusPill(
            state = state,
            onSelectConnection = onSelectConnection,
            onNewConnection = onNewConnection,
            onEditConnection = onEditConnection,
            onDeleteConnection = onDeleteConnection,
            onTestConnection = onTestConnection,
        )
        ModeStatusPill(state)
        BreadcrumbPath(
            path = state.selectedPath,
            modifier = Modifier.weight(1f),
        )
        ShellTextInput(
            value = search,
            onValueChange = onSearchChange,
            placeholder = "Search nodes & data",
            modifier = Modifier.width(340.dp),
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
                    text = "⌘K",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        Button(
            onClick = onCommand,
            modifier = Modifier.width(132.dp).height(ShellMetrics.ControlHeight),
            shape = ShellMetrics.FieldShape,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            Text("⌘ Command")
        }
        IconGlyphButton(label = "Search", onClick = onRunSearch) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        IconGlyphButton(label = "Settings", onClick = onSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("●", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BrandMark() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(28.dp)) {
        val stroke = 2.2.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.88f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.2f, h * 0.26f), Offset(w * 0.8f, h * 0.26f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.2f, h * 0.50f), Offset(w * 0.8f, h * 0.50f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.2f, h * 0.74f), Offset(w * 0.8f, h * 0.74f), stroke, cap = StrokeCap.Round)
        drawCircle(color, radius = 2.8.dp.toPx(), center = Offset(w * 0.2f, h * 0.26f))
        drawCircle(color, radius = 2.8.dp.toPx(), center = Offset(w * 0.8f, h * 0.50f))
        drawCircle(color, radius = 2.8.dp.toPx(), center = Offset(w * 0.2f, h * 0.74f))
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
) {
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
                text = connection?.name ?: "No connection",
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
                    text = "No saved connections",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.connections.forEach { profile ->
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
            DividerLine(vertical = false)
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
                    text = "Add connection",
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
                    text = if (profile.mode == ConnectionMode.ReadWrite) "Read-write" else "Read-only",
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
            label = if (status == ConnectionRuntimeStatus.Connecting) "Testing connection" else "Test connection",
            icon = Icons.Outlined.Refresh,
            enabled = status != ConnectionRuntimeStatus.Connecting,
            onClick = onTest,
        )
        ConnectionMenuActionButton(
            label = "Edit connection",
            icon = Icons.Outlined.Edit,
            onClick = onEdit,
        )
        ConnectionMenuActionButton(
            label = "Delete connection",
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
    EnvironmentPill(text = if (state.isReadOnly) "Read-only" else "Read-write")
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
private fun IconGlyphButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
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

@Composable
private fun ZkCliTerminal(
    state: AppState,
    settings: AppSettings,
    entries: List<TerminalEntry>,
    command: String,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
    onClear: () -> Unit,
) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (expanded) 220.dp else 48.dp)
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
                    .clip(ShellMetrics.TreeRowShape)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("zkCli Terminal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (expanded) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(ShellMetrics.ControlHeight),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse terminal" else "Expand terminal",
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
                    text = "No zkCli commands yet.",
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
                placeholder = "Enter zkCli command...",
                modifier = Modifier.weight(1f),
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
                Text("Ln 4, Col 1")
            }
            Button(
                onClick = onExecute,
                enabled = command.isNotBlank() && state.activeConnection != null,
                modifier = Modifier.height(ShellMetrics.ControlHeight),
                shape = ShellMetrics.FieldShape,
            ) {
                Text("Execute  ⌘↵")
            }
        }
    }
}

private data class TerminalEntry(
    val command: String,
    val output: String,
    val isError: Boolean = false,
)

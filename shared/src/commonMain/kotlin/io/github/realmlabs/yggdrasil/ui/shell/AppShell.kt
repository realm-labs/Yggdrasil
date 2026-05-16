package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ConnectionRuntimeStatus
import io.github.realmlabs.yggdrasil.application.state.ZNodeDetailState
import io.github.realmlabs.yggdrasil.application.state.ZkCliState
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
    var inspectorExpanded by remember { mutableStateOf(true) }

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
        Column(Modifier.fillMaxSize()) {
            YggdrasilTopBar(
                state = state,
                onNewConnection = { showConnectionDialog = true },
                onCommand = { showCommandDialog = true },
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
            ZkCliTerminal(
                state = state,
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
        ConnectionStatusPill(state, onSelectConnection)
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
                SearchGlyph()
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
        IconGlyphButton(label = "Search", onClick = onRunSearch) { RefreshGlyph() }
        IconGlyphButton(label = "Settings", onClick = onNewConnection) { GearGlyph() }
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
                .clickable(enabled = state.connections.isNotEmpty()) { expanded = true }
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
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.connections.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(profile.name)
                            Text(
                                profile.connectionString,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectConnection(profile.id)
                    },
                )
            }
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
private fun SearchGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(16.dp)) {
        drawCircle(
            color = color,
            radius = size.minDimension * 0.32f,
            center = Offset(size.width * 0.44f, size.height * 0.42f),
            style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.67f, size.height * 0.66f),
            end = Offset(size.width * 0.90f, size.height * 0.90f),
            strokeWidth = 1.7.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun RefreshGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        drawArc(
            color = color,
            startAngle = 35f,
            sweepAngle = 275f,
            useCenter = false,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color,
            Offset(size.width * 0.76f, size.height * 0.18f),
            Offset(size.width * 0.92f, size.height * 0.18f),
            2.2.dp.toPx()
        )
        drawLine(
            color,
            Offset(size.width * 0.92f, size.height * 0.18f),
            Offset(size.width * 0.92f, size.height * 0.34f),
            2.2.dp.toPx()
        )
    }
}

@Composable
private fun GearGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        drawCircle(color, radius = size.minDimension * 0.30f, style = Stroke(width = 2.2.dp.toPx()))
        drawCircle(color, radius = size.minDimension * 0.08f)
        repeat(8) { index ->
            val angle = Math.toRadians((index * 45).toDouble())
            val start = Offset(
                x = size.width / 2 + kotlin.math.cos(angle).toFloat() * size.width * 0.38f,
                y = size.height / 2 + kotlin.math.sin(angle).toFloat() * size.height * 0.38f,
            )
            val end = Offset(
                x = size.width / 2 + kotlin.math.cos(angle).toFloat() * size.width * 0.48f,
                y = size.height / 2 + kotlin.math.sin(angle).toFloat() * size.height * 0.48f,
            )
            drawLine(color, start, end, 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun ZkCliTerminal(
    state: AppState,
    entries: List<TerminalEntry>,
    command: String,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

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
            Text("▣", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (expanded) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(ShellMetrics.ControlHeight),
            ) {
                Text(
                    text = if (expanded) "⌃" else "⌄",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!expanded) return@Column

        DividerLine(vertical = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = "No zkCli commands yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            entries.takeLast(12).forEachIndexed { index, entry ->
                Text(
                    text = "09:${
                        (15 + index).toString().padStart(2, '0')
                    }:21   [zk: ${state.activeConnection?.connectionString ?: "-"}] ${entry.command}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.output,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
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
            OutlinedButton(
                onClick = {},
                modifier = Modifier.height(ShellMetrics.ControlHeight),
                shape = ShellMetrics.FieldShape,
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

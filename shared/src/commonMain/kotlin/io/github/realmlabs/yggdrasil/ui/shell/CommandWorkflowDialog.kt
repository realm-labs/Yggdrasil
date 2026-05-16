package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.*
import io.github.realmlabs.yggdrasil.domain.model.*

@Composable
fun CommandWorkflowDialog(
    state: AppState,
    onDismiss: () -> Unit,
    onSearch: (ZNodeSearchRequest) -> Unit,
    onCancelSearch: () -> Unit,
    onExport: (Boolean, ZNodeDataEncoding) -> Unit,
    onImport: (ZNodeImportRequest) -> Unit,
    onCompare: (ZNodeCompareRequest) -> Unit,
    onCancelCompare: () -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
    onSelectConnection: (ConnectionId) -> Unit,
) {
    var section by remember { mutableStateOf(CommandSection.Search) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Command") },
        text = {
            Column(
                modifier = Modifier.width(720.dp).heightIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommandSection.entries.forEach { item ->
                        ModeButton(
                            text = item.label,
                            selected = section == item,
                            onClick = { section = item },
                        )
                    }
                }
                DividerLine(vertical = false)
                when (section) {
                    CommandSection.Search -> SearchCommandPane(state, onSearch, onCancelSearch, onSelectPath)
                    CommandSection.Export -> ExportCommandPane(state, onExport)
                    CommandSection.Import -> ImportCommandPane(state, onImport)
                    CommandSection.Compare -> CompareCommandPane(
                        state = state,
                        onCompare = onCompare,
                        onCancelCompare = onCancelCompare,
                        onSelectPath = onSelectPath,
                        onSelectConnection = onSelectConnection,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun SearchCommandPane(
    state: AppState,
    onSearch: (ZNodeSearchRequest) -> Unit,
    onCancelSearch: () -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var rootPath by remember(state.selectedPath) { mutableStateOf(state.selectedPath?.value ?: "/") }
    var searchPath by remember { mutableStateOf(true) }
    var searchData by remember { mutableStateOf(false) }
    var maxDepth by remember { mutableStateOf("8") }
    var maxNodes by remember { mutableStateOf("1000") }
    val running = state.searchState is ZNodeSearchState.Running

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Query") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = rootPath,
                onValueChange = { rootPath = it },
                label = { Text("Root") },
                singleLine = true,
                modifier = Modifier.width(170.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(selected = searchPath, onClick = { searchPath = !searchPath }, label = { Text("Path") })
            FilterChip(selected = searchData, onClick = { searchData = !searchData }, label = { Text("Data") })
            OutlinedTextField(
                value = maxDepth,
                onValueChange = { maxDepth = it.filter(Char::isDigit) },
                label = { Text("Max depth") },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            OutlinedTextField(
                value = maxNodes,
                onValueChange = { maxNodes = it.filter(Char::isDigit) },
                label = { Text("Max nodes") },
                singleLine = true,
                modifier = Modifier.width(130.dp),
            )
            Spacer(Modifier.weight(1f))
            if (running) {
                OutlinedButton(onClick = onCancelSearch) {
                    Text("Cancel")
                }
            }
            Button(
                enabled = !running,
                onClick = {
                    val parsedPath = ZNodePath.parse(rootPath)
                    if (parsedPath is OperationResult.Success) {
                        onSearch(
                            ZNodeSearchRequest(
                                rootPath = parsedPath.value,
                                query = query,
                                searchPath = searchPath,
                                searchData = searchData,
                                maxDepth = maxDepth.toIntOrNull()?.coerceAtLeast(0) ?: 8,
                                maxNodes = maxNodes.toIntOrNull()?.coerceAtLeast(1) ?: 1_000,
                            ),
                        )
                    }
                },
            ) {
                Text("Search")
            }
        }
        when (val search = state.searchState) {
            ZNodeSearchState.Idle -> EmptyPanelMessage("No search yet", "Search by path or data from the selected root.")
            is ZNodeSearchState.Running -> Text("Scanned ${search.scannedNodes} nodes", style = MaterialTheme.typography.bodySmall)
            is ZNodeSearchState.Failed -> Text(search.error.message, color = MaterialTheme.colorScheme.error)
            is ZNodeSearchState.Loaded -> {
                Text(
                    text = "Hits ${search.report.hits.size} · scanned ${search.report.scannedNodes} · ${search.report.stopReason}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ResultList {
                    search.report.hits.forEach { hit ->
                        ListResultRow(
                            title = hit.path.value,
                            body = buildString {
                                if (hit.matchedPath) append("path")
                                if (hit.matchedPath && hit.matchedData) append(" · ")
                                if (hit.matchedData) append(hit.dataPreview ?: "data")
                            },
                            onClick = { onSelectPath(hit.path) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportCommandPane(
    state: AppState,
    onExport: (Boolean, ZNodeDataEncoding) -> Unit,
) {
    var includeAcl by remember { mutableStateOf(false) }
    var encoding by remember { mutableStateOf(ZNodeDataEncoding.Text) }
    val selectedPath = state.selectedPath
    val running = state.exportState is ZNodeExportState.Running

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Root ${selectedPath?.value ?: "-"}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = includeAcl, onClick = { includeAcl = !includeAcl }, label = { Text("Include ACL") })
            ZNodeDataEncoding.entries.forEach { item ->
                FilterChip(selected = encoding == item, onClick = { encoding = item }, label = { Text(item.name) })
            }
            Spacer(Modifier.weight(1f))
            Button(enabled = selectedPath != null && !running, onClick = { onExport(includeAcl, encoding) }) {
                Text("Export JSON")
            }
        }
        when (val export = state.exportState) {
            ZNodeExportState.Idle -> EmptyPanelMessage("No export yet", "Exported JSON appears here.")
            is ZNodeExportState.Running -> Text("Exporting ${export.request.rootPath}", style = MaterialTheme.typography.bodySmall)
            is ZNodeExportState.Failed -> Text(export.error.message, color = MaterialTheme.colorScheme.error)
            is ZNodeExportState.Loaded -> {
                Text(
                    "Exported ${export.report.exportedNodes} nodes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = export.report.json,
                    onValueChange = {},
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                )
            }
        }
    }
}

@Composable
private fun ImportCommandPane(
    state: AppState,
    onImport: (ZNodeImportRequest) -> Unit,
) {
    var json by remember { mutableStateOf("") }
    var dryRun by remember { mutableStateOf(true) }
    var strategy by remember { mutableStateOf(ZNodeImportConflictStrategy.Skip) }
    val running = state.importState is ZNodeImportState.Running

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = json,
            onValueChange = { json = it },
            label = { Text("Import JSON") },
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = dryRun, onClick = { dryRun = !dryRun }, label = { Text("Dry run") })
            ZNodeImportConflictStrategy.entries.forEach { item ->
                FilterChip(selected = strategy == item, onClick = { strategy = item }, label = { Text(item.label) })
            }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = json.isNotBlank() && !running && !state.isReadOnly,
                onClick = {
                    onImport(
                        ZNodeImportRequest(
                            json = json,
                            dryRun = dryRun,
                            conflictStrategy = strategy,
                        ),
                    )
                },
            ) {
                Text(if (dryRun) "Plan" else "Import")
            }
        }
        when (val import = state.importState) {
            ZNodeImportState.Idle -> EmptyPanelMessage("No import plan", "Run dry run before applying imported JSON.")
            is ZNodeImportState.Running -> Text("Import is running", style = MaterialTheme.typography.bodySmall)
            is ZNodeImportState.Failed -> Text(import.error.message, color = MaterialTheme.colorScheme.error)
            is ZNodeImportState.Loaded -> {
                Text(
                    "Operations ${import.report.operations.size} · applied ${import.report.appliedCount} · failed ${import.report.failureCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ResultList {
                    import.report.operations.forEach { operation ->
                        ListResultRow(
                            title = "${operation.type} ${operation.path.value}",
                            body = operation.message,
                            onClick = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareCommandPane(
    state: AppState,
    onCompare: (ZNodeCompareRequest) -> Unit,
    onCancelCompare: () -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
    onSelectConnection: (ConnectionId) -> Unit,
) {
    var leftConnectionId by remember(state.activeConnectionId) { mutableStateOf(state.activeConnectionId ?: state.connections.firstOrNull()?.id) }
    var rightConnectionId by remember(state.connections) {
        mutableStateOf(state.connections.firstOrNull { it.id != leftConnectionId }?.id ?: state.connections.firstOrNull()?.id)
    }
    var leftRoot by remember(state.selectedPath) { mutableStateOf(state.selectedPath?.value ?: "/") }
    var rightRoot by remember(state.selectedPath) { mutableStateOf(state.selectedPath?.value ?: "/") }
    var includeAcl by remember { mutableStateOf(true) }
    var maxNodes by remember { mutableStateOf("1000") }
    val running = state.compareState is ZNodeCompareState.Running

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectionPicker(
                label = "Left",
                connections = state.connections,
                selectedId = leftConnectionId,
                onSelected = { leftConnectionId = it },
                modifier = Modifier.weight(1f),
            )
            ConnectionPicker(
                label = "Right",
                connections = state.connections,
                selectedId = rightConnectionId,
                onSelected = { rightConnectionId = it },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = leftRoot,
                onValueChange = { leftRoot = it },
                label = { Text("Left root") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = rightRoot,
                onValueChange = { rightRoot = it },
                label = { Text("Right root") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = maxNodes,
                onValueChange = { maxNodes = it.filter(Char::isDigit) },
                label = { Text("Max nodes") },
                singleLine = true,
                modifier = Modifier.width(130.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = includeAcl, onClick = { includeAcl = !includeAcl }, label = { Text("Compare ACL") })
            Spacer(Modifier.weight(1f))
            if (running) {
                OutlinedButton(onClick = onCancelCompare) {
                    Text("Cancel")
                }
            }
            Button(
                enabled = !running && leftConnectionId != null && rightConnectionId != null,
                onClick = {
                    val leftPath = ZNodePath.parse(leftRoot)
                    val rightPath = ZNodePath.parse(rightRoot)
                    if (leftPath is OperationResult.Success && rightPath is OperationResult.Success) {
                        onCompare(
                            ZNodeCompareRequest(
                                leftConnectionId = leftConnectionId!!,
                                leftRootPath = leftPath.value,
                                rightConnectionId = rightConnectionId!!,
                                rightRootPath = rightPath.value,
                                includeAcl = includeAcl,
                                maxNodes = maxNodes.toIntOrNull()?.coerceAtLeast(1) ?: 1_000,
                            ),
                        )
                    }
                },
            ) {
                Text("Compare")
            }
        }
        when (val compare = state.compareState) {
            ZNodeCompareState.Idle -> EmptyPanelMessage("No comparison yet", "Differences across existence, data, and ACL appear here.")
            is ZNodeCompareState.Running -> Text("Scanned ${compare.scannedNodes} nodes", style = MaterialTheme.typography.bodySmall)
            is ZNodeCompareState.Failed -> Text(compare.error.message, color = MaterialTheme.colorScheme.error)
            is ZNodeCompareState.Loaded -> {
                Text(
                    "Differences ${compare.report.differences.size} · scanned ${compare.report.scannedNodes} · ${compare.report.stopReason}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ResultList {
                    compare.report.differences.forEach { difference ->
                        ListResultRow(
                            title = "${difference.type} ${difference.relativePath}",
                            body = difference.message,
                            onClick = difference.leftPath?.let { leftPath ->
                                {
                                    compare.report.request.leftConnectionId.let(onSelectConnection)
                                    onSelectPath(leftPath)
                                }
                            } ?: difference.rightPath?.let { rightPath ->
                                {
                                    compare.report.request.rightConnectionId.let(onSelectConnection)
                                    onSelectPath(rightPath)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionPicker(
    label: String,
    connections: List<ConnectionProfile>,
    selectedId: ConnectionId?,
    onSelected: (ConnectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = connections.firstOrNull { it.id == selectedId }?.name ?: "Select"

    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $selectedName", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            connections.forEach { connection ->
                DropdownMenuItem(
                    text = { Text(connection.name) },
                    onClick = {
                        onSelected(connection.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun ListResultRow(
    title: String,
    body: String,
    onClick: (() -> Unit)?,
) {
    val colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    TextButton(
        onClick = onClick ?: {},
        enabled = onClick != null,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            Text(
                body.ifBlank { "-" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class CommandSection(val label: String) {
    Search("Search"),
    Export("Export"),
    Import("Import"),
    Compare("Compare"),
}

private val ZNodeImportConflictStrategy.label: String
    get() = when (this) {
        ZNodeImportConflictStrategy.Skip -> "skip"
        ZNodeImportConflictStrategy.Overwrite -> "overwrite"
        ZNodeImportConflictStrategy.CreateOnly -> "create-only"
    }

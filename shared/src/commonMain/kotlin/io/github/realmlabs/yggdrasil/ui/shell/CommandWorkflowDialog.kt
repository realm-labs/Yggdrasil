package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.realmlabs.yggdrasil.application.state.*
import io.github.realmlabs.yggdrasil.application.workflow.completeZkCliCommandLine
import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.platform.chooseFilePath
import io.github.realmlabs.yggdrasil.platform.chooseSaveFilePath
import io.github.realmlabs.yggdrasil.platform.readTextFile
import io.github.realmlabs.yggdrasil.platform.writeTextFile
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*

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
    onExecuteZkCli: (ZkCliCommandRequest) -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
    onSelectConnection: (ConnectionId) -> Unit,
    onRefreshSelectedPath: () -> Unit,
    onReconnectActiveConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
) {
    val strings = Res.string
    var section by remember { mutableStateOf(CommandSection.Quick) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(1120.dp).heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(strings.command_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(strings.common_close))
                    }
                }
                CommandSectionNav(
                    selected = section,
                    onSelect = { section = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                DividerLine(vertical = false)
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 560.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    when (section) {
                        CommandSection.Quick -> QuickActionsPane(
                            state = state,
                            onSelectPath = onSelectPath,
                            onSelectConnection = onSelectConnection,
                            onRefreshSelectedPath = onRefreshSelectedPath,
                            onReconnectActiveConnection = onReconnectActiveConnection,
                            onOpenSettings = onOpenSettings,
                            onUpdateSettings = onUpdateSettings,
                        )

                        CommandSection.ZkCli -> ZkCliCommandPane(state, onExecuteZkCli)
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
            }
        }
    }
}

@Composable
private fun CommandSectionNav(
    selected: CommandSection,
    onSelect: (CommandSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CommandSection.entries.forEach { section ->
            CommandNavButton(
                section = section,
                selected = selected == section,
                onClick = { onSelect(section) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CommandNavButton(
    section: CommandSection,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = modifier.height(42.dp)
    if (selected) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            shape = ShellMetrics.FieldShape,
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            CommandNavContent(section)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            shape = ShellMetrics.FieldShape,
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            CommandNavContent(section)
        }
    }
}

@Composable
private fun CommandNavContent(section: CommandSection) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(section.icon(), contentDescription = null, modifier = Modifier.size(17.dp))
        Text(section.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickActionsPane(
    state: AppState,
    onSelectPath: (ZNodePath) -> Unit,
    onSelectConnection: (ConnectionId) -> Unit,
    onRefreshSelectedPath: () -> Unit,
    onReconnectActiveConnection: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
) {
    val strings = Res.string
    var pathText by remember(state.selectedPath) { mutableStateOf(state.selectedPath?.value ?: "/") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pathText,
                onValueChange = { pathText = it },
                label = { Text(stringResource(strings.command_jump_path)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                when (val result = ZNodePath.parse(pathText)) {
                    is OperationResult.Success -> onSelectPath(result.value)
                    is OperationResult.Failure -> Unit
                }
            }) {
                Text(stringResource(strings.command_jump))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRefreshSelectedPath, enabled = state.selectedPath != null) {
                Text(stringResource(strings.tree_refresh_selected))
            }
            OutlinedButton(onClick = onReconnectActiveConnection, enabled = state.activeConnection != null) {
                Text(stringResource(strings.connection_reconnect))
            }
            OutlinedButton(onClick = onOpenSettings) {
                Text(stringResource(strings.common_settings))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(strings.settings_application_theme), style = MaterialTheme.typography.labelMedium)
            ThemePreference.entries.forEach { theme ->
                FilterChip(
                    selected = state.settings.themePreference == theme,
                    onClick = { onUpdateSettings(state.settings.copy(themePreference = theme)) },
                    label = { Text(theme.name) },
                )
            }
        }
        ResultList {
            state.connections.forEach { connection ->
                ListResultRow(
                    title = connection.name,
                    body = connection.connectionString,
                    onClick = { onSelectConnection(connection.id) },
                )
            }
            (state.favoritePathsFor() + state.recentPathsFor()).distinct().take(12).forEach { path ->
                ListResultRow(
                    title = path.value,
                    body = stringResource(strings.command_jump_path),
                    onClick = { onSelectPath(path) },
                )
            }
        }
    }
}

@Composable
private fun ZkCliCommandPane(
    state: AppState,
    onExecuteZkCli: (ZkCliCommandRequest) -> Unit,
) {
    val strings = Res.string
    var command by remember { mutableStateOf(TextFieldValue("ls /", selection = TextRange("ls /".length))) }
    val running = state.zkCliState is ZkCliState.Running
    val completionPaths = remember(state.znodeChildren, state.selectedPath) {
        state.knownZkCliPaths()
    }
    val canExecute = command.text.isNotBlank() && state.activeConnection != null && !running
    val executeCommand = {
        if (canExecute) {
            onExecuteZkCli(ZkCliCommandRequest(command.text))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text(stringResource(strings.command_zkcli)) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        when (event.key) {
                            Key.Tab -> {
                                val completion = completeZkCliCommandLine(
                                    commandLine = command.text,
                                    cursor = command.selection.start,
                                    knownPaths = completionPaths,
                                )
                                command = TextFieldValue(
                                    text = completion.commandLine,
                                    selection = TextRange(completion.cursor),
                                )
                                true
                            }

                            Key.Enter -> {
                                executeCommand()
                                canExecute
                            }

                            else -> false
                        }
                    },
            )
            Button(
                enabled = canExecute,
                onClick = executeCommand,
            ) {
                Text(stringResource(strings.common_run))
            }
        }
        Text(
            text = stringResource(
                strings.command_zkcli_description,
                state.activeConnection?.name ?: stringResource(strings.command_no_active_connection),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val cli = state.zkCliState) {
            ZkCliState.Idle -> EmptyPanelMessage(
                stringResource(strings.command_no_run_title),
                stringResource(strings.command_no_run_body),
            )

            is ZkCliState.Running -> Text(
                stringResource(strings.command_running, cli.request.commandLine),
                style = MaterialTheme.typography.bodySmall
            )

            is ZkCliState.Failed -> Text(cli.error.localized(), color = MaterialTheme.colorScheme.error)
            is ZkCliState.Loaded -> CommandOutput(localizedZkCliOutput(cli.result.output))
        }
    }
}

@Composable
private fun SearchCommandPane(
    state: AppState,
    onSearch: (ZNodeSearchRequest) -> Unit,
    onCancelSearch: () -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
) {
    val strings = Res.string
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
                label = { Text(stringResource(strings.common_query)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = rootPath,
                onValueChange = { rootPath = it },
                label = { Text(stringResource(strings.common_root)) },
                singleLine = true,
                modifier = Modifier.width(170.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilterChip(
                selected = searchPath,
                onClick = { searchPath = !searchPath },
                label = { Text(stringResource(strings.common_path)) })
            FilterChip(
                selected = searchData,
                onClick = { searchData = !searchData },
                label = { Text(stringResource(strings.common_data)) })
            OutlinedTextField(
                value = maxDepth,
                onValueChange = { maxDepth = it.filter(Char::isDigit) },
                label = { Text(stringResource(strings.command_max_depth)) },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
            OutlinedTextField(
                value = maxNodes,
                onValueChange = { maxNodes = it.filter(Char::isDigit) },
                label = { Text(stringResource(strings.command_max_nodes)) },
                singleLine = true,
                modifier = Modifier.width(130.dp),
            )
            Spacer(Modifier.weight(1f))
            if (running) {
                OutlinedButton(onClick = onCancelSearch) {
                    Text(stringResource(strings.common_cancel))
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
                Text(stringResource(strings.common_search))
            }
        }
        when (val search = state.searchState) {
            ZNodeSearchState.Idle -> EmptyPanelMessage(
                stringResource(strings.command_no_search_title),
                stringResource(strings.command_no_search_body)
            )

            is ZNodeSearchState.Running -> Text(
                stringResource(strings.command_scanned_nodes, search.scannedNodes),
                style = MaterialTheme.typography.bodySmall
            )

            is ZNodeSearchState.Failed -> Text(search.error.localized(), color = MaterialTheme.colorScheme.error)
            is ZNodeSearchState.Loaded -> {
                Text(
                    text = stringResource(
                        strings.command_hits_summary,
                        search.report.hits.size,
                        search.report.scannedNodes,
                        search.report.stopReason.localized(),
                    ),
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
    val strings = Res.string
    val exportFileTitle = stringResource(strings.command_export_file_title)
    val fileSavedPrefix = stringResource(strings.command_file_saved_prefix)
    val fileWriteFailed = stringResource(strings.error_file_write_failed)
    var includeAcl by remember { mutableStateOf(false) }
    var encoding by remember { mutableStateOf(ZNodeDataEncoding.Text) }
    var fileStatus by remember { mutableStateOf<String?>(null) }
    val selectedPath = state.selectedPath
    val running = state.exportState is ZNodeExportState.Running

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(strings.command_root_summary, selectedPath?.value ?: "-"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = includeAcl,
                onClick = { includeAcl = !includeAcl },
                label = { Text(stringResource(strings.command_include_acl)) })
            ZNodeDataEncoding.entries.forEach { item ->
                FilterChip(selected = encoding == item, onClick = { encoding = item }, label = { Text(item.name) })
            }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = selectedPath != null && !running,
                onClick = { onExport(includeAcl, encoding) },
                modifier = Modifier.width(132.dp).height(ShellMetrics.ControlHeight),
                shape = ShellMetrics.FieldShape,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(stringResource(strings.command_export_json), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (val export = state.exportState) {
            ZNodeExportState.Idle -> EmptyPanelMessage(
                stringResource(strings.command_no_export_title),
                stringResource(strings.command_no_export_body)
            )

            is ZNodeExportState.Running -> Text(
                stringResource(
                    strings.command_exporting,
                    export.request.rootPath.value
                ), style = MaterialTheme.typography.bodySmall
            )

            is ZNodeExportState.Failed -> Text(export.error.localized(), color = MaterialTheme.colorScheme.error)
            is ZNodeExportState.Loaded -> {
                Text(
                    stringResource(strings.command_exported_nodes, export.report.exportedNodes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val file = chooseSaveFilePath(
                                title = exportFileTitle,
                                currentPath = "znode-export.json",
                            )
                            if (file != null) {
                                fileStatus = when (val result = writeTextFile(file, export.report.json)) {
                                    is OperationResult.Success -> "$fileSavedPrefix $file"
                                    is OperationResult.Failure -> result.error.cause?.let { "$fileWriteFailed $it" }
                                        ?: fileWriteFailed
                                }
                            }
                        },
                    ) {
                        Text(stringResource(strings.command_save_file))
                    }
                }
                fileStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    val strings = Res.string
    val importFileTitle = stringResource(strings.command_import_file_title)
    val fileLoadedPrefix = stringResource(strings.command_file_loaded_prefix)
    val fileReadFailed = stringResource(strings.error_file_read_failed)
    var json by remember { mutableStateOf("") }
    var dryRun by remember { mutableStateOf(true) }
    var strategy by remember { mutableStateOf(ZNodeImportConflictStrategy.Skip) }
    var fileStatus by remember { mutableStateOf<String?>(null) }
    val running = state.importState is ZNodeImportState.Running

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                enabled = !running,
                onClick = {
                    val file = chooseFilePath(importFileTitle)
                    if (file != null) {
                        when (val result = readTextFile(file)) {
                            is OperationResult.Success -> {
                                json = result.value
                                fileStatus = "$fileLoadedPrefix $file"
                            }

                            is OperationResult.Failure -> {
                                fileStatus = result.error.cause?.let { "$fileReadFailed $it" } ?: fileReadFailed
                            }
                        }
                    }
                },
            ) {
                Text(stringResource(strings.command_load_file))
            }
            fileStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = json,
            onValueChange = { json = it },
            label = { Text(stringResource(strings.command_import_json)) },
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = dryRun,
                onClick = { dryRun = !dryRun },
                label = { Text(stringResource(strings.command_dry_run)) })
            ZNodeImportConflictStrategy.entries.forEach { item ->
                FilterChip(selected = strategy == item, onClick = { strategy = item }, label = { Text(item.label()) })
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
                modifier = Modifier.width(132.dp).height(ShellMetrics.ControlHeight),
                shape = ShellMetrics.FieldShape,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(
                    if (dryRun) stringResource(strings.command_plan) else stringResource(strings.command_import),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (val import = state.importState) {
            ZNodeImportState.Idle -> EmptyPanelMessage(
                stringResource(strings.command_no_import_title),
                stringResource(strings.command_no_import_body)
            )

            is ZNodeImportState.Running -> Text(
                stringResource(strings.command_import_running),
                style = MaterialTheme.typography.bodySmall
            )

            is ZNodeImportState.Failed -> Text(import.error.localized(), color = MaterialTheme.colorScheme.error)
            is ZNodeImportState.Loaded -> {
                Text(
                    stringResource(
                        strings.command_operations_summary,
                        import.report.operations.size,
                        import.report.appliedCount,
                        import.report.failureCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ResultList {
                    import.report.operations.forEach { operation ->
                        ListResultRow(
                            title = "${operation.localizedType()} ${operation.path.value}",
                            body = operation.localizedMessage(),
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
    val strings = Res.string
    var leftConnectionId by remember(state.activeConnectionId) { mutableStateOf(state.activeConnectionId ?: state.connections.firstOrNull()?.id) }
    var rightConnectionId by remember(state.connections) {
        mutableStateOf(state.connections.firstOrNull { it.id != leftConnectionId }?.id ?: state.connections.firstOrNull()?.id)
    }
    var leftRoot by remember(state.selectedPath) { mutableStateOf(state.selectedPath?.value ?: "/") }
    var rightRoot by remember(state.selectedPath) { mutableStateOf(state.selectedPath?.value ?: "/") }
    var includeAcl by remember { mutableStateOf(true) }
    var maxNodes by remember { mutableStateOf("1000") }
    val running = state.compareState is ZNodeCompareState.Running

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(strings.command_compare),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnectionPicker(
                    label = stringResource(strings.command_left),
                    connections = state.connections,
                    selectedId = leftConnectionId,
                    onSelected = { leftConnectionId = it },
                    modifier = Modifier.weight(1f),
                )
                ConnectionPicker(
                    label = stringResource(strings.command_right),
                    connections = state.connections,
                    selectedId = rightConnectionId,
                    onSelected = { rightConnectionId = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = leftRoot,
                onValueChange = { leftRoot = it },
                label = { Text(stringResource(strings.command_left_root)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = rightRoot,
                onValueChange = { rightRoot = it },
                label = { Text(stringResource(strings.command_right_root)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = includeAcl,
                onClick = { includeAcl = !includeAcl },
                label = { Text(stringResource(strings.command_compare_acl), maxLines = 1) },
            )
            OutlinedTextField(
                value = maxNodes,
                onValueChange = { maxNodes = it.filter(Char::isDigit) },
                label = { Text(stringResource(strings.command_max_nodes)) },
                singleLine = true,
                modifier = Modifier.width(140.dp),
            )
            Spacer(Modifier.weight(1f))
            if (running) {
                OutlinedButton(
                    onClick = onCancelCompare,
                    modifier = Modifier.width(96.dp).height(ShellMetrics.ControlHeight),
                    shape = ShellMetrics.FieldShape,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(stringResource(strings.common_cancel), maxLines = 1)
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
                modifier = Modifier.width(96.dp).height(ShellMetrics.ControlHeight),
                shape = ShellMetrics.FieldShape,
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(stringResource(strings.command_compare), maxLines = 1)
            }
        }
        DividerLine(vertical = false)
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 340.dp)) {
            CompareResultContent(
                state = state,
                onSelectPath = onSelectPath,
                onSelectConnection = onSelectConnection,
            )
        }
    }
}

@Composable
private fun CompareResultContent(
    state: AppState,
    onSelectPath: (ZNodePath) -> Unit,
    onSelectConnection: (ConnectionId) -> Unit,
) {
    val strings = Res.string
    when (val compare = state.compareState) {
        ZNodeCompareState.Idle -> EmptyPanelMessage(
            stringResource(strings.command_no_compare_title),
            stringResource(strings.command_no_compare_body)
        )

        is ZNodeCompareState.Running -> Text(
            stringResource(strings.command_scanned_nodes, compare.scannedNodes),
            style = MaterialTheme.typography.bodySmall
        )

        is ZNodeCompareState.Failed -> Text(compare.error.localized(), color = MaterialTheme.colorScheme.error)
        is ZNodeCompareState.Loaded -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        strings.command_differences_summary,
                        compare.report.differences.size,
                        compare.report.scannedNodes,
                        compare.report.stopReason.localized(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ResultList {
                    compare.report.differences.forEach { difference ->
                        ListResultRow(
                            title = "${difference.localizedType()} ${difference.relativePath}",
                            body = difference.localizedMessage(),
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
    val strings = Res.string
    var expanded by remember { mutableStateOf(false) }
    val selectedName = connections.firstOrNull { it.id == selectedId }?.name ?: stringResource(strings.common_select)

    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(ShellMetrics.ControlHeight),
            shape = ShellMetrics.FieldShape,
            contentPadding = PaddingValues(horizontal = 12.dp),
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

@Composable
private fun CommandOutput(output: String) {
    OutlinedTextField(
        value = output,
        onValueChange = {},
        readOnly = true,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().height(320.dp),
    )
}

private enum class CommandSection {
    Quick,
    ZkCli,
    Search,
    Export,
    Import,
    Compare,
}

@Composable
private fun CommandSection.label(): String {
    val strings = Res.string
    return when (this) {
        CommandSection.ZkCli -> stringResource(strings.command_zkcli)
        CommandSection.Quick -> stringResource(strings.command_quick)
        CommandSection.Search -> stringResource(strings.command_search)
        CommandSection.Export -> stringResource(strings.command_export)
        CommandSection.Import -> stringResource(strings.command_import)
        CommandSection.Compare -> stringResource(strings.command_compare)
    }
}

private fun CommandSection.icon(): ImageVector = when (this) {
    CommandSection.Quick -> Icons.Outlined.Tune
    CommandSection.ZkCli -> Icons.Outlined.Terminal
    CommandSection.Search -> Icons.Outlined.Search
    CommandSection.Export -> Icons.Outlined.FileDownload
    CommandSection.Import -> Icons.Outlined.FileUpload
    CommandSection.Compare -> Icons.AutoMirrored.Outlined.CompareArrows
}

@Composable
private fun ZNodeImportConflictStrategy.label(): String {
    val strings = Res.string
    return when (this) {
        ZNodeImportConflictStrategy.Skip -> stringResource(strings.command_strategy_skip)
        ZNodeImportConflictStrategy.Overwrite -> stringResource(strings.command_strategy_overwrite)
        ZNodeImportConflictStrategy.CreateOnly -> stringResource(strings.command_strategy_create_only)
    }
}

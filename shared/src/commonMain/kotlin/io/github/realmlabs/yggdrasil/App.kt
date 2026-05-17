package io.github.realmlabs.yggdrasil

import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import io.github.realmlabs.yggdrasil.application.state.YggdrasilStateHolder
import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.platform.LocalAppLocale
import io.github.realmlabs.yggdrasil.platform.createYggdrasilServices
import io.github.realmlabs.yggdrasil.ui.shell.AppShell
import io.github.realmlabs.yggdrasil.ui.theme.YggdrasilTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val services = remember { createYggdrasilServices() }
    val stateHolder = remember {
        YggdrasilStateHolder(
            appSettingsRepository = services.appSettingsRepository,
            connectionProfileRepository = services.connectionProfileRepository,
            credentialRepository = services.credentialRepository,
            zooKeeperConnectionTester = services.zooKeeperConnectionTester,
            zNodeRepository = services.zNodeRepository,
        )
    }
    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var compareJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(stateHolder) {
        stateHolder.loadSettings()
        stateHolder.loadConnections()
    }

    LaunchedEffect(
        stateHolder,
        stateHolder.state.activeConnectionId,
        stateHolder.state.watchState.watchedPath,
        stateHolder.state.settings.autoWatchSelectedNode,
    ) {
        if (!stateHolder.state.settings.autoWatchSelectedNode) return@LaunchedEffect
        stateHolder.watchSelectedPath()
            ?.catch { error ->
                stateHolder.reportWatchError(
                    AppError.ZooKeeper(
                        message = "Watch failed.",
                        cause = error.message,
                    ),
                )
            }
            ?.collect { event ->
                stateHolder.processWatchEvent(event)
            }
    }

    CompositionLocalProvider(LocalAppLocale provides stateHolder.state.settings.language.localeTag) {
        key(LocalAppLocale.current) {
            YggdrasilTheme(themePreference = stateHolder.state.settings.themePreference) {
                AppShell(
                    state = stateHolder.state,
                    onSelectConnection = { id -> coroutineScope.launch { stateHolder.selectConnection(id) } },
                    onCreateConnection = { draft -> coroutineScope.launch { stateHolder.createConnection(draft) } },
                    onUpdateConnection = { id, draft ->
                        coroutineScope.launch {
                            stateHolder.updateConnection(
                                id,
                                draft
                            )
                        }
                    },
                    onDeleteConnection = { id -> coroutineScope.launch { stateHolder.deleteConnection(id) } },
                    onTestConnection = { id -> coroutineScope.launch { stateHolder.testConnection(id) } },
                    onSelectPath = { path -> coroutineScope.launch { stateHolder.selectPath(path) } },
                    onRefreshSelectedPath = { coroutineScope.launch { stateHolder.refreshSelectedPath() } },
                    onCreateNode = { request -> coroutineScope.launch { stateHolder.createNode(request) } },
                    onUpdateNodeData = { data, expectedVersion ->
                        coroutineScope.launch { stateHolder.updateSelectedNodeData(data, expectedVersion) }
                    },
                    onPreviewDeleteNode = { recursive ->
                        coroutineScope.launch { stateHolder.previewDeleteSelectedNode(recursive) }
                    },
                    onDeletePreviewedNode = { confirmation ->
                        coroutineScope.launch {
                            stateHolder.deletePreviewedNode(
                                confirmation = confirmation,
                                requireConfirmation = stateHolder.state.settings.requireDangerousConfirmation,
                            )
                        }
                    },
                    onClearDeletePreview = stateHolder::clearDeletePreview,
                    onUpdateAcl = { acl, expectedAversion ->
                        coroutineScope.launch { stateHolder.updateSelectedAcl(acl, expectedAversion) }
                    },
                    onSearch = { request ->
                        searchJob?.cancel()
                        searchJob = coroutineScope.launch { stateHolder.searchZNodes(request) }
                    },
                    onCancelSearch = {
                        searchJob?.cancel()
                        stateHolder.markSearchCanceled()
                    },
                    onExport = { includeAcl, dataEncoding ->
                        coroutineScope.launch { stateHolder.exportSelectedSubtree(includeAcl, dataEncoding) }
                    },
                    onImport = { request ->
                        coroutineScope.launch { stateHolder.importZNodeTree(request) }
                    },
                    onCompare = { request ->
                        compareJob?.cancel()
                        compareJob = coroutineScope.launch { stateHolder.compareConnections(request) }
                    },
                    onCancelCompare = {
                        compareJob?.cancel()
                        stateHolder.markCompareCanceled()
                    },
                    onExecuteZkCli = { request ->
                        coroutineScope.launch { stateHolder.executeZkCliCommand(request) }
                    },
                    onClearSelection = stateHolder::clearSelection,
                    onUpdateSettings = { settings ->
                        coroutineScope.launch { stateHolder.updateSettings(settings) }
                    },
                )
            }
        }
    }
}

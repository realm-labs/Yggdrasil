package io.github.realmlabs.yggdrasil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import io.github.realmlabs.yggdrasil.application.state.YggdrasilStateHolder
import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.platform.createYggdrasilServices
import io.github.realmlabs.yggdrasil.ui.shell.AppShell
import io.github.realmlabs.yggdrasil.ui.theme.YggdrasilTheme
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val services = remember { createYggdrasilServices() }
    val stateHolder = remember {
        YggdrasilStateHolder(
            connectionProfileRepository = services.connectionProfileRepository,
            zooKeeperConnectionTester = services.zooKeeperConnectionTester,
            zNodeRepository = services.zNodeRepository,
        )
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(stateHolder) {
        stateHolder.loadConnections()
    }

    LaunchedEffect(
        stateHolder,
        stateHolder.state.activeConnectionId,
        stateHolder.state.watchState.watchedPath,
    ) {
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

    YggdrasilTheme {
        AppShell(
            state = stateHolder.state,
            onSelectConnection = stateHolder::selectConnection,
            onCreateConnection = { draft -> coroutineScope.launch { stateHolder.createConnection(draft) } },
            onDeleteConnection = { id -> coroutineScope.launch { stateHolder.deleteConnection(id) } },
            onTestConnection = { id -> coroutineScope.launch { stateHolder.testConnection(id) } },
            onSelectPath = { path -> coroutineScope.launch { stateHolder.selectPath(path) } },
            onRefreshSelectedPath = { coroutineScope.launch { stateHolder.refreshSelectedPath() } },
            onClearSelection = stateHolder::clearSelection,
        )
    }
}

package io.github.realmlabs.yggdrasil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import io.github.realmlabs.yggdrasil.application.state.YggdrasilStateHolder
import io.github.realmlabs.yggdrasil.platform.createYggdrasilServices
import io.github.realmlabs.yggdrasil.ui.shell.AppShell
import io.github.realmlabs.yggdrasil.ui.theme.YggdrasilTheme
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val services = remember { createYggdrasilServices() }
    val stateHolder = remember {
        YggdrasilStateHolder(
            connectionProfileRepository = services.connectionProfileRepository,
            zooKeeperConnectionTester = services.zooKeeperConnectionTester,
        )
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(stateHolder) {
        stateHolder.loadConnections()
    }

    YggdrasilTheme {
        AppShell(
            state = stateHolder.state,
            onSelectConnection = stateHolder::selectConnection,
            onCreateConnection = { draft -> coroutineScope.launch { stateHolder.createConnection(draft) } },
            onDeleteConnection = { id -> coroutineScope.launch { stateHolder.deleteConnection(id) } },
            onTestConnection = { id -> coroutineScope.launch { stateHolder.testConnection(id) } },
            onSelectPath = stateHolder::selectPath,
            onClearSelection = stateHolder::clearSelection,
        )
    }
}

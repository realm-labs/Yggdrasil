package io.github.realmlabs.yggdrasil

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import io.github.realmlabs.yggdrasil.application.state.YggdrasilStateHolder
import io.github.realmlabs.yggdrasil.ui.shell.AppShell
import io.github.realmlabs.yggdrasil.ui.theme.YggdrasilTheme

@Composable
@Preview
fun App() {
    val stateHolder = remember { YggdrasilStateHolder() }

    YggdrasilTheme {
        AppShell(
            state = stateHolder.state,
            onSelectConnection = stateHolder::selectConnection,
            onSelectPath = stateHolder::selectPath,
            onClearSelection = stateHolder::clearSelection,
        )
    }
}

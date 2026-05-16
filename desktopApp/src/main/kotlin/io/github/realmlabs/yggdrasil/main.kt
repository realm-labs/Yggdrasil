package io.github.realmlabs.yggdrasil

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 1280.dp, height = 820.dp),
        title = "Yggdrasil",
    ) {
        App()
    }
}

package io.github.realmlabs.yggdrasil.ui.shell

import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ZNodeChildrenState

internal fun AppState.knownZkCliPaths(): List<String> {
    val paths = mutableSetOf("/")
    selectedPath?.let { paths += it.value }
    znodeChildren.forEach { (parent, childrenState) ->
        paths += parent.value
        if (childrenState is ZNodeChildrenState.Loaded) {
            paths += childrenState.children.map { it.path.value }
        }
    }
    return paths.sorted()
}

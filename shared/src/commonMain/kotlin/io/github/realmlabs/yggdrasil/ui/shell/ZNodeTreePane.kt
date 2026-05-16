package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.*
import io.github.realmlabs.yggdrasil.domain.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Composable
fun TreePane(
    state: AppState,
    onSelectPath: (ZNodePath) -> Unit,
    onRefreshSelectedPath: () -> Unit,
    onCreateNode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Znodes",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCreateNode,
                    enabled = state.activeConnection != null && !state.isReadOnly,
                ) {
                    Text("New")
                }
                OutlinedButton(
                    onClick = onRefreshSelectedPath,
                    enabled = state.selectedPath != null,
                ) {
                    Text("Refresh")
                }
            }
        },
    ) {
        val activeConnection = state.activeConnection
        if (activeConnection == null) {
            EmptyPanelMessage(
                title = "No active connection",
                body = "Select a saved connection before loading the znode tree.",
            )
            return@Panel
        }

        var expandedPaths by remember(state.activeConnectionId) {
            mutableStateOf(setOf(ZNodePath.Root))
        }

        fun togglePath(path: ZNodePath, expandable: Boolean) {
            if (!expandable) {
                onSelectPath(path)
                return
            }

            if (path in expandedPaths) {
                expandedPaths = expandedPaths - path
            } else {
                expandedPaths = expandedPaths + path
                onSelectPath(path)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val rootChildrenState = state.znodeChildren[ZNodePath.Root]
            TreeNodeRow(
                path = ZNodePath.Root,
                depth = 0,
                selected = state.selectedPath == ZNodePath.Root,
                childrenState = rootChildrenState,
                expandable = rootChildrenState !is ZNodeChildrenState.Loaded || rootChildrenState.children.isNotEmpty(),
                expanded = ZNodePath.Root in expandedPaths,
                onClick = {
                    togglePath(
                        path = ZNodePath.Root,
                        expandable = rootChildrenState !is ZNodeChildrenState.Loaded || rootChildrenState.children.isNotEmpty(),
                    )
                },
            )
            TreeChildren(
                state = state,
                expandedPaths = expandedPaths,
                parent = ZNodePath.Root,
                depth = 1,
                onTogglePath = ::togglePath,
            )
        }
    }
}

@Composable
private fun TreeChildren(
    state: AppState,
    expandedPaths: Set<ZNodePath>,
    parent: ZNodePath,
    depth: Int,
    onTogglePath: (ZNodePath, Boolean) -> Unit,
) {
    if (parent !in expandedPaths) return

    when (val childrenState = state.znodeChildren[parent]) {
        null,
        ZNodeChildrenState.Unloaded -> if (parent == ZNodePath.Root) {
            TreeStateMessage(
                text = "Loading root children",
                depth = depth,
            )
        }

        ZNodeChildrenState.Loading -> TreeStateMessage(
            text = "Loading...",
            depth = depth,
        )

        is ZNodeChildrenState.Failed -> TreeStateMessage(
            text = childrenState.error.message,
            depth = depth,
            isError = true,
        )

        is ZNodeChildrenState.Loaded -> {
            if (childrenState.children.isEmpty()) {
                TreeStateMessage(
                    text = "No children",
                    depth = depth,
                )
            }
            childrenState.children.forEach { child ->
                val childChildrenState = state.znodeChildren[child.path]
                val expandable =
                    child.hasChildren || childChildrenState is ZNodeChildrenState.Loaded && childChildrenState.children.isNotEmpty()
                TreeNodeRow(
                    path = child.path,
                    depth = depth,
                    selected = state.selectedPath == child.path,
                    childrenState = childChildrenState,
                    expandable = expandable,
                    expanded = child.path in expandedPaths,
                    onClick = { onTogglePath(child.path, expandable) },
                )
                TreeChildren(
                    state = state,
                    expandedPaths = expandedPaths,
                    parent = child.path,
                    depth = depth + 1,
                    onTogglePath = onTogglePath,
                )
            }
        }
    }
}

@Composable
private fun TreeNodeRow(
    path: ZNodePath,
    depth: Int,
    selected: Boolean,
    childrenState: ZNodeChildrenState?,
    expandable: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    val isLoading = childrenState == ZNodeChildrenState.Loading
    val isFailed = childrenState is ZNodeChildrenState.Failed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(
                PaddingValues(
                    start = (10 + depth * 16).dp,
                    top = 8.dp,
                    end = 10.dp,
                    bottom = 8.dp,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TreeDisclosureIcon(
            expandable = expandable,
            expanded = expanded,
            loading = isLoading,
            failed = isFailed,
        )
        Text(
            text = if (path == ZNodePath.Root) path.value else path.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TreeDisclosureIcon(
    expandable: Boolean,
    expanded: Boolean,
    loading: Boolean,
    failed: Boolean,
) {
    val color = when {
        failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.size(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = color,
            )

            failed -> Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )

            expandable -> Canvas(Modifier.size(14.dp)) {
                val strokeWidth = 1.7.dp.toPx()
                if (expanded) {
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.25f, size.height * 0.40f),
                        end = Offset(size.width * 0.50f, size.height * 0.65f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.50f, size.height * 0.65f),
                        end = Offset(size.width * 0.75f, size.height * 0.40f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                } else {
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.40f, size.height * 0.25f),
                        end = Offset(size.width * 0.65f, size.height * 0.50f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = color,
                        start = Offset(size.width * 0.65f, size.height * 0.50f),
                        end = Offset(size.width * 0.40f, size.height * 0.75f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeStateMessage(
    text: String,
    depth: Int,
    isError: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (10 + depth * 16).dp, top = 4.dp, end = 10.dp, bottom = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

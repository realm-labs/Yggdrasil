package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ZNodeChildrenState
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath

@Composable
fun TreePane(
    state: AppState,
    onSelectPath: (ZNodePath) -> Unit,
    onRefreshSelectedPath: () -> Unit,
    onCreateNode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Znode Explorer",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        var filter by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ShellTextInput(
                value = filter,
                onValueChange = { filter = it },
                placeholder = "Filter znodes...",
                modifier = Modifier.weight(1f),
            )
            TreeToolbarIconButton(
                onClick = onRefreshSelectedPath,
                enabled = state.selectedPath != null,
            ) {
                TreeRefreshGlyph(enabled = state.selectedPath != null)
            }
            TreeToolbarIconButton(
                onClick = onCreateNode,
                enabled = state.activeConnection != null && !state.isReadOnly,
            ) {
                TreeMenuGlyph(enabled = state.activeConnection != null && !state.isReadOnly)
            }
        }
        val activeConnection = state.activeConnection
        if (activeConnection == null) {
            EmptyPanelMessage(
                title = "No active connection",
                body = "Select a saved connection before loading the znode tree.",
            )
        } else {
            var expandedPaths by remember(state.activeConnectionId) {
                mutableStateOf(setOf(ZNodePath.Root))
            }

            fun togglePath(path: ZNodePath, expandable: Boolean, childrenState: ZNodeChildrenState?) {
                if (!expandable) return
                val wasExpanded = path in expandedPaths
                if (wasExpanded) {
                    expandedPaths = expandedPaths - path
                } else {
                    expandedPaths = expandedPaths + path
                }
                if (!wasExpanded && childrenState !is ZNodeChildrenState.Loaded && childrenState != ZNodeChildrenState.Loading) {
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
                    onSelect = { onSelectPath(ZNodePath.Root) },
                    onToggle = {
                        togglePath(
                            path = ZNodePath.Root,
                            expandable = rootChildrenState !is ZNodeChildrenState.Loaded || rootChildrenState.children.isNotEmpty(),
                            childrenState = rootChildrenState,
                        )
                    },
                )
                TreeChildren(
                    state = state,
                    expandedPaths = expandedPaths,
                    parent = ZNodePath.Root,
                    depth = 1,
                    filter = filter,
                    onSelectPath = onSelectPath,
                    onTogglePath = ::togglePath,
                )
            }
        }
    }
}

@Composable
private fun TreeToolbarIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(ShellMetrics.ControlHeight),
        shape = ShellMetrics.FieldShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun TreeRefreshGlyph(enabled: Boolean) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Canvas(Modifier.size(20.dp)) {
        drawArc(
            color = color,
            startAngle = 35f,
            sweepAngle = 280f,
            useCenter = false,
            style = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.74f, size.height * 0.15f),
            end = Offset(size.width * 0.94f, size.height * 0.15f),
            strokeWidth = 2.1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.94f, size.height * 0.15f),
            end = Offset(size.width * 0.94f, size.height * 0.35f),
            strokeWidth = 2.1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun TreeMenuGlyph(enabled: Boolean) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Column(
        modifier = Modifier.size(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(vertical = 1.5.dp)
                    .size(3.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color),
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
    filter: String,
    onSelectPath: (ZNodePath) -> Unit,
    onTogglePath: (ZNodePath, Boolean, ZNodeChildrenState?) -> Unit,
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
            childrenState.children
                .filter { child -> filter.isBlank() || child.path.value.contains(filter, ignoreCase = true) }
                .forEach { child ->
                val childChildrenState = state.znodeChildren[child.path]
                val expandable =
                    child.hasChildren || (childChildrenState is ZNodeChildrenState.Loaded && childChildrenState.children.isNotEmpty())
                TreeNodeRow(
                    path = child.path,
                    depth = depth,
                    selected = state.selectedPath == child.path,
                    childrenState = childChildrenState,
                    expandable = expandable,
                    expanded = child.path in expandedPaths,
                    onSelect = { onSelectPath(child.path) },
                    onToggle = { onTogglePath(child.path, expandable, childChildrenState) },
                )
                TreeChildren(
                    state = state,
                    expandedPaths = expandedPaths,
                    parent = child.path,
                    depth = depth + 1,
                    filter = filter,
                    onSelectPath = onSelectPath,
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
    onSelect: () -> Unit,
    onToggle: () -> Unit,
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
            .clip(ShellMetrics.TreeRowShape)
            .background(background)
            .clickable(onClick = onSelect)
            .padding(
                PaddingValues(
                    start = (6 + depth * 16).dp,
                    top = 7.dp,
                    end = 10.dp,
                    bottom = 7.dp,
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
            onClick = onToggle,
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
    onClick: () -> Unit,
) {
    val color = when {
        failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = expandable || loading || failed, onClick = onClick),
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

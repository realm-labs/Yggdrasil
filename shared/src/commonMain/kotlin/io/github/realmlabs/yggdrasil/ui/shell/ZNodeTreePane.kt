package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.*
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*
import kotlin.math.roundToInt

data class ZNodeTreeRevealRequest(
    val id: Int,
    val path: ZNodePath,
)

@Composable
fun TreePane(
    state: AppState,
    onSelectPath: (ZNodePath) -> Unit,
    onRefreshSelectedPath: () -> Unit,
    onToggleFavoritePath: (ZNodePath) -> Unit,
    revealRequest: ZNodeTreeRevealRequest? = null,
    modifier: Modifier = Modifier,
) {
    val strings = Res.string
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(strings.tree_title),
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
                placeholder = stringResource(strings.tree_filter_placeholder),
                modifier = Modifier.weight(1f),
            )
            TreeToolbarIconButton(
                label = stringResource(strings.tree_refresh_selected),
                onClick = onRefreshSelectedPath,
                enabled = state.selectedPath != null,
            ) {
                TreeToolbarIcon(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = stringResource(strings.tree_refresh_selected),
                    enabled = state.selectedPath != null,
                )
            }
            val selectedPath = state.selectedPath
            TreeToolbarIconButton(
                label = stringResource(strings.tree_toggle_favorite),
                onClick = { selectedPath?.let(onToggleFavoritePath) },
                enabled = selectedPath != null,
            ) {
                TreeToolbarIcon(
                    icon = if (selectedPath != null && state.isFavoritePath(selectedPath)) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(strings.tree_toggle_favorite),
                    enabled = selectedPath != null,
                )
            }
        }
        val activeConnection = state.activeConnection
        if (activeConnection == null) {
            EmptyPanelMessage(
                title = stringResource(strings.tree_no_active_connection_title),
                body = stringResource(strings.tree_no_active_connection_body),
            )
        } else {
            FavoriteAndRecentPaths(
                state = state,
                onSelectPath = onSelectPath,
            )
            var expandedPaths by remember(state.activeConnectionId) {
                mutableStateOf(setOf(ZNodePath.Root))
            }
            val treeScrollState = rememberScrollState()
            val coroutineScope = rememberCoroutineScope()
            var viewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            var viewportHeight by remember { mutableStateOf(0) }
            var centeredPath by remember { mutableStateOf<ZNodePath?>(null) }
            var selectedRowCoordinates by remember { mutableStateOf<Pair<ZNodePath, LayoutCoordinates>?>(null) }
            var revealRequestedPath by remember(state.activeConnectionId) { mutableStateOf<ZNodePath?>(null) }

            fun selectTreePath(path: ZNodePath) {
                onSelectPath(path)
            }

            fun revealSelectedPath(path: ZNodePath, rowCoordinates: LayoutCoordinates) {
                if (path != state.selectedPath || path != revealRequestedPath || centeredPath == path) return
                val viewport = viewportCoordinates ?: return
                if (viewportHeight <= 0) return
                val rowTop = viewport.localPositionOf(rowCoordinates, Offset.Zero).y
                val rowBottom = rowTop + rowCoordinates.size.height
                if (rowTop >= 0f && rowBottom <= viewportHeight) {
                    centeredPath = path
                    revealRequestedPath = null
                    return
                }
                val rowCenter = rowTop + rowCoordinates.size.height / 2f
                val target = (treeScrollState.value + rowCenter - viewportHeight / 2f)
                    .roundToInt()
                    .coerceIn(0, treeScrollState.maxValue)
                centeredPath = path
                revealRequestedPath = null
                coroutineScope.launch {
                    treeScrollState.animateScrollTo(target)
                }
            }

            fun requestReveal(path: ZNodePath) {
                centeredPath = null
                revealRequestedPath = path
                selectedRowCoordinates
                    ?.takeIf { (rowPath, _) -> rowPath == path }
                    ?.let { (_, coordinates) -> revealSelectedPath(path, coordinates) }
            }

            fun handleSelectedPositioned(path: ZNodePath, rowCoordinates: LayoutCoordinates) {
                selectedRowCoordinates = path to rowCoordinates
                revealSelectedPath(path, rowCoordinates)
            }

            LaunchedEffect(state.selectedPath) {
                state.selectedPath?.let { selectedPath ->
                    expandedPaths = expandedPaths + selectedPath.ancestorPaths()
                    if (revealRequestedPath != null && selectedPath != revealRequestedPath) {
                        centeredPath = null
                        revealRequestedPath = null
                    }
                }
            }

            LaunchedEffect(revealRequest?.id) {
                val request = revealRequest ?: return@LaunchedEffect
                filter = ""
                expandedPaths = expandedPaths + request.path.ancestorPaths()
                requestReveal(request.path)
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
                    selectTreePath(path)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        viewportCoordinates = coordinates
                        viewportHeight = coordinates.size.height
                    }
                    .verticalScroll(treeScrollState),
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
                    onSelect = { selectTreePath(ZNodePath.Root) },
                    onToggle = {
                        togglePath(
                            path = ZNodePath.Root,
                            expandable = rootChildrenState !is ZNodeChildrenState.Loaded || rootChildrenState.children.isNotEmpty(),
                            childrenState = rootChildrenState,
                        )
                    },
                    onSelectedPositioned = ::handleSelectedPositioned,
                )
                TreeChildren(
                    state = state,
                    expandedPaths = expandedPaths,
                    parent = ZNodePath.Root,
                    depth = 1,
                    filter = filter,
                    onSelectPath = ::selectTreePath,
                    onTogglePath = ::togglePath,
                    onSelectedPositioned = ::handleSelectedPositioned,
                )
            }
        }
    }
}

@Composable
private fun FavoriteAndRecentPaths(
    state: AppState,
    onSelectPath: (ZNodePath) -> Unit,
) {
    val strings = Res.string
    val favorites = state.favoritePathsFor()
    val recent = state.recentPathsFor().filterNot { it in favorites }.take(4)
    if (favorites.isEmpty() && recent.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (favorites.isNotEmpty()) {
            PathShortcutRow(stringResource(strings.tree_favorites), favorites.take(5), onSelectPath)
        }
        if (recent.isNotEmpty()) {
            PathShortcutRow(stringResource(strings.tree_recent_paths), recent, onSelectPath)
        }
    }
}

@Composable
private fun PathShortcutRow(
    label: String,
    paths: List<ZNodePath>,
    onSelectPath: (ZNodePath) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        paths.forEach { path ->
            Text(
                text = path.name.ifBlank { "/" },
                modifier = Modifier
                    .clip(ShellMetrics.TreeRowShape)
                    .clickable { onSelectPath(path) }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun ZNodePath.ancestorPaths(): List<ZNodePath> =
    generateSequence(parent) { it.parent }.toList().asReversed()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TreeToolbarIconButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
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
}

@Composable
private fun TreeToolbarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        },
        modifier = Modifier.size(21.dp),
    )
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
    onSelectedPositioned: (ZNodePath, LayoutCoordinates) -> Unit,
) {
    val strings = Res.string
    if (parent !in expandedPaths) return

    when (val childrenState = state.znodeChildren[parent]) {
        null,
        ZNodeChildrenState.Unloaded -> if (parent == ZNodePath.Root) {
            TreeStateMessage(
                text = stringResource(strings.tree_loading_root_children),
                depth = depth,
            )
        }

        ZNodeChildrenState.Loading -> TreeStateMessage(
            text = stringResource(strings.tree_loading),
            depth = depth,
        )

        is ZNodeChildrenState.Failed -> TreeStateMessage(
            text = childrenState.error.localized(),
            depth = depth,
            isError = true,
        )

        is ZNodeChildrenState.Loaded -> {
            if (childrenState.children.isEmpty()) {
                TreeStateMessage(
                    text = stringResource(strings.tree_no_children),
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
                    onSelectedPositioned = onSelectedPositioned,
                )
                TreeChildren(
                    state = state,
                    expandedPaths = expandedPaths,
                    parent = child.path,
                    depth = depth + 1,
                    filter = filter,
                    onSelectPath = onSelectPath,
                    onTogglePath = onTogglePath,
                    onSelectedPositioned = onSelectedPositioned,
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
    onSelectedPositioned: (ZNodePath, LayoutCoordinates) -> Unit,
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
            .onGloballyPositioned { coordinates ->
                if (selected) {
                    onSelectedPositioned(path, coordinates)
                }
            }
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

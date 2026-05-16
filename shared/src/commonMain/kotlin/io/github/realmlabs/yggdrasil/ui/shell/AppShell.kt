package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ConnectionRuntimeStatus
import io.github.realmlabs.yggdrasil.application.state.NodeSelectionState
import io.github.realmlabs.yggdrasil.domain.model.ConnectionId
import io.github.realmlabs.yggdrasil.domain.model.ConnectionMode
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath

@Composable
fun AppShell(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    onSelectPath: (ZNodePath) -> Unit,
    onClearSelection: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                ConnectionPane(
                    state = state,
                    onSelectConnection = onSelectConnection,
                    modifier = Modifier.width(280.dp).fillMaxHeight(),
                )
                DividerLine(vertical = true)
                TreePane(
                    state = state,
                    onSelectPath = onSelectPath,
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                )
                DividerLine(vertical = true)
                NodeDetailPane(
                    state = state,
                    onClearSelection = onClearSelection,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                DividerLine(vertical = true)
                InspectorPane(
                    state = state,
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                )
            }
            DividerLine(vertical = false)
            StatusBar(state)
        }
    }
}

@Composable
private fun TopBar(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Yggdrasil",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        EnvironmentPill(text = state.activeConnection?.name ?: "No connection")
        if (state.isReadOnly) {
            EnvironmentPill(text = "Read only")
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = {}) {
            Text("Command")
        }
        Button(onClick = {}) {
            Text("New connection")
        }
    }
}

@Composable
private fun ConnectionPane(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Connections",
        modifier = modifier,
    ) {
        if (state.connections.isEmpty()) {
            EmptyPanelMessage(
                title = "No saved connections",
                body = "Create a ZooKeeper connection to start browsing znodes.",
            )
            return@Panel
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.connections.forEach { connection ->
                ConnectionRow(
                    connection = connection,
                    status = state.connectionStatuses[connection.id] ?: ConnectionRuntimeStatus.Disconnected,
                    selected = connection.id == state.activeConnectionId,
                    onClick = { onSelectConnection(connection.id) },
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    connection: ConnectionProfile,
    status: ConnectionRuntimeStatus,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = connection.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusDot(status)
        }
        Text(
            text = connection.connectionString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (connection.mode == ConnectionMode.ReadWrite) "Read/write" else "Read only",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TreePane(
    state: AppState,
    onSelectPath: (ZNodePath) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Znodes",
        modifier = modifier,
    ) {
        val activeConnection = state.activeConnection
        if (activeConnection == null) {
            EmptyPanelMessage(
                title = "No active connection",
                body = "Select a saved connection before loading the znode tree.",
            )
            return@Panel
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TreeRow(
                path = ZNodePath.Root,
                selected = state.nodeSelection is NodeSelectionState.SelectedPath &&
                    state.nodeSelection.path == ZNodePath.Root,
                onClick = { onSelectPath(ZNodePath.Root) },
            )
            EmptyPanelMessage(
                title = "No children loaded",
                body = "Refresh the selected path after the connection is online.",
            )
        }
    }
}

@Composable
private fun TreeRow(
    path: ZNodePath,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = ">",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = path.value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NodeDetailPane(
    state: AppState,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Node data",
        modifier = modifier,
        trailing = {
            OutlinedButton(onClick = onClearSelection) {
                Text("Clear")
            }
        },
    ) {
        when (val selection = state.nodeSelection) {
            NodeSelectionState.None -> EmptyPanelMessage(
                title = "No znode selected",
                body = "Choose a path from the tree to inspect data, stat, and ACL details.",
            )

            is NodeSelectionState.SelectedPath -> PlaceholderEditor(selection.path)
            is NodeSelectionState.Loading -> EmptyPanelMessage(
                title = "Loading ${selection.path}",
                body = "The node detail request is in progress.",
            )

            is NodeSelectionState.Failed -> EmptyPanelMessage(
                title = "Could not load ${selection.path}",
                body = selection.error.message,
            )
        }
    }
}

@Composable
private fun PlaceholderEditor(path: ZNodePath) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = path.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(14.dp),
        ) {
            Text(
                text = "No node data loaded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InspectorPane(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Inspector",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InspectorSection(
                title = "Stat",
                rows = listOf(
                    "Version" to "-",
                    "Children" to "-",
                    "Modified" to "-",
                ),
            )
            InspectorSection(
                title = "ACL",
                rows = listOf(
                    "Entries" to "-",
                    "Mode" to if (state.isReadOnly) "Read only" else "Read/write",
                ),
            )
            InspectorSection(
                title = "Watch",
                rows = listOf(
                    "State" to "Not registered",
                    "Last event" to "-",
                ),
            )
        }
    }
}

@Composable
private fun InspectorSection(
    title: String,
    rows: List<Pair<String, String>>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        rows.forEach { (label, value) ->
            Row {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Panel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun EmptyPanelMessage(
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusBar(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.statusMessage,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Idle",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(status: ConnectionRuntimeStatus) {
    val color = when (status) {
        ConnectionRuntimeStatus.Connected -> Color(0xFF2E7D32)
        ConnectionRuntimeStatus.Connecting -> Color(0xFFF9A825)
        ConnectionRuntimeStatus.Suspended -> Color(0xFFF57C00)
        ConnectionRuntimeStatus.Lost -> Color(0xFFC62828)
        ConnectionRuntimeStatus.Disconnected -> MaterialTheme.colorScheme.outline
        is ConnectionRuntimeStatus.Failed -> Color(0xFFC62828)
    }

    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

@Composable
private fun EnvironmentPill(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DividerLine(vertical: Boolean) {
    Box(
        modifier = if (vertical) {
            Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        } else {
            Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        },
    )
}

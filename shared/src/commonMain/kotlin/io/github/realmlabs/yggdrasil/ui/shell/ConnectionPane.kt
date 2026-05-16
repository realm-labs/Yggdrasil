package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ConnectionRuntimeStatus
import io.github.realmlabs.yggdrasil.domain.model.*

@Composable
fun ConnectionPane(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    onEditConnection: (ConnectionProfile) -> Unit,
    onDeleteConnection: (ConnectionId) -> Unit,
    onTestConnection: (ConnectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Connections",
        modifier = modifier,
    ) {
        if (state.isLoadingConnections) {
            EmptyPanelMessage(
                title = "Loading connections",
                body = "Saved connection profiles are being loaded.",
            )
            return@Panel
        }

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
                    onTest = { onTestConnection(connection.id) },
                    onEdit = { onEditConnection(connection) },
                    onDelete = { onDeleteConnection(connection.id) },
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
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (connection.mode == ConnectionMode.ReadWrite) "Read/write" else "Read only",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ConnectionActionButton(
                    label = if (status == ConnectionRuntimeStatus.Connecting) "Testing connection" else "Test connection",
                    icon = ConnectionActionIcon.Test,
                    enabled = status != ConnectionRuntimeStatus.Connecting,
                    onClick = onTest,
                )
                ConnectionActionButton(
                    label = "Edit connection",
                    icon = ConnectionActionIcon.Edit,
                    onClick = onEdit,
                )
                ConnectionActionButton(
                    label = "Delete connection",
                    icon = ConnectionActionIcon.Delete,
                    onClick = onDelete,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionActionButton(
    label: String,
    icon: ConnectionActionIcon,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(label)
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(36.dp)
                .semantics { contentDescription = label },
        ) {
            ConnectionActionIconCanvas(
                icon = icon,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ConnectionActionIconCanvas(
    icon: ConnectionActionIcon,
    enabled: Boolean,
) {
    val color = if (enabled) {
        when (icon) {
            ConnectionActionIcon.Delete -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
    }

    Canvas(Modifier.size(18.dp)) {
        val strokeWidth = 2.dp.toPx()
        when (icon) {
            ConnectionActionIcon.Test -> {
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.28f,
                    center = Offset(size.width * 0.42f, size.height * 0.50f),
                    style = Stroke(width = strokeWidth),
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.62f, size.height * 0.50f),
                    end = Offset(size.width * 0.92f, size.height * 0.50f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.82f, size.height * 0.34f),
                    end = Offset(size.width * 0.82f, size.height * 0.66f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            ConnectionActionIcon.Edit -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.24f, size.height * 0.76f),
                    end = Offset(size.width * 0.76f, size.height * 0.24f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.64f, size.height * 0.16f),
                    end = Offset(size.width * 0.84f, size.height * 0.36f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.18f, size.height * 0.82f),
                    end = Offset(size.width * 0.36f, size.height * 0.76f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            ConnectionActionIcon.Delete -> {
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.24f, size.height * 0.30f),
                    end = Offset(size.width * 0.76f, size.height * 0.30f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width * 0.40f, size.height * 0.18f),
                    end = Offset(size.width * 0.60f, size.height * 0.18f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.30f, size.height * 0.38f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.40f, size.height * 0.44f),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}

private enum class ConnectionActionIcon {
    Test,
    Edit,
    Delete,
}

@Composable
fun ConnectionDialog(
    profile: ConnectionProfile? = null,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfileDraft) -> Unit,
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var connectionString by remember(profile?.id) { mutableStateOf(profile?.connectionString.orEmpty()) }
    var chroot by remember(profile?.id) { mutableStateOf(profile?.chroot?.value.orEmpty()) }
    var readWrite by remember(profile?.id) { mutableStateOf(profile?.mode == ConnectionMode.ReadWrite) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (profile == null) "New ZooKeeper connection" else "Edit ZooKeeper connection")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = connectionString,
                    onValueChange = { connectionString = it },
                    label = { Text("Connection string") },
                    placeholder = { Text("localhost:2181") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = chroot,
                    onValueChange = { chroot = it },
                    label = { Text("Chroot") },
                    placeholder = { Text("/app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeButton(
                        text = "Read only",
                        selected = !readWrite,
                        onClick = { readWrite = false },
                        modifier = Modifier.weight(1f),
                    )
                    ModeButton(
                        text = "Read/write",
                        selected = readWrite,
                        onClick = { readWrite = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                validationMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val draft = ConnectionProfileDraft(
                        name = name,
                        connectionString = connectionString,
                        chroot = chroot,
                        mode = if (readWrite) ConnectionMode.ReadWrite else ConnectionMode.ReadOnly,
                    )
                    when (val validation = draft.toProfile(ConnectionId("validation"))) {
                        is OperationResult.Success -> onSave(draft)
                        is OperationResult.Failure -> validationMessage = validation.error.message
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

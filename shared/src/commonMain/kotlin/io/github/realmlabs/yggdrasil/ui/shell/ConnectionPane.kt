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
fun ConnectionPane(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
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
        Text(
            text = if (connection.mode == ConnectionMode.ReadWrite) "Read/write" else "Read only",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onTest,
                modifier = Modifier.weight(1f),
                enabled = status != ConnectionRuntimeStatus.Connecting,
            ) {
                Text(if (status == ConnectionRuntimeStatus.Connecting) "Testing" else "Test")
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
fun ConnectionDialog(
    onDismiss: () -> Unit,
    onSave: (ConnectionProfileDraft) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var connectionString by remember { mutableStateOf("") }
    var chroot by remember { mutableStateOf("") }
    var readWrite by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("New ZooKeeper connection")
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

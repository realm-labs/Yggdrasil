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
fun CreateNodeDialog(
    selectedPath: ZNodePath?,
    onDismiss: () -> Unit,
    onCreate: (CreateZNodeRequest) -> Unit,
) {
    val defaultPath = selectedPath?.let { path ->
        if (path == ZNodePath.Root) "/new-node" else "${path.value}/new-node"
    } ?: "/new-node"
    var pathText by remember { mutableStateOf(defaultPath) }
    var dataText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ZNodeCreateMode.Persistent) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create znode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text("Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = dataText,
                    onValueChange = { dataText = it },
                    label = { Text("Data") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton(
                            text = "Persistent",
                            selected = mode == ZNodeCreateMode.Persistent,
                            onClick = { mode = ZNodeCreateMode.Persistent },
                            modifier = Modifier.weight(1f),
                        )
                        ModeButton(
                            text = "Ephemeral",
                            selected = mode == ZNodeCreateMode.Ephemeral,
                            onClick = { mode = ZNodeCreateMode.Ephemeral },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton(
                            text = "Persistent seq",
                            selected = mode == ZNodeCreateMode.PersistentSequential,
                            onClick = { mode = ZNodeCreateMode.PersistentSequential },
                            modifier = Modifier.weight(1f),
                        )
                        ModeButton(
                            text = "Ephemeral seq",
                            selected = mode == ZNodeCreateMode.EphemeralSequential,
                            onClick = { mode = ZNodeCreateMode.EphemeralSequential },
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                    when (val parsedPath = ZNodePath.parse(pathText)) {
                        is OperationResult.Success -> onCreate(
                            CreateZNodeRequest(
                                path = parsedPath.value,
                                data = dataText.encodeToByteArray(),
                                mode = mode,
                            ),
                        )

                        is OperationResult.Failure -> validationMessage = parsedPath.error.message
                    }
                },
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun DeleteNodeDialog(
    state: AppState,
    onPreview: (Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedPath = state.selectedPath
    var recursive by remember(selectedPath) { mutableStateOf(false) }
    var confirmation by remember(selectedPath) { mutableStateOf("") }
    val preview = state.deletePreview as? DeletePreviewState.Loaded
    val requiresConfirmation = preview?.preview?.requiresFullPathConfirmation == true
    val canDelete = preview != null && (!requiresConfirmation || confirmation == preview.preview.rootPath.value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete znode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = selectedPath?.value ?: "No selected path",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = recursive,
                        onCheckedChange = { recursive = it },
                    )
                    Text("Recursive delete")
                }
                OutlinedButton(
                    onClick = { onPreview(recursive) },
                    enabled = selectedPath != null,
                ) {
                    Text("Preview")
                }
                when (val previewState = state.deletePreview) {
                    DeletePreviewState.None -> Text(
                        text = "Preview the delete before confirming.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is DeletePreviewState.Loading -> Text(
                        text = "Loading delete preview for ${previewState.path}",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    is DeletePreviewState.Failed -> Text(
                        text = previewState.error.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    is DeletePreviewState.Loaded -> {
                        Text(
                            text = "${previewState.preview.paths.size} node${if (previewState.preview.paths.size == 1) "" else "s"} will be deleted.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .verticalScroll(rememberScrollState())
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            previewState.preview.paths.forEach { path ->
                                Text(
                                    text = path.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (previewState.preview.requiresFullPathConfirmation) {
                            TextField(
                                value = confirmation,
                                onValueChange = { confirmation = it },
                                label = { Text("Type ${previewState.preview.rootPath} to confirm") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDelete(confirmation) },
                enabled = canDelete,
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private data class AclDraft(
    val scheme: String,
    val id: String,
    val permissions: Set<ZNodePermission>,
) {
    fun toAcl(): ZNodeAcl =
        ZNodeAcl(
            scheme = scheme.trim(),
            id = id.trim(),
            permissions = permissions,
        )
}

@Composable
fun AclEditorDialog(
    detail: ZNodeDetail,
    onDismiss: () -> Unit,
    onSave: (List<ZNodeAcl>) -> Unit,
) {
    var entries by remember(detail.path, detail.stat.aversion) {
        mutableStateOf(
            detail.acl.map { acl ->
                AclDraft(
                    scheme = acl.scheme,
                    id = acl.id,
                    permissions = acl.permissions,
                )
            },
        )
    }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    fun updateEntry(index: Int, transform: (AclDraft) -> AclDraft) {
        entries = entries.mapIndexed { currentIndex, entry ->
            if (currentIndex == index) transform(entry) else entry
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ACL") },
        text = {
            Column(
                modifier = Modifier.height(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        entries = entries + AclDraft("world", "anyone", setOf(ZNodePermission.Read))
                    }) {
                        Text("World read")
                    }
                    OutlinedButton(onClick = {
                        entries = entries + AclDraft("digest", "user:password-hash", ZNodePermission.entries.toSet())
                    }) {
                        Text("Digest admin")
                    }
                    OutlinedButton(onClick = {
                        entries =
                            entries + AclDraft("ip", "127.0.0.1", setOf(ZNodePermission.Read, ZNodePermission.Write))
                    }) {
                        Text("IP rw")
                    }
                }
                entries.forEachIndexed { index, entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextField(
                                value = entry.scheme,
                                onValueChange = { value -> updateEntry(index) { it.copy(scheme = value) } },
                                label = { Text("Scheme") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            TextField(
                                value = entry.id,
                                onValueChange = { value -> updateEntry(index) { it.copy(id = value) } },
                                label = { Text("Id") },
                                modifier = Modifier.weight(2f),
                                singleLine = true,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ZNodePermission.entries.forEach { permission ->
                                PermissionToggle(
                                    permission = permission,
                                    checked = permission in entry.permissions,
                                    onCheckedChange = { checked ->
                                        updateEntry(index) {
                                            it.copy(
                                                permissions = if (checked) {
                                                    it.permissions + permission
                                                } else {
                                                    it.permissions - permission
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                            TextButton(onClick = {
                                entries = entries.filterIndexed { currentIndex, _ -> currentIndex != index }
                            }) {
                                Text("Remove")
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    entries = entries + AclDraft("world", "anyone", setOf(ZNodePermission.Read))
                }) {
                    Text("Add ACL")
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
                    val acl = entries.map(AclDraft::toAcl)
                    if (acl.isEmpty() || acl.any { it.scheme.isBlank() || it.id.isBlank() || it.permissions.isEmpty() }) {
                        validationMessage = "ACL entries require scheme, id, and at least one permission."
                    } else {
                        onSave(acl)
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

@Composable
private fun PermissionToggle(
    permission: ZNodePermission,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = permission.name.take(1).lowercase(),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}


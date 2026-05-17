package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.DeletePreviewState
import io.github.realmlabs.yggdrasil.domain.model.*
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*

@Composable
fun CreateNodeDialog(
    selectedPath: ZNodePath?,
    onDismiss: () -> Unit,
    onCreate: (CreateZNodeRequest) -> Unit,
) {
    val strings = Res.string
    val defaultPath = selectedPath?.let { path ->
        if (path == ZNodePath.Root) "/new-node" else "${path.value}/new-node"
    } ?: "/new-node"
    var pathText by remember { mutableStateOf(defaultPath) }
    var dataText by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(ZNodeCreateMode.Persistent) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val invalidPathMessage = stringResource(strings.error_invalid_zk_path, pathText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.dialog_create_znode)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = pathText,
                    onValueChange = { pathText = it },
                    label = { Text(stringResource(strings.common_path)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = dataText,
                    onValueChange = { dataText = it },
                    label = { Text(stringResource(strings.common_data)) },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton(
                            text = stringResource(strings.dialog_mode_persistent),
                            selected = mode == ZNodeCreateMode.Persistent,
                            onClick = { mode = ZNodeCreateMode.Persistent },
                            modifier = Modifier.weight(1f),
                        )
                        ModeButton(
                            text = stringResource(strings.dialog_mode_ephemeral),
                            selected = mode == ZNodeCreateMode.Ephemeral,
                            onClick = { mode = ZNodeCreateMode.Ephemeral },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton(
                            text = stringResource(strings.dialog_mode_persistent_seq),
                            selected = mode == ZNodeCreateMode.PersistentSequential,
                            onClick = { mode = ZNodeCreateMode.PersistentSequential },
                            modifier = Modifier.weight(1f),
                        )
                        ModeButton(
                            text = stringResource(strings.dialog_mode_ephemeral_seq),
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

                        is OperationResult.Failure -> validationMessage = invalidPathMessage
                    }
                },
            ) {
                Text(stringResource(strings.common_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.common_cancel))
            }
        },
    )
}

@Composable
fun DeleteNodeDialog(
    state: AppState,
    onPreview: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = Res.string
    val selectedPath = state.selectedPath
    var recursive by remember(selectedPath) { mutableStateOf(false) }
    val preview = state.deletePreview as? DeletePreviewState.Loaded
    val canDelete = preview?.let {
        it.preview.rootPath == selectedPath && it.preview.recursive == recursive
    } == true

    LaunchedEffect(selectedPath, recursive) {
        if (selectedPath != null) {
            onPreview(recursive)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(strings.dialog_delete_znode)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = selectedPath?.value ?: stringResource(strings.common_no_selected_path),
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
                    Text(stringResource(strings.dialog_recursive_delete))
                }
                when (val previewState = state.deletePreview) {
                    DeletePreviewState.None -> Text(
                        text = stringResource(strings.dialog_delete_preview_loading, selectedPath?.value ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is DeletePreviewState.Loading -> Text(
                        text = stringResource(strings.dialog_delete_preview_loading, previewState.path.value),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    is DeletePreviewState.Failed -> Text(
                        text = previewState.error.localized(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    is DeletePreviewState.Loaded -> {
                        Text(
                            text = stringResource(strings.dialog_delete_preview_count, previewState.preview.paths.size),
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
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDelete,
                enabled = canDelete,
            ) {
                Text(stringResource(strings.common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.common_cancel))
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
    val strings = Res.string
    val aclValidation = stringResource(strings.dialog_acl_validation)
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
        title = { Text(stringResource(strings.dialog_edit_acl)) },
        text = {
            Column(
                modifier = Modifier.height(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        entries = entries + AclDraft("world", "anyone", setOf(ZNodePermission.Read))
                    }) {
                        Text(stringResource(strings.dialog_world_read))
                    }
                    OutlinedButton(onClick = {
                        entries = entries + AclDraft("digest", "user:password-hash", ZNodePermission.entries.toSet())
                    }) {
                        Text(stringResource(strings.dialog_digest_admin))
                    }
                    OutlinedButton(onClick = {
                        entries =
                            entries + AclDraft("ip", "127.0.0.1", setOf(ZNodePermission.Read, ZNodePermission.Write))
                    }) {
                        Text(stringResource(strings.dialog_ip_rw))
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
                                label = { Text(stringResource(strings.dialog_scheme)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            TextField(
                                value = entry.id,
                                onValueChange = { value -> updateEntry(index) { it.copy(id = value) } },
                                label = { Text(stringResource(strings.dialog_id)) },
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
                                Text(stringResource(strings.dialog_remove))
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    entries = entries + AclDraft("world", "anyone", setOf(ZNodePermission.Read))
                }) {
                    Text(stringResource(strings.dialog_add_acl))
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
                        validationMessage = aclValidation
                    } else {
                        onSave(acl)
                    }
                },
            ) {
                Text(stringResource(strings.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(strings.common_cancel))
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

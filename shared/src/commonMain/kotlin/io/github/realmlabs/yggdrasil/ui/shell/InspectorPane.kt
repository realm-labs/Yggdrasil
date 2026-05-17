package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.toString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ConnectionRuntimeStatus
import io.github.realmlabs.yggdrasil.application.state.ZNodeDetailState
import io.github.realmlabs.yggdrasil.domain.model.ZNodeAcl
import io.github.realmlabs.yggdrasil.domain.model.ZNodePermission
import io.github.realmlabs.yggdrasil.domain.model.ZNodeStat
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*

@Composable
fun InspectorPane(
    state: AppState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditAcl: () -> Unit,
    onSetWatchEnabled: (Boolean) -> Unit,
    onClearWatchEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = Res.string
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(if (expanded) 16.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = if (expanded) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        if (!expanded) {
            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.size(ShellMetrics.ControlHeight),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(strings.inspector_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ShellMetrics.ControlHeight)
                .clip(ShellMetrics.TreeRowShape)
                .clickable(onClick = onToggleExpanded),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(strings.inspector_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier.size(ShellMetrics.ControlHeight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(strings.inspector_collapse),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val detail = (state.nodeDetail as? ZNodeDetailState.Loaded)?.detail
            InspectorSection(
                title = stringResource(strings.inspector_stat),
                rows = detail?.stat?.toInspectorRows() ?: emptyStatRows(),
            )
            InspectorSection(
                title = stringResource(strings.inspector_acl),
                rows = detail?.acl?.toInspectorRows() ?: listOf(
                    stringResource(strings.inspector_entries) to "-",
                    stringResource(strings.inspector_mode) to state.modeLabel(),
                ),
                trailing = {
                    OutlinedButton(
                        onClick = onEditAcl,
                        enabled = detail != null && !state.isReadOnly,
                        modifier = Modifier.size(ShellMetrics.CompactControlHeight),
                        shape = ShellMetrics.FieldShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(strings.inspector_edit_acl),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
            InspectorSection(
                title = stringResource(strings.inspector_watch),
                rows = listOf(
                    stringResource(strings.inspector_state) to when {
                        state.watchState.error != null -> stringResource(strings.inspector_state_failed)
                        state.watchState.isRegistered -> stringResource(strings.inspector_state_registered)
                        else -> stringResource(strings.inspector_state_not_registered)
                    },
                    stringResource(strings.common_path) to (state.watchState.watchedPath?.value ?: "-"),
                    stringResource(strings.inspector_last_event) to (state.watchState.lastEvent?.let { "${it.type} ${it.path}" }
                        ?: "-"),
                ),
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = state.watchState.enabled,
                            onCheckedChange = onSetWatchEnabled,
                            enabled = state.selectedPath != null,
                        )
                        TextButton(onClick = onClearWatchEvents, enabled = state.watchState.events.isNotEmpty()) {
                            Text(stringResource(strings.common_clear))
                        }
                    }
                },
            )
            if (state.watchState.events.isNotEmpty()) {
                InspectorSection(
                    title = stringResource(strings.inspector_watch_events),
                    rows = state.watchState.events.take(5).map { event ->
                        event.type.name to event.path.value
                    },
                )
            }
            InspectorSection(
                title = stringResource(strings.inspector_connection),
                rows = listOf(
                    stringResource(strings.inspector_state) to state.connectionStatusLabel(),
                    "SSH" to if (state.activeConnection?.sshTunnel != null) {
                        stringResource(strings.connection_ssh_tunnel)
                    } else {
                        "-"
                    },
                ),
            )
            InspectorSection(
                title = stringResource(strings.inspector_audit),
                rows = state.auditEntries.take(5).ifEmpty {
                    listOf(null)
                }.mapIndexed { index, entry ->
                    if (entry == null) {
                        stringResource(strings.inspector_recent_operations) to "-"
                    } else {
                        val label = if (index == 0) stringResource(strings.inspector_latest) else entry.action.name
                        label to buildString {
                            append(entry.action.name)
                            entry.path?.let { append(" ").append(it) }
                            append(" · ").append(entry.summary)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun InspectorSection(
    title: String,
    rows: List<Pair<String, String>>,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShellMetrics.CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), ShellMetrics.CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            trailing?.invoke()
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (label, value) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = value,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZNodeStat.toInspectorRows(): List<Pair<String, String>> {
    val strings = Res.string
    return listOf(
        stringResource(strings.inspector_data_size) to stringResource(strings.inspector_data_size_bytes, dataLength),
        stringResource(strings.inspector_data_version) to version.toString(),
        "cversion" to cversion.toString(),
        "aversion" to aversion.toString(),
        stringResource(strings.inspector_children) to numChildren.toString(),
        "ctime" to ctimeMillis.toString(),
        "mtime" to mtimeMillis.toString(),
        stringResource(strings.inspector_ephemeral_owner) to ephemeralOwner.toZxidLabel(),
        "czxid" to czxid.toZxidLabel(),
        "mzxid" to mzxid.toZxidLabel(),
        "pzxid" to pzxid.toZxidLabel(),
    )
}

@Composable
private fun emptyStatRows(): List<Pair<String, String>> {
    val strings = Res.string
    return listOf(
        stringResource(strings.inspector_data_size) to "-",
        stringResource(strings.inspector_data_version) to "-",
        "cversion" to "-",
        "aversion" to "-",
        stringResource(strings.inspector_children) to "-",
        "ctime" to "-",
        "mtime" to "-",
        stringResource(strings.inspector_ephemeral_owner) to "-",
        "czxid" to "-",
        "mzxid" to "-",
        "pzxid" to "-",
    )
}

@Composable
private fun List<ZNodeAcl>.toInspectorRows(): List<Pair<String, String>> {
    val strings = Res.string
    return buildList {
        add(stringResource(strings.inspector_entries) to size.toString())
        if (isEmpty()) {
            add(stringResource(strings.inspector_permissions) to "-")
            return@buildList
        }
        this@toInspectorRows.take(4).forEachIndexed { index, acl ->
            add("ACL ${index + 1}" to "${acl.scheme}:${acl.id} ${acl.permissions.toPermissionLabel()}")
        }
        if (size > 4) {
            add(stringResource(strings.inspector_more) to stringResource(strings.inspector_hidden_count, size - 4))
        }
    }
}

private fun Set<ZNodePermission>.toPermissionLabel(): String =
    listOfNotNull(
        "r".takeIf { ZNodePermission.Read in this },
        "w".takeIf { ZNodePermission.Write in this },
        "c".takeIf { ZNodePermission.Create in this },
        "d".takeIf { ZNodePermission.Delete in this },
        "a".takeIf { ZNodePermission.Admin in this },
    ).joinToString("")

private fun Long.toZxidLabel(): String =
    if (this == 0L) "0" else "0x${toString(16)}"

@Composable
private fun AppState.connectionStatusLabel(): String {
    val strings = Res.string
    return when (activeConnectionId?.let { connectionStatuses[it] } ?: ConnectionRuntimeStatus.Disconnected) {
        ConnectionRuntimeStatus.Connected -> stringResource(strings.connection_status_connected)
        ConnectionRuntimeStatus.Connecting -> stringResource(strings.connection_status_connecting)
        ConnectionRuntimeStatus.Disconnected -> stringResource(strings.connection_status_disconnected)
        ConnectionRuntimeStatus.Lost -> stringResource(strings.connection_status_lost)
        ConnectionRuntimeStatus.Suspended -> stringResource(strings.connection_status_suspended)
        is ConnectionRuntimeStatus.Failed -> stringResource(strings.inspector_state_failed)
    }
}

@Composable
private fun AppState.modeLabel(): String {
    val strings = Res.string
    return if (isReadOnly) {
        stringResource(strings.mode_read_only_spaced)
    } else {
        stringResource(strings.mode_read_write_spaced)
    }
}

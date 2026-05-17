package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ConnectionRuntimeStatus
import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.platform.chooseFilePath
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*

@Composable
fun ConnectionPane(
    state: AppState,
    onSelectConnection: (ConnectionId) -> Unit,
    onEditConnection: (ConnectionProfile) -> Unit,
    onDeleteConnection: (ConnectionId) -> Unit,
    onTestConnection: (ConnectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = Res.string
    val defaultGroup = stringResource(strings.connection_default_group)
    Panel(
        title = stringResource(strings.connection_panel_title),
        modifier = modifier,
    ) {
        if (state.isLoadingConnections) {
            EmptyPanelMessage(
                title = stringResource(strings.connection_loading_title),
                body = stringResource(strings.connection_loading_body),
            )
            return@Panel
        }

        if (state.connections.isEmpty()) {
            EmptyPanelMessage(
                title = stringResource(strings.connection_none_saved),
                body = stringResource(strings.connection_empty_body),
            )
            return@Panel
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.connections
                .groupBy { it.groupLabel(defaultGroup) }
                .toSortedMap()
                .forEach { (group, connections) ->
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    connections.forEach { connection ->
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
}

private fun ConnectionProfile.groupLabel(defaultGroup: String): String =
    tags.firstOrNull()?.takeIf { it.isNotBlank() } ?: defaultGroup

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
    val strings = Res.string
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
                text = buildString {
                    append(
                        if (connection.mode == ConnectionMode.ReadWrite) stringResource(strings.mode_read_write_spaced) else stringResource(
                            strings.mode_read_only_spaced
                        )
                    )
                    if (connection.sshTunnel != null) append(stringResource(strings.connection_ssh_tunnel_suffix))
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ConnectionActionButton(
                    label = if (status == ConnectionRuntimeStatus.Connecting) stringResource(strings.connection_testing) else stringResource(
                        strings.connection_test
                    ),
                    icon = ConnectionActionIcon.Test,
                    enabled = status != ConnectionRuntimeStatus.Connecting,
                    onClick = onTest,
                )
                ConnectionActionButton(
                    label = stringResource(strings.connection_edit),
                    icon = ConnectionActionIcon.Edit,
                    onClick = onEdit,
                )
                ConnectionActionButton(
                    label = stringResource(strings.connection_delete),
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
    val strings = Res.string
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
    val strings = Res.string
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var group by remember(profile?.id) { mutableStateOf(profile?.tags?.firstOrNull().orEmpty()) }
    var connectionString by remember(profile?.id) { mutableStateOf(profile?.connectionString.orEmpty()) }
    var chroot by remember(profile?.id) { mutableStateOf(profile?.chroot?.value.orEmpty()) }
    var readWrite by remember(profile?.id) { mutableStateOf(profile?.mode == ConnectionMode.ReadWrite) }
    var zkDigestAuthEnabled by remember(profile?.id) { mutableStateOf(profile?.security is ConnectionSecurity.Digest) }
    var zkDigestUsername by remember(profile?.id) {
        mutableStateOf((profile?.security as? ConnectionSecurity.Digest)?.username.orEmpty())
    }
    var zkDigestCredentialRef by remember(profile?.id) {
        mutableStateOf((profile?.security as? ConnectionSecurity.Digest)?.credentialRef)
    }
    var zkDigestPassword by remember(profile?.id) { mutableStateOf("") }
    var sshTunnelEnabled by remember(profile?.id) { mutableStateOf(profile?.sshTunnel != null) }
    var sshHost by remember(profile?.id) { mutableStateOf(profile?.sshTunnel?.host.orEmpty()) }
    var sshPort by remember(profile?.id) { mutableStateOf(profile?.sshTunnel?.port?.toString() ?: "22") }
    var sshUsername by remember(profile?.id) { mutableStateOf(profile?.sshTunnel?.username.orEmpty()) }
    var sshIdentityFile by remember(profile?.id) { mutableStateOf(profile?.sshTunnel?.identityFile.orEmpty()) }
    var sshAuthenticationMethod by remember(profile?.id) {
        mutableStateOf(profile?.sshTunnel?.authenticationMethod ?: SshAuthenticationMethod.PublicKey)
    }
    var sshCredentialRef by remember(profile?.id) { mutableStateOf(profile?.sshTunnel?.credentialRef) }
    var sshSecret by remember(profile?.id) { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val connectionNameRequired = stringResource(strings.error_connection_name_required)
    val zkConnectionRequired = stringResource(strings.error_zk_connection_required)
    val connectionProfileInvalid = stringResource(strings.error_connection_profile_invalid)
    val sshHostRequired = stringResource(strings.error_ssh_host_required)
    val sshUsernameRequired = stringResource(strings.error_ssh_username_required)
    val sshPortInvalid = stringResource(strings.error_ssh_port_invalid)
    val sshPasswordRequired = stringResource(strings.error_ssh_password_required)
    val sshIdentityFileRequired = stringResource(strings.error_ssh_identity_file_required)
    val zkDigestUsernameRequired = stringResource(strings.error_zk_digest_username_required)
    val zkDigestPasswordRequired = stringResource(strings.error_zk_digest_password_required)
    val identityBrowseLabel = stringResource(strings.connection_identity_browse)
    val identityPickerTitle = stringResource(strings.connection_identity_picker_title)
    fun validationErrorMessage(error: AppError): String =
        when (error.message) {
            "Connection name is required." -> connectionNameRequired
            "ZooKeeper connection string is required." -> zkConnectionRequired
            "Connection profile is not valid." -> connectionProfileInvalid
            "ZooKeeper digest username is required." -> zkDigestUsernameRequired
            "ZooKeeper digest password is required." -> zkDigestPasswordRequired
            "SSH host is required." -> sshHostRequired
            "SSH username is required." -> sshUsernameRequired
            "SSH port must be between 1 and 65535." -> sshPortInvalid
            "SSH password is required." -> sshPasswordRequired
            "SSH identity file is required." -> sshIdentityFileRequired
            else -> error.message
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (profile == null) stringResource(strings.connection_new_title) else stringResource(strings.connection_edit_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(strings.connection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = group,
                    onValueChange = { group = it },
                    label = { Text(stringResource(strings.connection_group)) },
                    placeholder = { Text(stringResource(strings.connection_group_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = connectionString,
                    onValueChange = { connectionString = it },
                    label = { Text(stringResource(strings.connection_string)) },
                    placeholder = { Text("localhost:2181") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = chroot,
                    onValueChange = { chroot = it },
                    label = { Text(stringResource(strings.connection_chroot)) },
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
                        text = stringResource(strings.mode_read_only_spaced),
                        selected = !readWrite,
                        onClick = { readWrite = false },
                        modifier = Modifier.weight(1f),
                    )
                    ModeButton(
                        text = stringResource(strings.mode_read_write_spaced),
                        selected = readWrite,
                        onClick = { readWrite = true },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = zkDigestAuthEnabled,
                        onClick = {
                            val next = !zkDigestAuthEnabled
                            zkDigestAuthEnabled = next
                            if (!next) {
                                zkDigestCredentialRef = null
                                zkDigestPassword = ""
                            }
                        },
                        label = { Text(stringResource(strings.connection_zk_digest_auth)) },
                    )
                    Text(
                        text = stringResource(strings.connection_zk_digest_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (zkDigestAuthEnabled) {
                    TextField(
                        value = zkDigestUsername,
                        onValueChange = {
                            if (it != zkDigestUsername) {
                                zkDigestCredentialRef = null
                            }
                            zkDigestUsername = it
                        },
                        label = { Text(stringResource(strings.connection_zk_digest_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextField(
                        value = zkDigestPassword,
                        onValueChange = { zkDigestPassword = it },
                        label = { Text(stringResource(strings.connection_zk_digest_password)) },
                        placeholder = {
                            Text(
                                if (zkDigestCredentialRef != null) {
                                    stringResource(strings.connection_ssh_keep_saved_secret)
                                } else {
                                    stringResource(strings.connection_zk_digest_password_placeholder)
                                },
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = sshTunnelEnabled,
                        onClick = { sshTunnelEnabled = !sshTunnelEnabled },
                        label = { Text(stringResource(strings.connection_ssh_tunnel)) },
                    )
                    Text(
                        text = stringResource(strings.connection_ssh_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sshTunnelEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = sshHost,
                            onValueChange = { sshHost = it },
                            label = { Text(stringResource(strings.connection_ssh_host)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextField(
                            value = sshPort,
                            onValueChange = { sshPort = it.filter(Char::isDigit) },
                            label = { Text(stringResource(strings.connection_ssh_port)) },
                            singleLine = true,
                            modifier = Modifier.width(96.dp),
                        )
                    }
                    TextField(
                        value = sshUsername,
                        onValueChange = { sshUsername = it },
                        label = { Text(stringResource(strings.connection_ssh_user)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeButton(
                            text = stringResource(strings.connection_ssh_auth_public_key),
                            selected = sshAuthenticationMethod == SshAuthenticationMethod.PublicKey,
                            onClick = {
                                sshAuthenticationMethod = SshAuthenticationMethod.PublicKey
                                sshCredentialRef = null
                                sshSecret = ""
                            },
                            modifier = Modifier.weight(1f),
                        )
                        ModeButton(
                            text = stringResource(strings.connection_ssh_auth_password),
                            selected = sshAuthenticationMethod == SshAuthenticationMethod.Password,
                            onClick = {
                                sshAuthenticationMethod = SshAuthenticationMethod.Password
                                sshCredentialRef = null
                                sshSecret = ""
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (sshAuthenticationMethod == SshAuthenticationMethod.PublicKey) {
                        TextField(
                            value = sshIdentityFile,
                            onValueChange = { sshIdentityFile = it },
                            label = { Text(stringResource(strings.connection_identity_file)) },
                            placeholder = { Text(stringResource(strings.connection_identity_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        chooseFilePath(
                                            title = identityPickerTitle,
                                            currentPath = sshIdentityFile,
                                        )?.let { selectedPath ->
                                            sshIdentityFile = selectedPath
                                        }
                                    },
                                    modifier = Modifier.semantics { contentDescription = identityBrowseLabel },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                        TextField(
                            value = sshSecret,
                            onValueChange = { sshSecret = it },
                            label = { Text(stringResource(strings.connection_ssh_passphrase)) },
                            placeholder = {
                                Text(
                                    if (sshCredentialRef != null) {
                                        stringResource(strings.connection_ssh_keep_saved_secret)
                                    } else {
                                        stringResource(strings.connection_ssh_passphrase_placeholder)
                                    },
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        TextField(
                            value = sshSecret,
                            onValueChange = { sshSecret = it },
                            label = { Text(stringResource(strings.connection_ssh_password)) },
                            placeholder = {
                                Text(
                                    if (sshCredentialRef != null) {
                                        stringResource(strings.connection_ssh_keep_saved_secret)
                                    } else {
                                        stringResource(strings.connection_ssh_password_placeholder)
                                    },
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
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
                    val draft = ConnectionProfileDraft(
                        name = name,
                        connectionString = connectionString,
                        chroot = chroot,
                        group = group,
                        mode = if (readWrite) ConnectionMode.ReadWrite else ConnectionMode.ReadOnly,
                        zkDigestAuthEnabled = zkDigestAuthEnabled,
                        zkDigestUsername = zkDigestUsername,
                        zkDigestCredentialRef = zkDigestCredentialRef,
                        zkDigestPassword = zkDigestPassword,
                        sshTunnelEnabled = sshTunnelEnabled,
                        sshHost = sshHost,
                        sshPort = sshPort,
                        sshUsername = sshUsername,
                        sshIdentityFile = sshIdentityFile,
                        sshAuthenticationMethod = sshAuthenticationMethod,
                        sshCredentialRef = sshCredentialRef,
                        sshSecret = sshSecret,
                    )
                    when (val validation = draft.toProfile(ConnectionId("validation"))) {
                        is OperationResult.Success -> onSave(draft)
                        is OperationResult.Failure -> validationMessage = validationErrorMessage(validation.error)
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

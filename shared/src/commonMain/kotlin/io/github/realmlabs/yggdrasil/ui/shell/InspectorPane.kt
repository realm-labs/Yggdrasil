package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.toString
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ZNodeDetailState
import io.github.realmlabs.yggdrasil.domain.model.ZNodeAcl
import io.github.realmlabs.yggdrasil.domain.model.ZNodePermission
import io.github.realmlabs.yggdrasil.domain.model.ZNodeStat

@Composable
fun InspectorPane(
    state: AppState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditAcl: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                Text("‹", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                text = "Inspector",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Box(
                modifier = Modifier.size(ShellMetrics.ControlHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "›",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val detail = (state.nodeDetail as? ZNodeDetailState.Loaded)?.detail
            InspectorSection(
                title = "Stat",
                rows = detail?.stat?.toInspectorRows() ?: emptyStatRows(),
            )
            InspectorSection(
                title = "ACL",
                rows = detail?.acl?.toInspectorRows() ?: listOf("Entries" to "-", "Mode" to state.modeLabel()),
                trailing = {
                    OutlinedButton(
                        onClick = onEditAcl,
                        enabled = detail != null && !state.isReadOnly,
                        modifier = Modifier.size(ShellMetrics.CompactControlHeight),
                        shape = ShellMetrics.FieldShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("✎")
                    }
                },
            )
            InspectorSection(
                title = "Watch",
                rows = listOf(
                    "State" to when {
                        state.watchState.error != null -> "Failed"
                        state.watchState.isRegistered -> "Registered"
                        else -> "Not registered"
                    },
                    "Path" to (state.watchState.watchedPath?.value ?: "-"),
                    "Last event" to (state.watchState.lastEvent?.let { "${it.type} ${it.path}" } ?: "-"),
                ),
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

private fun ZNodeStat.toInspectorRows(): List<Pair<String, String>> =
    listOf(
        "Data size" to "$dataLength bytes",
        "Data version" to version.toString(),
        "cversion" to cversion.toString(),
        "aversion" to aversion.toString(),
        "Children" to numChildren.toString(),
        "ctime" to ctimeMillis.toString(),
        "mtime" to mtimeMillis.toString(),
        "Ephemeral owner" to ephemeralOwner.toZxidLabel(),
        "czxid" to czxid.toZxidLabel(),
        "mzxid" to mzxid.toZxidLabel(),
        "pzxid" to pzxid.toZxidLabel(),
    )

private fun emptyStatRows(): List<Pair<String, String>> =
    listOf(
        "Data size" to "-",
        "Data version" to "-",
        "cversion" to "-",
        "aversion" to "-",
        "Children" to "-",
        "ctime" to "-",
        "mtime" to "-",
        "Ephemeral owner" to "-",
        "czxid" to "-",
        "mzxid" to "-",
        "pzxid" to "-",
    )

private fun List<ZNodeAcl>.toInspectorRows(): List<Pair<String, String>> =
    buildList {
        add("Entries" to size.toString())
        if (isEmpty()) {
            add("Permissions" to "-")
            return@buildList
        }
        this@toInspectorRows.take(4).forEachIndexed { index, acl ->
            add("ACL ${index + 1}" to "${acl.scheme}:${acl.id} ${acl.permissions.toPermissionLabel()}")
        }
        if (size > 4) {
            add("More" to "${size - 4} hidden")
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

private fun AppState.modeLabel(): String =
    if (isReadOnly) "Read only" else "Read/write"

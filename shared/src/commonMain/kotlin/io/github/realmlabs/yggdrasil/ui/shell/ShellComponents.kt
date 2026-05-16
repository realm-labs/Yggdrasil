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
fun TopBar(
    state: AppState,
    onNewConnection: () -> Unit,
) {
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
        Button(onClick = onNewConnection) {
            Text("New connection")
        }
    }
}

@Composable
fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text)
        }
    }
}

@Composable
fun Panel(
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
fun EmptyPanelMessage(
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
fun StatusBar(state: AppState) {
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
fun StatusDot(status: ConnectionRuntimeStatus) {
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
fun EnvironmentPill(text: String) {
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
fun DividerLine(vertical: Boolean) {
    Box(
        modifier = if (vertical) {
            Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        } else {
            Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        },
    )
}

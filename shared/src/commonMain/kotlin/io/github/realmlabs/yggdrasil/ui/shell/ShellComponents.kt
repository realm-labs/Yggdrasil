package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ConnectionRuntimeStatus
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*

object ShellMetrics {
    val ControlHeight = 40.dp
    val CompactControlHeight = 34.dp
    val TitleBarTopInset = 28.dp
    val FieldShape = RoundedCornerShape(8.dp)
    val CardShape = RoundedCornerShape(8.dp)
    val TreeRowShape = RoundedCornerShape(5.dp)
    val IconButtonSize = 40.dp
}

@Composable
fun ShellTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    monospace: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val textStyle = MaterialTheme.typography.bodySmall.merge(
        TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        ),
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(ShellMetrics.ControlHeight),
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ShellMetrics.FieldShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.55f else 0.28f),
                        shape = ShellMetrics.FieldShape,
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                leading?.invoke()
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun TopBar(
    state: AppState,
    onNewConnection: () -> Unit,
    onCommand: () -> Unit,
) {
    val strings = Res.string
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
        EnvironmentPill(text = state.activeConnection?.name ?: stringResource(strings.connection_none))
        if (state.isReadOnly) {
            EnvironmentPill(text = stringResource(strings.mode_read_only_spaced))
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCommand) {
            Text(stringResource(strings.command_title))
        }
        Button(onClick = onNewConnection) {
            Text(stringResource(strings.connection_new_title))
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
    val strings = Res.string
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.statusMessage.localized(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(strings.common_idle),
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

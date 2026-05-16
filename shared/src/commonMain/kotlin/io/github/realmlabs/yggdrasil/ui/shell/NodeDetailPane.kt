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
fun NodeDetailPane(
    state: AppState,
    onUpdateNodeData: (ByteArray, Int) -> Unit,
    onDeleteNode: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Panel(
        title = "Node data",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDeleteNode,
                    enabled = state.selectedPath != null && !state.isReadOnly,
                ) {
                    Text("Delete")
                }
                OutlinedButton(onClick = onClearSelection) {
                    Text("Clear")
                }
            }
        },
    ) {
        when (val detailState = state.nodeDetail) {
            ZNodeDetailState.None -> EmptyPanelMessage(
                title = "No znode selected",
                body = "Choose a path from the tree to inspect data, stat, and ACL details.",
            )

            is ZNodeDetailState.Loading -> EmptyPanelMessage(
                title = "Loading ${detailState.path}",
                body = "The node detail request is in progress.",
            )

            is ZNodeDetailState.Loaded -> NodeDataViewer(
                detail = detailState.detail,
                readOnly = state.isReadOnly,
                onUpdateNodeData = onUpdateNodeData,
            )
            is ZNodeDetailState.Failed -> EmptyPanelMessage(
                title = "Could not load ${detailState.path}",
                body = detailState.error.message,
            )
        }
    }
}

@Composable
private fun NodeDataViewer(
    detail: ZNodeDetail,
    readOnly: Boolean,
    onUpdateNodeData: (ByteArray, Int) -> Unit,
) {
    var selectedFormat by remember(detail.path, detail.stat.version) {
        mutableStateOf(detail.detectedFormat.toViewFormat())
    }
    var editing by remember(detail.path, detail.stat.version) { mutableStateOf(false) }
    var editText by remember(detail.path, detail.stat.version) { mutableStateOf(detail.data.toTextPreview()) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = detail.path.value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DataFormatButton(
                label = "Text",
                selected = selectedFormat == ZNodeDataFormat.Text,
                onClick = { selectedFormat = ZNodeDataFormat.Text },
            )
            DataFormatButton(
                label = "JSON",
                selected = selectedFormat == ZNodeDataFormat.Json,
                onClick = { selectedFormat = ZNodeDataFormat.Json },
            )
            DataFormatButton(
                label = "Hex",
                selected = selectedFormat == ZNodeDataFormat.Hex,
                onClick = { selectedFormat = ZNodeDataFormat.Hex },
            )
            Spacer(Modifier.weight(1f))
            if (editing) {
                OutlinedButton(onClick = {
                    editing = false
                    editText = detail.data.toTextPreview()
                }) {
                    Text("Cancel")
                }
                Button(onClick = {
                    onUpdateNodeData(editText.encodeToByteArray(), detail.stat.version)
                    editing = false
                }) {
                    Text("Save")
                }
            } else {
                Button(
                    onClick = {
                        selectedFormat = ZNodeDataFormat.Text
                        editText = detail.data.toTextPreview()
                        editing = true
                    },
                    enabled = !readOnly,
                ) {
                    Text("Edit")
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (editing) {
                TextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = detail.renderData(selectedFormat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DataFormatButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}

private val PrettyJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

private fun ZNodeDataFormat.toViewFormat(): ZNodeDataFormat =
    when (this) {
        ZNodeDataFormat.Json -> ZNodeDataFormat.Json
        ZNodeDataFormat.Hex,
        ZNodeDataFormat.Base64,
        ZNodeDataFormat.Unknown -> ZNodeDataFormat.Hex

        ZNodeDataFormat.Text,
        ZNodeDataFormat.Yaml,
        ZNodeDataFormat.Properties -> ZNodeDataFormat.Text
    }

private fun ZNodeDetail.renderData(format: ZNodeDataFormat): String {
    if (data.isEmpty()) return "(empty data)"

    return when (format) {
        ZNodeDataFormat.Json -> renderJsonData()
        ZNodeDataFormat.Hex -> data.toHexPreview()
        else -> data.toTextPreview()
    }
}

private fun ZNodeDetail.renderJsonData(): String {
    val text = data.toTextPreview()
    return try {
        PrettyJson.encodeToString<JsonElement>(PrettyJson.parseToJsonElement(text))
    } catch (_: Exception) {
        "Invalid JSON\n\n$text"
    }
}

private fun ByteArray.toTextPreview(): String {
    val text = decodeToString()
    val preview = text.take(MaxDataPreviewChars)
    val suffix = if (text.length > MaxDataPreviewChars) {
        "\n\n... truncated ${text.length - MaxDataPreviewChars} characters"
    } else {
        ""
    }
    return preview + suffix
}

private fun ByteArray.toHexPreview(): String {
    val bytes = take(MaxHexPreviewBytes)
    val lines = bytes.chunked(16).mapIndexed { index, chunk ->
        val offset = (index * 16).toString(16).padStart(8, '0')
        val hex = chunk.joinToString(" ") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        "$offset  $hex"
    }
    val suffix = if (size > MaxHexPreviewBytes) {
        "\n... truncated ${size - MaxHexPreviewBytes} bytes"
    } else {
        ""
    }
    return lines.joinToString("\n") + suffix
}


private const val MaxDataPreviewChars = 64 * 1024
private const val MaxHexPreviewBytes = 16 * 1024

package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ZNodeDetailState
import io.github.realmlabs.yggdrasil.domain.model.ZNodeDataFormat
import io.github.realmlabs.yggdrasil.domain.model.ZNodeDetail
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
                NodeDataText(detail = detail, format = selectedFormat)
            }
        }
    }
}

@Composable
private fun NodeDataText(
    detail: ZNodeDetail,
    format: ZNodeDataFormat,
) {
    val renderedData = remember(detail.path, detail.stat.version, format) {
        detail.renderData(format)
    }
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    SelectionContainer {
        if (format == ZNodeDataFormat.Json && !renderedData.startsWith(InvalidJsonPrefix)) {
            Text(
                text = highlightJson(
                    json = renderedData,
                    colors = JsonSyntaxColors(
                        punctuation = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        key = MaterialTheme.colorScheme.primary,
                        string = Color(0xFF2E7D32),
                        number = Color(0xFF8E24AA),
                        literal = Color(0xFFC2410C),
                    ),
                ),
                style = textStyle,
            )
        } else {
            Text(
                text = renderedData,
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        "$InvalidJsonPrefix\n\n$text"
    }
}

private data class JsonSyntaxColors(
    val punctuation: Color,
    val key: Color,
    val string: Color,
    val number: Color,
    val literal: Color,
)

private fun highlightJson(
    json: String,
    colors: JsonSyntaxColors,
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var index = 0

    while (index < json.length) {
        val char = json[index]
        when {
            char == '"' -> {
                val end = json.findStringEnd(index)
                val style = if (json.isObjectKey(end + 1)) {
                    SpanStyle(color = colors.key, fontWeight = FontWeight.Medium)
                } else {
                    SpanStyle(color = colors.string)
                }
                builder.withStyle(style) {
                    append(json.substring(index, end + 1))
                }
                index = end + 1
            }

            char == '-' || char.isDigit() -> {
                val end = json.findNumberEnd(index)
                builder.withStyle(SpanStyle(color = colors.number)) {
                    append(json.substring(index, end))
                }
                index = end
            }

            json.startsWithLiteral(index, "true") ||
                    json.startsWithLiteral(index, "false") ||
                    json.startsWithLiteral(index, "null") -> {
                val literal = when {
                    json.startsWithLiteral(index, "true") -> "true"
                    json.startsWithLiteral(index, "false") -> "false"
                    else -> "null"
                }
                builder.withStyle(SpanStyle(color = colors.literal, fontWeight = FontWeight.Medium)) {
                    append(literal)
                }
                index += literal.length
            }

            char in "{}[]:," -> {
                builder.withStyle(SpanStyle(color = colors.punctuation)) {
                    append(char)
                }
                index += 1
            }

            else -> {
                builder.append(char)
                index += 1
            }
        }
    }

    return builder.toAnnotatedString()
}

private fun String.findStringEnd(start: Int): Int {
    var index = start + 1
    var escaping = false
    while (index < length) {
        val char = this[index]
        when {
            escaping -> escaping = false
            char == '\\' -> escaping = true
            char == '"' -> return index
        }
        index += 1
    }
    return lastIndex
}

private fun String.isObjectKey(start: Int): Boolean {
    var index = start
    while (index < length && this[index].isWhitespace()) {
        index += 1
    }
    return index < length && this[index] == ':'
}

private fun String.findNumberEnd(start: Int): Int {
    var index = start + 1
    while (index < length && this[index] in "0123456789.eE+-") {
        index += 1
    }
    return index
}

private fun String.startsWithLiteral(
    index: Int,
    literal: String,
): Boolean {
    if (!startsWith(literal, index)) return false
    val end = index + literal.length
    return end == length || !this[end].isLetterOrDigit()
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
private const val InvalidJsonPrefix = "Invalid JSON"

package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppState
import io.github.realmlabs.yggdrasil.application.state.ZNodeDetailState
import io.github.realmlabs.yggdrasil.application.workflow.parseEditedZNodeData
import io.github.realmlabs.yggdrasil.application.workflow.renderEditableZNodeData
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.model.ZNodeDataFormat
import io.github.realmlabs.yggdrasil.domain.model.ZNodeDetail
import io.github.realmlabs.yggdrasil.platform.copyPlainTextToClipboard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*

@Composable
fun NodeDetailPane(
    state: AppState,
    onCreateNode: () -> Unit,
    onUpdateNodeData: (ByteArray, Int) -> Unit,
    onDeleteNode: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = Res.string
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        when (val detailState = state.nodeDetail) {
            ZNodeDetailState.None -> EmptyPanelMessage(
                title = stringResource(strings.node_no_selected_title),
                body = stringResource(strings.node_no_selected_body),
            )

            is ZNodeDetailState.Loading -> EmptyPanelMessage(
                title = stringResource(strings.node_loading_title, detailState.path.value),
                body = stringResource(strings.node_loading_body),
            )

            is ZNodeDetailState.Loaded -> NodeDataViewer(
                detail = detailState.detail,
                readOnly = state.isReadOnly,
                onCreateNode = onCreateNode,
                onDeleteNode = onDeleteNode,
                onClearSelection = onClearSelection,
                onUpdateNodeData = onUpdateNodeData,
            )

            is ZNodeDetailState.Failed -> EmptyPanelMessage(
                title = stringResource(strings.node_load_failed_title, detailState.path.value),
                body = detailState.error.localized(),
            )
        }
    }
}

@Composable
private fun NodeDataViewer(
    detail: ZNodeDetail,
    readOnly: Boolean,
    onCreateNode: () -> Unit,
    onDeleteNode: () -> Unit,
    onClearSelection: () -> Unit,
    onUpdateNodeData: (ByteArray, Int) -> Unit,
) {
    val strings = Res.string
    val dataVerticalScrollState = rememberScrollState()
    val codeTextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    var selectedFormat by remember(detail.path, detail.stat.version) {
        mutableStateOf(detail.detectedFormat.toViewFormat())
    }
    var editing by remember(detail.path, detail.stat.version) { mutableStateOf(false) }
    var editText by remember(detail.path, detail.stat.version) {
        mutableStateOf(renderEditableZNodeData(detail.data, selectedFormat))
    }
    val emptyData = stringResource(strings.node_empty_data)
    val invalidJson = stringResource(strings.node_invalid_json)
    val invalidHex = stringResource(strings.node_invalid_hex)
    val invalidBase64 = stringResource(strings.node_invalid_base64)
    val truncatedCharsPrefix = stringResource(strings.node_truncated_chars_prefix)
    val truncatedCharsSuffix = stringResource(strings.node_truncated_chars_suffix)
    val truncatedBytesPrefix = stringResource(strings.node_truncated_bytes_prefix)
    val truncatedBytesSuffix = stringResource(strings.node_truncated_bytes_suffix)
    val editTooLarge = stringResource(strings.node_edit_too_large, MaxEditableDataBytes / 1024)
    val changeSummaryPrefix = stringResource(strings.node_change_summary)
    val renderedData = remember(
        detail.path,
        detail.stat.version,
        selectedFormat,
        emptyData,
        invalidJson,
        truncatedCharsPrefix,
        truncatedCharsSuffix,
        truncatedBytesPrefix,
        truncatedBytesSuffix,
    ) {
        detail.renderData(
            selectedFormat,
            emptyData,
            invalidJson,
            truncatedCharsPrefix,
            truncatedCharsSuffix,
            truncatedBytesPrefix,
            truncatedBytesSuffix,
        )
    }
    val displayedData = if (editing) editText else renderedData
    val editParseResult = remember(selectedFormat, editing, editText) {
        if (editing) parseEditedZNodeData(editText, selectedFormat) else null
    }
    val formatValidation = remember(selectedFormat, editing, editText, detail.path, detail.stat.version) {
        when {
            selectedFormat == ZNodeDataFormat.Json -> {
                val jsonText = if (editing) editText else detail.data.decodeToString()
                if (jsonText.isValidJsonDocument()) FormatValidation.ValidJson else FormatValidation.InvalidJson
            }

            editing && selectedFormat == ZNodeDataFormat.Hex -> {
                if (editParseResult is OperationResult.Success) FormatValidation.ValidHex else FormatValidation.InvalidHex
            }

            editing && selectedFormat == ZNodeDataFormat.Base64 -> {
                if (editParseResult is OperationResult.Success) FormatValidation.ValidBase64 else FormatValidation.InvalidBase64
            }

            else -> null
        }
    }
    val canSaveEditedData = !editing || editParseResult is OperationResult.Success
    val editDisabledForSize = detail.data.size > MaxEditableDataBytes
    val changeSummary = when (val parsed = editParseResult) {
        is OperationResult.Success -> summarizeDataChange(detail.data, parsed.value)
        else -> null
    }
    val invalidEditMessage = when (editParseResult) {
        is OperationResult.Failure -> when (editParseResult.error.message) {
            "JSON data is invalid." -> invalidJson
            "Hex data is invalid." -> invalidHex
            "Base64 data is invalid." -> invalidBase64
            else -> editParseResult.error.message
        }

        else -> null
    }

    fun startEditing() {
        editText = renderEditableZNodeData(detail.data, selectedFormat)
        editing = true
    }

    fun selectFormat(format: ZNodeDataFormat) {
        selectedFormat = format
        if (editing) {
            editText = renderEditableZNodeData(detail.data, format)
        }
    }

    fun resetEditing() {
        editing = false
        editText = renderEditableZNodeData(detail.data, selectedFormat)
    }

    fun saveEditedData() {
        when (val parsed = editParseResult) {
            is OperationResult.Success -> {
                onUpdateNodeData(parsed.value, detail.stat.version)
            }

            null,
            is OperationResult.Failure -> Unit
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.path.value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NodeActionButton(
                label = stringResource(strings.tree_create_znode),
                icon = Icons.Outlined.Add,
                enabled = !readOnly && !editing,
                onClick = onCreateNode,
            )
            NodeActionButton(
                label = stringResource(strings.node_copy_data),
                icon = Icons.Outlined.ContentCopy,
                enabled = true,
                onClick = {
                    copyPlainTextToClipboard(
                        if (editing) {
                            editText
                        } else {
                            renderedData
                        },
                    )
                },
            )
            NodeActionButton(
                label = stringResource(strings.common_edit),
                icon = Icons.Outlined.Edit,
                enabled = !readOnly && !editing && !editDisabledForSize,
                onClick = ::startEditing,
            )
            NodeActionButton(
                label = stringResource(strings.common_delete),
                icon = Icons.Outlined.Delete,
                enabled = !readOnly && !editing,
                destructive = true,
                onClick = onDeleteNode,
            )
            TextButton(onClick = onClearSelection) { Text(stringResource(strings.common_clear)) }
        }
        DataFormatSegmentedControl(
            selectedFormat = selectedFormat,
            onSelectFormat = ::selectFormat,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(ShellMetrics.CardShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), ShellMetrics.CardShape)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(dataVerticalScrollState),
            ) {
                if (editing) {
                    Box(Modifier.fillMaxWidth().padding(12.dp)) {
                        TextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
                            textStyle = codeTextStyle,
                        )
                    }
                } else {
                    NodeDataLines(
                        renderedData = displayedData,
                        format = selectedFormat,
                        invalidJsonPrefix = invalidJson,
                        textStyle = codeTextStyle,
                    )
                }
            }
            DividerLine(vertical = false)
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${detail.data.size.toDisplaySize()}   ${selectedFormat.editorEncodingLabel()}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = invalidEditMessage ?: when (formatValidation) {
                        FormatValidation.ValidJson -> stringResource(strings.node_valid_json)
                        FormatValidation.InvalidJson -> stringResource(strings.node_invalid_json)
                        FormatValidation.ValidHex -> stringResource(strings.node_valid_hex)
                        FormatValidation.InvalidHex -> stringResource(strings.node_invalid_hex)
                        FormatValidation.ValidBase64 -> stringResource(strings.node_valid_base64)
                        FormatValidation.InvalidBase64 -> stringResource(strings.node_invalid_base64)
                        null -> ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (formatValidation?.invalid == true || invalidEditMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                if (editing && changeSummary != null) {
                    Text(
                        text = "$changeSummaryPrefix $changeSummary",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!editing && editDisabledForSize) {
                    Text(
                        text = editTooLarge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (editing) {
                    Button(
                        onClick = ::saveEditedData,
                        enabled = canSaveEditedData,
                        modifier = Modifier.height(ShellMetrics.ControlHeight),
                        shape = ShellMetrics.FieldShape,
                    ) { Text(stringResource(strings.common_save)) }
                    OutlinedButton(
                        onClick = ::resetEditing,
                        modifier = Modifier.height(ShellMetrics.ControlHeight),
                        shape = ShellMetrics.FieldShape,
                    ) { Text(stringResource(strings.common_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun NodeDataLines(
    renderedData: String,
    format: ZNodeDataFormat,
    invalidJsonPrefix: String,
    textStyle: TextStyle,
) {
    SelectionContainer {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            renderedData.lines().forEachIndexed { index, line ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                ) {
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(end = 12.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = textStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                    NodeDataText(
                        renderedData = line.ifEmpty { " " },
                        format = format,
                        invalidJsonPrefix = invalidJsonPrefix,
                        textStyle = textStyle,
                        modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

private enum class FormatValidation(val invalid: Boolean) {
    ValidJson(invalid = false),
    InvalidJson(invalid = true),
    ValidHex(invalid = false),
    InvalidHex(invalid = true),
    ValidBase64(invalid = false),
    InvalidBase64(invalid = true),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(ShellMetrics.ControlHeight),
            shape = ShellMetrics.FieldShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                    destructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun String.isValidJsonDocument(): Boolean =
    try {
        PrettyJson.parseToJsonElement(this)
        true
    } catch (_: Exception) {
        false
    }

@Composable
private fun NodeDataText(
    renderedData: String,
    format: ZNodeDataFormat,
    invalidJsonPrefix: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (format == ZNodeDataFormat.Json && !renderedData.startsWith(invalidJsonPrefix)) {
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
            modifier = modifier,
            style = textStyle,
        )
    } else {
        Text(
            text = renderedData,
            modifier = modifier,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DataFormatSegmentedControl(
    selectedFormat: ZNodeDataFormat,
    onSelectFormat: (ZNodeDataFormat) -> Unit,
) {
    val strings = Res.string
    val items = listOf(
        ZNodeDataFormat.Text to stringResource(strings.node_format_text),
        ZNodeDataFormat.Json to stringResource(strings.node_format_json),
        ZNodeDataFormat.Hex to stringResource(strings.node_format_hex),
        ZNodeDataFormat.Base64 to stringResource(strings.node_format_base64),
    )
    Row(
        modifier = Modifier
            .height(ShellMetrics.ControlHeight)
            .clip(ShellMetrics.FieldShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), ShellMetrics.FieldShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, (format, label) ->
            val selected = selectedFormat == format
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(82.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelectFormat(format) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index < items.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                )
            }
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
        ZNodeDataFormat.Unknown -> ZNodeDataFormat.Hex

        ZNodeDataFormat.Base64 -> ZNodeDataFormat.Base64
        ZNodeDataFormat.Text,
        ZNodeDataFormat.Yaml,
        ZNodeDataFormat.Properties -> ZNodeDataFormat.Text
    }

private fun ZNodeDataFormat.editorEncodingLabel(): String =
    when (this) {
        ZNodeDataFormat.Hex -> "HEX"
        ZNodeDataFormat.Base64 -> "Base64"
        else -> "UTF-8"
    }

private fun ZNodeDetail.renderData(
    format: ZNodeDataFormat,
    emptyData: String,
    invalidJson: String,
    truncatedCharsPrefix: String,
    truncatedCharsSuffix: String,
    truncatedBytesPrefix: String,
    truncatedBytesSuffix: String,
): String {
    if (data.isEmpty()) return emptyData

    return when (format) {
        ZNodeDataFormat.Json -> renderJsonData(invalidJson, truncatedCharsPrefix, truncatedCharsSuffix)
        ZNodeDataFormat.Hex -> data.toHexPreview(truncatedBytesPrefix, truncatedBytesSuffix)
        ZNodeDataFormat.Base64 -> renderEditableZNodeData(data, ZNodeDataFormat.Base64)
        else -> data.toTextPreview(truncatedCharsPrefix, truncatedCharsSuffix)
    }
}

private fun ZNodeDetail.renderJsonData(
    invalidJson: String,
    truncatedCharsPrefix: String,
    truncatedCharsSuffix: String,
): String {
    val text = data.toTextPreview(truncatedCharsPrefix, truncatedCharsSuffix)
    return try {
        PrettyJson.encodeToString<JsonElement>(PrettyJson.parseToJsonElement(text))
    } catch (_: Exception) {
        "$invalidJson\n\n$text"
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

private fun ByteArray.toTextPreview(
    truncatedPrefix: String = "... truncated ",
    truncatedSuffix: String = " characters",
): String {
    val text = decodeToString()
    val preview = text.take(MaxDataPreviewChars)
    val suffix = if (text.length > MaxDataPreviewChars) {
        "\n\n$truncatedPrefix${text.length - MaxDataPreviewChars}$truncatedSuffix"
    } else {
        ""
    }
    return preview + suffix
}

private fun ByteArray.toHexPreview(
    truncatedPrefix: String = "... truncated ",
    truncatedSuffix: String = " bytes",
): String {
    val bytes = take(MaxHexPreviewBytes)
    val lines = bytes.chunked(16).mapIndexed { index, chunk ->
        val offset = (index * 16).toString(16).padStart(8, '0')
        val hex = chunk.joinToString(" ") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        "$offset  $hex"
    }
    val suffix = if (size > MaxHexPreviewBytes) {
        "\n$truncatedPrefix${size - MaxHexPreviewBytes}$truncatedSuffix"
    } else {
        ""
    }
    return lines.joinToString("\n") + suffix
}

private fun summarizeDataChange(
    before: ByteArray,
    after: ByteArray,
): String {
    val beforeLines = before.decodeToString().lines()
    val afterLines = after.decodeToString().lines()
    val changedLines = maxOf(beforeLines.size, afterLines.size).let { count ->
        (0 until count).count { index -> beforeLines.getOrNull(index) != afterLines.getOrNull(index) }
    }
    val byteDelta = after.size - before.size
    val byteLabel = when {
        byteDelta > 0 -> "+$byteDelta B"
        byteDelta < 0 -> "$byteDelta B"
        else -> "0 B"
    }
    return "$changedLines line(s), $byteLabel"
}

private fun Int.toDisplaySize(): String =
    if (this >= 1024) "${this / 1024}.${((this % 1024) * 10 / 1024)} KB" else "$this B"

private const val MaxDataPreviewChars = 64 * 1024
private const val MaxHexPreviewBytes = 16 * 1024
private const val MaxEditableDataBytes = 1024 * 1024

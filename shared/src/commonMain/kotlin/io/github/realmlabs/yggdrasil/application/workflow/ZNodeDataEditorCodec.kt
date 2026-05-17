package io.github.realmlabs.yggdrasil.application.workflow

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.model.ZNodeDataFormat
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun renderEditableZNodeData(
    data: ByteArray,
    format: ZNodeDataFormat,
): String =
    when (format) {
        ZNodeDataFormat.Hex -> data.toEditableHex()
        ZNodeDataFormat.Base64 -> encodeBase64(data)
        else -> data.decodeToString()
    }

fun parseEditedZNodeData(
    text: String,
    format: ZNodeDataFormat,
): OperationResult<ByteArray> =
    when (format) {
        ZNodeDataFormat.Json -> parseJsonData(text)
        ZNodeDataFormat.Hex -> parseHexData(text)
        ZNodeDataFormat.Base64 -> parseBase64Data(text)
        else -> OperationResult.Success(text.encodeToByteArray())
    }

private fun parseJsonData(text: String): OperationResult<ByteArray> =
    try {
        Json.parseToJsonElement(text)
        OperationResult.Success(text.encodeToByteArray())
    } catch (exception: Exception) {
        OperationResult.Failure(AppError.Validation("JSON data is invalid.", exception.message))
    }

private fun parseHexData(text: String): OperationResult<ByteArray> {
    val compact = text.filterNot(Char::isWhitespace)
    if (compact.isEmpty()) return OperationResult.Success(byteArrayOf())
    if (compact.length % 2 != 0 || compact.any { it.digitToIntOrNull(16) == null }) {
        return OperationResult.Failure(AppError.Validation("Hex data is invalid."))
    }

    val bytes = ByteArray(compact.length / 2)
    compact.chunked(2).forEachIndexed { index, pair ->
        bytes[index] = pair.toInt(16).toByte()
    }
    return OperationResult.Success(bytes)
}

@OptIn(ExperimentalEncodingApi::class)
private fun parseBase64Data(text: String): OperationResult<ByteArray> =
    try {
        OperationResult.Success(Base64.Default.decode(text.filterNot(Char::isWhitespace)))
    } catch (exception: Exception) {
        OperationResult.Failure(AppError.Validation("Base64 data is invalid.", exception.message))
    }

private fun ByteArray.toEditableHex(): String =
    asIterable().chunked(16).joinToString("\n") { chunk ->
        chunk.joinToString(" ") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

@OptIn(ExperimentalEncodingApi::class)
private fun encodeBase64(data: ByteArray): String =
    Base64.Default.encode(data)

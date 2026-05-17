package io.github.realmlabs.yggdrasil.application.workflow

import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.model.ZNodeDataFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ZNodeDataEditorCodecTest {
    @Test
    fun parsesTextAsUtf8() {
        val result = parseEditedZNodeData("hello", ZNodeDataFormat.Text)

        assertContentEquals("hello".encodeToByteArray(), assertSuccess(result))
    }

    @Test
    fun parsesValidJsonAsUtf8() {
        val result = parseEditedZNodeData("""{"enabled":true}""", ZNodeDataFormat.Json)

        assertContentEquals("""{"enabled":true}""".encodeToByteArray(), assertSuccess(result))
    }

    @Test
    fun rejectsInvalidJson() {
        val result = parseEditedZNodeData("not json", ZNodeDataFormat.Json)

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("JSON data is invalid.", failure.error.message)
    }

    @Test
    fun rendersAndParsesHexBytes() {
        val bytes = byteArrayOf(0x00, 0x0f, 0x10, 0xff.toByte())

        val rendered = renderEditableZNodeData(bytes, ZNodeDataFormat.Hex)
        val parsed = parseEditedZNodeData(rendered, ZNodeDataFormat.Hex)

        assertEquals("00 0f 10 ff", rendered)
        assertContentEquals(bytes, assertSuccess(parsed))
    }

    @Test
    fun rejectsInvalidHex() {
        val result = parseEditedZNodeData("0f z1", ZNodeDataFormat.Hex)

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("Hex data is invalid.", failure.error.message)
    }

    @Test
    fun rendersAndParsesBase64Bytes() {
        val bytes = "ready".encodeToByteArray()

        val rendered = renderEditableZNodeData(bytes, ZNodeDataFormat.Base64)
        val parsed = parseEditedZNodeData(rendered, ZNodeDataFormat.Base64)

        assertEquals("cmVhZHk=", rendered)
        assertContentEquals(bytes, assertSuccess(parsed))
    }

    @Test
    fun rejectsInvalidBase64() {
        val result = parseEditedZNodeData("not base64!", ZNodeDataFormat.Base64)

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("Base64 data is invalid.", failure.error.message)
    }

    private fun assertSuccess(result: OperationResult<ByteArray>): ByteArray =
        assertIs<OperationResult.Success<ByteArray>>(result).value
}

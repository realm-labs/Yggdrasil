package io.github.realmlabs.yggdrasil.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZNodePathTest {
    @Test
    fun acceptsRootPath() {
        val result = ZNodePath.parse("/")

        assertIs<OperationResult.Success<ZNodePath>>(result)
        assertEquals("/", result.value.value)
        assertNull(result.value.parent)
    }

    @Test
    fun acceptsNestedAbsolutePath() {
        val path = ZNodePath.requireValid("/services/api")

        assertEquals("api", path.name)
        assertEquals("/services", path.parent?.value)
        assertEquals("/services/api/config", path.child("config").value)
    }

    @Test
    fun rejectsInvalidPaths() {
        assertFalse(ZNodePath.isValid(""))
        assertFalse(ZNodePath.isValid("services/api"))
        assertFalse(ZNodePath.isValid("/services/api/"))
        assertFalse(ZNodePath.isValid("/services//api"))
        assertFalse(ZNodePath.isValid("/services/./api"))
        assertFalse(ZNodePath.isValid("/services/../api"))
    }

    @Test
    fun parseReturnsValidationFailureForInvalidPath() {
        val result = ZNodePath.parse("relative")

        assertIs<OperationResult.Failure>(result)
        assertTrue(result.error.message.contains("Invalid ZooKeeper path"))
    }
}

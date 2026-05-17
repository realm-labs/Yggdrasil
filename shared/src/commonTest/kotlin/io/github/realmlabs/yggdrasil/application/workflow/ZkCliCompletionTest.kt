package io.github.realmlabs.yggdrasil.application.workflow

import kotlin.test.Test
import kotlin.test.assertEquals

class ZkCliCompletionTest {
    @Test
    fun completesCommandName() {
        assertEquals("stat ", completeZkCliCommandLine("sta", emptyList()))
    }

    @Test
    fun movesCursorAfterCompletedText() {
        val completion = completeZkCliCommandLine(
            commandLine = "ge",
            cursor = 2,
            knownPaths = emptyList(),
        )

        assertEquals("get", completion.commandLine)
        assertEquals(3, completion.cursor)
    }

    @Test
    fun completesPathArgument() {
        val completed = completeZkCliCommandLine(
            commandLine = "get /ap",
            knownPaths = listOf("/", "/app", "/config"),
        )

        assertEquals("get /app", completed)
    }

    @Test
    fun completesOnlySharedPrefixForMultipleMatches() {
        val completed = completeZkCliCommandLine(
            commandLine = "ls /a",
            knownPaths = listOf("/app", "/api"),
        )

        assertEquals("ls /ap", completed)
    }

    @Test
    fun leavesUnknownPrefixUnchanged() {
        assertEquals("get /missing", completeZkCliCommandLine("get /missing", listOf("/app")))
    }

    @Test
    fun completesImplementedFlagsOnly() {
        assertEquals("ls -R", completeZkCliCommandLine("ls -R", emptyList()))
        assertEquals("ls -w", completeZkCliCommandLine("ls -w", emptyList()))
    }
}

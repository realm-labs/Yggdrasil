package io.github.realmlabs.yggdrasil.application.workflow

private val ZkCliCommands = listOf(
    "help",
    "ls",
    "ls2",
    "get",
    "stat",
    "getAcl",
    "create",
    "set",
    "delete",
    "rmr",
    "deleteall",
    "setAcl",
)

private val ZkCliCommandFlags = mapOf(
    "ls" to listOf("-s", "-R"),
    "get" to listOf("-s"),
    "getAcl" to listOf("-s"),
    "create" to listOf("-e", "-s"),
    "set" to listOf("-s", "-v"),
    "delete" to listOf("-v"),
    "setAcl" to listOf("-s", "-v", "-R"),
)

data class ZkCliCompletion(
    val commandLine: String,
    val cursor: Int,
)

fun completeZkCliCommandLine(
    commandLine: String,
    knownPaths: Collection<String>,
): String = completeZkCliCommandLine(commandLine, commandLine.length, knownPaths).commandLine

fun completeZkCliCommandLine(
    commandLine: String,
    cursor: Int,
    knownPaths: Collection<String>,
): ZkCliCompletion {
    val safeCursor = cursor.coerceIn(0, commandLine.length)
    val tokenStart = commandLine.substring(0, safeCursor).indexOfLast { it.isWhitespace() } + 1
    val tokenEnd = commandLine.indexOfFirstWhitespace(startIndex = safeCursor).takeIf { it >= 0 } ?: commandLine.length
    val prefix = commandLine.substring(tokenStart, safeCursor)
    val head = commandLine.substring(0, tokenStart)
    val candidates = when {
        head.isBlank() -> ZkCliCommands
        prefix.startsWith("/") -> knownPaths
        prefix.startsWith("-") -> ZkCliCommandFlags[commandLine.firstToken()].orEmpty()
        else -> ZkCliCommands
    }
    val matches = candidates
        .filter { it.startsWith(prefix) }
        .sorted()
    if (matches.isEmpty()) return ZkCliCompletion(commandLine, safeCursor)

    val completion = if (matches.size == 1) {
        matches.single()
    } else {
        matches.commonPrefix().takeIf { it.length > prefix.length } ?: return ZkCliCompletion(commandLine, safeCursor)
    }
    val separator = if (head.isBlank() && matches.size == 1 && commandLine.getOrNull(tokenEnd)?.isWhitespace() != true) " " else ""
    val completedHead = head + completion + separator
    return ZkCliCompletion(
        commandLine = completedHead + commandLine.substring(tokenEnd),
        cursor = completedHead.length,
    )
}

private fun String.indexOfFirstWhitespace(startIndex: Int): Int {
    for (index in startIndex until length) {
        if (this[index].isWhitespace()) return index
    }
    return -1
}

private fun String.firstToken(): String =
    trimStart().takeWhile { !it.isWhitespace() }

private fun List<String>.commonPrefix(): String {
    if (isEmpty()) return ""
    var prefix = first()
    drop(1).forEach { value ->
        while (!value.startsWith(prefix) && prefix.isNotEmpty()) {
            prefix = prefix.dropLast(1)
        }
    }
    return prefix
}

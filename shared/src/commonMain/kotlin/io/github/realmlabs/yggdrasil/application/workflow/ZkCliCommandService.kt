package io.github.realmlabs.yggdrasil.application.workflow

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository

class ZkCliCommandService(
    private val repository: ZNodeRepository,
) {
    suspend fun execute(
        profile: ConnectionProfile,
        request: ZkCliCommandRequest,
    ): OperationResult<ZkCliCommandResult> {
        val tokens = request.commandLine.tokenizeCommandLine()
        if (tokens.isEmpty()) {
            return OperationResult.Failure(AppError.Validation("Command cannot be empty."))
        }

        val command = tokens.first()
        val output = when (command) {
            "help" -> OperationResult.Success(helpText())
            "ls" -> ls(profile, tokens)
            "ls2" -> ls2(profile, tokens)
            "get" -> get(profile, tokens)
            "stat" -> stat(profile, tokens)
            "getAcl" -> getAcl(profile, tokens)
            "create" -> create(profile, tokens)
            "set" -> set(profile, tokens)
            "delete" -> delete(profile, tokens, recursive = false)
            "rmr", "deleteall" -> delete(profile, tokens, recursive = true)
            "setAcl" -> setAcl(profile, tokens)
            else -> OperationResult.Failure(
                AppError.Validation("Unsupported zk command: $command. Type help for supported commands."),
            )
        }

        return when (output) {
            is OperationResult.Success -> OperationResult.Success(
                ZkCliCommandResult(
                    commandLine = request.commandLine,
                    output = output.value,
                ),
            )

            is OperationResult.Failure -> output
        }
    }

    private suspend fun ls(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val parsed = parseLsArguments(tokens)
            ?: return OperationResult.Failure(AppError.Validation("Usage: ls [-s] [-R] [path]"))
        val path = parsePath(parsed.path) ?: return invalidPath(parsed.path)
        if (parsed.recursive) {
            val paths = when (val result = listRecursive(profile, path)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> return result
            }
            return OperationResult.Success(
                buildString {
                    append(paths.joinToString("\n") { it.value })
                    if (parsed.showStat) {
                        val detail = when (val result = repository.loadDetail(profile, path)) {
                            is OperationResult.Success -> result.value
                            is OperationResult.Failure -> return result
                        }
                        appendLine()
                        append(detail.stat.render())
                    }
                },
            )
        }
        return when (val children = repository.loadChildren(profile, path)) {
            is OperationResult.Success -> {
                val childList = children.value.joinToString(prefix = "[", postfix = "]") { it.path.name }
                if (!parsed.showStat) {
                    OperationResult.Success(childList)
                } else {
                    when (val detail = repository.loadDetail(profile, path)) {
                        is OperationResult.Success -> OperationResult.Success(
                            buildString {
                                appendLine(childList)
                                append(detail.value.stat.render())
                            }.trimEnd(),
                        )

                        is OperationResult.Failure -> detail
                    }
                }
            }

            is OperationResult.Failure -> children
        }
    }

    private suspend fun ls2(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val path = tokens.getOrNull(1)
        if (tokens.size > 2 || path?.startsWith("-") == true) {
            return OperationResult.Failure(AppError.Validation("Usage: ls2 [path]"))
        }
        val normalizedTokens = listOfNotNull("ls", "-s", path)
        return ls(profile, normalizedTokens)
    }

    private suspend fun get(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val parsed = parsePathAndStatFlag(tokens)
            ?: return OperationResult.Failure(AppError.Validation("Usage: get [-s] <path>"))
        val path = parsePath(parsed.path) ?: return invalidPath(parsed.path)
        return when (val detail = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> OperationResult.Success(renderData(detail.value, parsed.showStat))

            is OperationResult.Failure -> detail
        }
    }

    private suspend fun stat(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        if (tokens.size != 2 || tokens[1].startsWith("-")) {
            return OperationResult.Failure(AppError.Validation("Usage: stat <path>"))
        }
        val path = parsePath(tokens.getOrNull(1)) ?: return invalidPath(tokens.getOrNull(1))
        return when (val detail = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> OperationResult.Success(detail.value.stat.render())
            is OperationResult.Failure -> detail
        }
    }

    private suspend fun getAcl(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val parsed = parsePathAndStatFlag(tokens)
            ?: return OperationResult.Failure(AppError.Validation("Usage: getAcl [-s] <path>"))
        val path = parsePath(parsed.path) ?: return invalidPath(parsed.path)
        return when (val detail = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> OperationResult.Success(renderAcl(detail.value, parsed.showStat))

            is OperationResult.Failure -> detail
        }
    }

    private suspend fun create(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val writable = requireWritable(profile) ?: return writableFailure()
        val parsed = parseCreateArguments(tokens)
            ?: return OperationResult.Failure(AppError.Validation("Usage: create [-e] [-s] <path> [data]"))
        val path = parsePath(parsed.path) ?: return invalidPath(parsed.path)
        val data = parsed.data.encodeToByteArray()
        return when (val result = repository.createNode(
            writable,
            CreateZNodeRequest(path = path, data = data, mode = parsed.mode),
        )) {
            is OperationResult.Success -> OperationResult.Success("Created ${result.value}")
            is OperationResult.Failure -> result
        }
    }

    private suspend fun set(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val writable = requireWritable(profile) ?: return writableFailure()
        val parsed = parseSetArguments(tokens)
            ?: return OperationResult.Failure(AppError.Validation("Usage: set [-s] [-v version] <path> <data>"))
        val path = parsePath(parsed.path) ?: return invalidPath(parsed.path)
        val data = parsed.data.encodeToByteArray()
        val expectedVersion = parsed.version ?: when (val result = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> result.value.stat.version
            is OperationResult.Failure -> return result
        }
        return when (val result = repository.updateData(
            writable,
            UpdateZNodeDataRequest(path = path, data = data, expectedVersion = expectedVersion),
        )) {
            is OperationResult.Success -> OperationResult.Success(
                if (parsed.showStat) result.value.stat.render() else "Set ${result.value.path} version ${result.value.stat.version}",
            )

            is OperationResult.Failure -> result
        }
    }

    private suspend fun delete(
        profile: ConnectionProfile,
        tokens: List<String>,
        recursive: Boolean,
    ): OperationResult<String> {
        val writable = requireWritable(profile) ?: return writableFailure()
        val parsed = parseDeleteArguments(tokens, recursive)
            ?: return OperationResult.Failure(
                AppError.Validation(if (recursive) "Usage: ${tokens.first()} <path>" else "Usage: delete [-v version] <path>"),
            )
        val pathToken = parsed.path
        val path = parsePath(pathToken) ?: return invalidPath(pathToken)
        if (path == ZNodePath.Root) {
            return OperationResult.Failure(AppError.Validation("Deleting the root znode is not supported."))
        }
        return when (val result = repository.deleteNode(
            writable,
            DeleteZNodeRequest(path = path, recursive = parsed.recursive, expectedVersion = parsed.version),
        )) {
            is OperationResult.Success -> OperationResult.Success("Deleted $path")
            is OperationResult.Failure -> result
        }
    }

    private suspend fun setAcl(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val writable = requireWritable(profile) ?: return writableFailure()
        val usage = "Usage: setAcl [-s] [-v version] [-R] <path> <scheme:id:perms[,scheme:id:perms]>"
        val parsed = parseSetAclArguments(tokens)
            ?: return OperationResult.Failure(AppError.Validation(usage))
        if (parsed.showStat && parsed.recursive) {
            return OperationResult.Failure(AppError.Validation(usage))
        }
        val path = parsePath(parsed.path) ?: return invalidPath(parsed.path)
        val targets = if (parsed.recursive) {
            when (val result = listRecursive(profile, path)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> return result
            }
        } else {
            listOf(path)
        }
        var rootUpdate: ZNodeDetail? = null
        for (target in targets) {
            val current = when (val result = repository.loadDetail(profile, target)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> return result
            }
            val expectedAversion = parsed.version ?: current.stat.aversion
            val updated = when (val result = repository.updateAcl(
                writable,
                UpdateZNodeAclRequest(path = target, acl = parsed.acl, expectedAversion = expectedAversion),
            )) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> return result
            }
            if (target == path) rootUpdate = updated
        }
        return OperationResult.Success(
            if (parsed.showStat) {
                rootUpdate?.stat?.render().orEmpty()
            } else if (parsed.recursive) {
                "Set ACL for ${targets.size} znodes"
            } else {
                "Set ACL for $path"
            },
        )
    }

    private fun renderData(
        detail: ZNodeDetail,
        showStat: Boolean,
    ): String =
        if (showStat) {
            buildString {
                appendLine(detail.data.toTextOutput())
                append(detail.stat.render())
            }.trimEnd()
        } else {
            detail.data.toTextOutput()
        }

    private fun renderAcl(
        detail: ZNodeDetail,
        showStat: Boolean,
    ): String =
        buildString {
            append(
                detail.acl.ifEmpty {
                    listOf(ZNodeAcl("world", "anyone", setOf(ZNodePermission.Read)))
                }.joinToString("\n") { acl ->
                    "${acl.scheme}:${acl.id}:${acl.permissions.toCliPermissions()}"
                },
            )
            if (showStat) {
                appendLine()
                append(detail.stat.render())
            }
        }

    private fun requireWritable(profile: ConnectionProfile): ConnectionProfile? =
        profile.takeIf { it.mode == ConnectionMode.ReadWrite }

    private fun writableFailure(): OperationResult.Failure =
        OperationResult.Failure(AppError.Validation("This connection is read only."))

    private fun parsePath(raw: String?): ZNodePath? =
        raw?.let { (ZNodePath.parse(it) as? OperationResult.Success)?.value }

    private fun invalidPath(raw: String?): OperationResult.Failure =
        OperationResult.Failure(AppError.Validation("Invalid ZooKeeper path: ${raw ?: ""}"))

    private suspend fun listRecursive(
        profile: ConnectionProfile,
        root: ZNodePath,
    ): OperationResult<List<ZNodePath>> {
        val paths = mutableListOf(root)
        return when (val result = appendDescendants(profile, root, paths)) {
            is OperationResult.Success -> OperationResult.Success(paths)
            is OperationResult.Failure -> result
        }
    }

    private suspend fun appendDescendants(
        profile: ConnectionProfile,
        parent: ZNodePath,
        paths: MutableList<ZNodePath>,
    ): OperationResult<Unit> {
        val children = when (val result = repository.loadChildren(profile, parent)) {
            is OperationResult.Success -> result.value.sortedBy { it.path.value }
            is OperationResult.Failure -> return result
        }
        for (child in children) {
            paths += child.path
            when (val result = appendDescendants(profile, child.path, paths)) {
                is OperationResult.Success -> Unit
                is OperationResult.Failure -> return result
            }
        }
        return OperationResult.Success(Unit)
    }
}

private fun helpText(): String =
    """
    Supported commands:
      ls [-s] [-R] [path]
      ls2 [path]
      get [-s] <path>
      stat <path>
      getAcl [-s] <path>
      create [-e] [-s] <path> [data]
      set [-s] [-v version] <path> <data>
      delete [-v version] <path>
      rmr <path>
      deleteall <path>
      setAcl [-s] [-v version] [-R] <path> <scheme:id:perms[,scheme:id:perms]>

    Permissions use cdrwa: create, delete, read, write, admin.
    """.trimIndent()

private data class ParsedCreateArguments(
    val path: String,
    val data: String,
    val mode: ZNodeCreateMode,
)

private data class ParsedLsArguments(
    val path: String,
    val showStat: Boolean,
    val recursive: Boolean,
)

private data class ParsedPathAndStatFlag(
    val path: String,
    val showStat: Boolean,
)

private data class ParsedSetArguments(
    val path: String,
    val data: String,
    val version: Int?,
    val showStat: Boolean,
)

private data class ParsedDeleteArguments(
    val path: String,
    val recursive: Boolean,
    val version: Int?,
)

private data class ParsedSetAclArguments(
    val path: String,
    val acl: List<ZNodeAcl>,
    val version: Int?,
    val showStat: Boolean,
    val recursive: Boolean,
)

private fun parseLsArguments(tokens: List<String>): ParsedLsArguments? {
    var showStat = false
    var recursive = false
    val values = mutableListOf<String>()
    tokens.drop(1).forEach { token ->
        when (token) {
            "-s" -> showStat = true
            "-R" -> recursive = true
            else -> {
                if (token.startsWith("-")) return null
                values += token
            }
        }
    }
    if (values.size > 1) return null
    return ParsedLsArguments(
        path = values.firstOrNull() ?: "/",
        showStat = showStat,
        recursive = recursive,
    )
}

private fun parsePathAndStatFlag(tokens: List<String>): ParsedPathAndStatFlag? {
    var showStat = false
    val values = mutableListOf<String>()
    tokens.drop(1).forEach { token ->
        when (token) {
            "-s" -> showStat = true
            else -> {
                if (token.startsWith("-")) return null
                values += token
            }
        }
    }
    if (values.size != 1) return null
    return ParsedPathAndStatFlag(path = values.single(), showStat = showStat)
}

private fun parseCreateArguments(tokens: List<String>): ParsedCreateArguments? {
    var ephemeral = false
    var sequential = false
    var index = 1
    while (index < tokens.size) {
        when (tokens[index]) {
            "-e" -> ephemeral = true
            "-s" -> sequential = true
            else -> {
                if (tokens[index].startsWith("-")) return null
                val values = tokens.drop(index)
                if (values.size > 2) return null
                val mode = when {
                    ephemeral && sequential -> ZNodeCreateMode.EphemeralSequential
                    ephemeral -> ZNodeCreateMode.Ephemeral
                    sequential -> ZNodeCreateMode.PersistentSequential
                    else -> ZNodeCreateMode.Persistent
                }
                return ParsedCreateArguments(
                    path = values.first(),
                    data = values.getOrNull(1).orEmpty(),
                    mode = mode,
                )
            }
        }
        index += 1
    }
    return null
}

private fun parseSetArguments(tokens: List<String>): ParsedSetArguments? {
    var showStat = false
    var version: Int? = null
    val values = mutableListOf<String>()
    var index = 1
    while (index < tokens.size) {
        when (tokens[index]) {
            "-s" -> showStat = true
            "-v" -> {
                index += 1
                version = tokens.getOrNull(index)?.toIntOrNull() ?: return null
            }

            else -> {
                if (tokens[index].startsWith("-")) return null
                values += tokens.drop(index)
                break
            }
        }
        index += 1
    }
    val path = values.firstOrNull() ?: return null
    val dataTokens = values.drop(1)
    if (dataTokens.isEmpty()) return null
    return ParsedSetArguments(
        path = path,
        data = dataTokens.joinToString(" "),
        version = version,
        showStat = showStat,
    )
}

private fun parseDeleteArguments(
    tokens: List<String>,
    recursiveCommand: Boolean,
): ParsedDeleteArguments? {
    if (recursiveCommand) {
        val path = tokens.getOrNull(1) ?: return null
        if (tokens.size != 2 || path.startsWith("-")) return null
        return ParsedDeleteArguments(path = path, recursive = true, version = null)
    }
    var version: Int? = null
    val values = mutableListOf<String>()
    var index = 1
    while (index < tokens.size) {
        when (tokens[index]) {
            "-v" -> {
                index += 1
                version = tokens.getOrNull(index)?.toIntOrNull() ?: return null
            }

            else -> {
                if (tokens[index].startsWith("-")) return null
                values += tokens.drop(index)
                break
            }
        }
        index += 1
    }
    val path = values.firstOrNull() ?: return null
    if (values.size != 1) return null
    return ParsedDeleteArguments(
        path = path,
        recursive = false,
        version = version,
    )
}

private fun parseSetAclArguments(tokens: List<String>): ParsedSetAclArguments? {
    var showStat = false
    var recursive = false
    var version: Int? = null
    val values = mutableListOf<String>()
    var index = 1
    while (index < tokens.size) {
        when (tokens[index]) {
            "-s" -> showStat = true
            "-R" -> recursive = true
            "-v" -> {
                index += 1
                version = tokens.getOrNull(index)?.toIntOrNull() ?: return null
            }

            else -> {
                if (tokens[index].startsWith("-")) return null
                values += tokens.drop(index)
                break
            }
        }
        index += 1
    }
    if (values.size != 2) return null
    val acl = values[1].parseAclList() ?: return null
    return ParsedSetAclArguments(
        path = values[0],
        acl = acl,
        version = version,
        showStat = showStat,
        recursive = recursive,
    )
}

private fun String.tokenizeCommandLine(): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var escaping = false

    forEach { char ->
        when {
            escaping -> {
                current.append(char)
                escaping = false
            }

            char == '\\' -> escaping = true
            quote != null && char == quote -> quote = null
            quote != null -> current.append(char)
            char == '"' || char == '\'' -> quote = char
            char.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }

            else -> current.append(char)
        }
    }
    if (current.isNotEmpty()) tokens += current.toString()
    return tokens
}

private fun ZNodeStat.render(): String =
    """
    cZxid = $czxid
    ctime = $ctimeMillis
    mZxid = $mzxid
    mtime = $mtimeMillis
    pZxid = $pzxid
    cversion = $cversion
    dataVersion = $version
    aclVersion = $aversion
    ephemeralOwner = $ephemeralOwner
    dataLength = $dataLength
    numChildren = $numChildren
    """.trimIndent()

private fun ByteArray.toTextOutput(): String =
    runCatching { decodeToString(throwOnInvalidSequence = true) }
        .getOrElse { joinToString(" ") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') } }

private fun Set<ZNodePermission>.toCliPermissions(): String =
    buildString {
        if (ZNodePermission.Create in this@toCliPermissions) append('c')
        if (ZNodePermission.Delete in this@toCliPermissions) append('d')
        if (ZNodePermission.Read in this@toCliPermissions) append('r')
        if (ZNodePermission.Write in this@toCliPermissions) append('w')
        if (ZNodePermission.Admin in this@toCliPermissions) append('a')
    }

private fun String.parseAclList(): List<ZNodeAcl>? =
    split(",")
        .filter { it.isNotBlank() }
        .map { raw ->
            val parts = raw.split(":")
            if (parts.size != 3) return null
            val permissions = parts[2].mapNotNull { char ->
                when (char) {
                    'c' -> ZNodePermission.Create
                    'd' -> ZNodePermission.Delete
                    'r' -> ZNodePermission.Read
                    'w' -> ZNodePermission.Write
                    'a' -> ZNodePermission.Admin
                    else -> return null
                }
            }.toSet()
            if (parts[0].isBlank() || parts[1].isBlank() || permissions.isEmpty()) return null
            ZNodeAcl(
                scheme = parts[0],
                id = parts[1],
                permissions = permissions,
            )
        }

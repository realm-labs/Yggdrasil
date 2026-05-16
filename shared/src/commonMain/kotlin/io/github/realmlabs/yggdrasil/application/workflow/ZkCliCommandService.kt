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
        val path = parsePath(tokens.getOrNull(1) ?: "/") ?: return invalidPath(tokens.getOrNull(1))
        return when (val children = repository.loadChildren(profile, path)) {
            is OperationResult.Success -> OperationResult.Success(
                children.value.joinToString(prefix = "[", postfix = "]") { it.path.name },
            )

            is OperationResult.Failure -> children
        }
    }

    private suspend fun ls2(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val path = parsePath(tokens.getOrNull(1) ?: "/") ?: return invalidPath(tokens.getOrNull(1))
        val children = when (val result = repository.loadChildren(profile, path)) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }
        val detail = when (val result = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }
        return OperationResult.Success(
            buildString {
                appendLine(children.joinToString(prefix = "[", postfix = "]") { it.path.name })
                append(detail.stat.render())
            }.trimEnd(),
        )
    }

    private suspend fun get(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val path = parsePath(tokens.getOrNull(1)) ?: return invalidPath(tokens.getOrNull(1))
        return when (val detail = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> OperationResult.Success(
                buildString {
                    appendLine(detail.value.data.toTextOutput())
                    append(detail.value.stat.render())
                }.trimEnd(),
            )

            is OperationResult.Failure -> detail
        }
    }

    private suspend fun stat(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
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
        val path = parsePath(tokens.getOrNull(1)) ?: return invalidPath(tokens.getOrNull(1))
        return when (val detail = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> OperationResult.Success(
                detail.value.acl.ifEmpty {
                    listOf(ZNodeAcl("world", "anyone", setOf(ZNodePermission.Read)))
                }.joinToString("\n") { acl ->
                    "${acl.scheme}:${acl.id}:${acl.permissions.toCliPermissions()}"
                },
            )

            is OperationResult.Failure -> detail
        }
    }

    private suspend fun create(
        profile: ConnectionProfile,
        tokens: List<String>,
    ): OperationResult<String> {
        val writable = requireWritable(profile) ?: return writableFailure()
        val path = parsePath(tokens.getOrNull(1)) ?: return invalidPath(tokens.getOrNull(1))
        val data = tokens.drop(2).joinToString(" ").encodeToByteArray()
        return when (val result = repository.createNode(
            writable,
            CreateZNodeRequest(path = path, data = data, mode = ZNodeCreateMode.Persistent),
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
        val path = parsePath(tokens.getOrNull(1)) ?: return invalidPath(tokens.getOrNull(1))
        val data = tokens.drop(2).joinToString(" ").encodeToByteArray()
        val current = when (val result = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }
        return when (val result = repository.updateData(
            writable,
            UpdateZNodeDataRequest(path = path, data = data, expectedVersion = current.stat.version),
        )) {
            is OperationResult.Success -> OperationResult.Success("Set ${result.value.path} version ${result.value.stat.version}")
            is OperationResult.Failure -> result
        }
    }

    private suspend fun delete(
        profile: ConnectionProfile,
        tokens: List<String>,
        recursive: Boolean,
    ): OperationResult<String> {
        val writable = requireWritable(profile) ?: return writableFailure()
        val pathToken = tokens.firstOrNull { it != tokens.first() && it != "-r" }
        val path = parsePath(pathToken) ?: return invalidPath(pathToken)
        if (path == ZNodePath.Root) {
            return OperationResult.Failure(AppError.Validation("Deleting the root znode is not supported."))
        }
        val useRecursive = recursive || tokens.contains("-r")
        return when (val result = repository.deleteNode(
            writable,
            DeleteZNodeRequest(path = path, recursive = useRecursive),
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
        val path = parsePath(tokens.getOrNull(1)) ?: return invalidPath(tokens.getOrNull(1))
        val acl = tokens.getOrNull(2)?.parseAclList()
            ?: return OperationResult.Failure(AppError.Validation("Usage: setAcl <path> <scheme:id:perms[,scheme:id:perms]>"))
        val current = when (val result = repository.loadDetail(profile, path)) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }
        return when (val result = repository.updateAcl(
            writable,
            UpdateZNodeAclRequest(path = path, acl = acl, expectedAversion = current.stat.aversion),
        )) {
            is OperationResult.Success -> OperationResult.Success("Set ACL for ${result.value.path}")
            is OperationResult.Failure -> result
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
}

private fun helpText(): String =
    """
    Supported commands:
      ls [path]
      ls2 [path]
      get <path>
      stat <path>
      getAcl <path>
      create <path> [data]
      set <path> <data>
      delete <path>
      delete <path> -r
      rmr <path>
      deleteall <path>
      setAcl <path> <scheme:id:perms[,scheme:id:perms]>

    Permissions use cdrwa: create, delete, read, write, admin.
    """.trimIndent()

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

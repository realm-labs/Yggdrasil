package io.github.realmlabs.yggdrasil.domain.model

data class ConnectionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Connection id cannot be blank." }
    }
}

data class ConnectionProfile(
    val id: ConnectionId,
    val name: String,
    val connectionString: String,
    val chroot: ZNodePath? = null,
    val security: ConnectionSecurity = ConnectionSecurity.None,
    val mode: ConnectionMode = ConnectionMode.ReadOnly,
    val tags: Set<String> = emptySet(),
) {
    init {
        require(name.isNotBlank()) { "Connection name cannot be blank." }
        require(connectionString.isNotBlank()) { "Connection string cannot be blank." }
    }
}

data class ConnectionProfileDraft(
    val name: String = "",
    val connectionString: String = "",
    val chroot: String = "",
    val mode: ConnectionMode = ConnectionMode.ReadOnly,
) {
    fun toProfile(id: ConnectionId): OperationResult<ConnectionProfile> {
        val trimmedName = name.trim()
        val trimmedConnectionString = connectionString.trim()
        val trimmedChroot = chroot.trim()

        if (trimmedName.isBlank()) {
            return OperationResult.Failure(AppError.Validation("Connection name is required."))
        }

        if (trimmedConnectionString.isBlank()) {
            return OperationResult.Failure(AppError.Validation("ZooKeeper connection string is required."))
        }

        val parsedChroot = if (trimmedChroot.isBlank()) {
            null
        } else {
            when (val result = ZNodePath.parse(trimmedChroot)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> return result
            }
        }

        return try {
            OperationResult.Success(
                ConnectionProfile(
                    id = id,
                    name = trimmedName,
                    connectionString = trimmedConnectionString,
                    chroot = parsedChroot,
                    mode = mode,
                ),
            )
        } catch (exception: IllegalArgumentException) {
            OperationResult.Failure(
                AppError.Validation(
                    message = exception.message ?: "Connection profile is not valid.",
                ),
            )
        }
    }
}

enum class ConnectionMode {
    ReadOnly,
    ReadWrite,
}

sealed interface ConnectionSecurity {
    data object None : ConnectionSecurity

    data class Digest(
        val username: String,
        val credentialRef: String,
    ) : ConnectionSecurity

    data class Sasl(
        val principal: String,
    ) : ConnectionSecurity
}

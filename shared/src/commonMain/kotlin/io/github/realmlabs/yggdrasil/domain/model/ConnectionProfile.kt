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
    val sshTunnel: SshTunnelConfig? = null,
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
    val sshTunnelEnabled: Boolean = false,
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUsername: String = "",
    val sshIdentityFile: String = "",
    val sshAuthenticationMethod: SshAuthenticationMethod = SshAuthenticationMethod.PublicKey,
    val sshCredentialRef: String? = null,
    val sshSecret: String = "",
) {
    fun toProfile(id: ConnectionId): OperationResult<ConnectionProfile> {
        val trimmedName = name.trim()
        val trimmedConnectionString = connectionString.trim()
        val trimmedChroot = chroot.trim()
        val parsedSshTunnel = if (sshTunnelEnabled) parseSshTunnel() else OperationResult.Success(null)

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
        val sshTunnel = when (parsedSshTunnel) {
            is OperationResult.Success -> parsedSshTunnel.value
            is OperationResult.Failure -> return parsedSshTunnel
        }

        return try {
            OperationResult.Success(
                ConnectionProfile(
                    id = id,
                    name = trimmedName,
                    connectionString = trimmedConnectionString,
                    chroot = parsedChroot,
                    sshTunnel = sshTunnel,
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

    private fun parseSshTunnel(): OperationResult<SshTunnelConfig?> {
        val trimmedHost = sshHost.trim()
        val trimmedUsername = sshUsername.trim()
        val trimmedIdentityFile = sshIdentityFile.trim()
        val trimmedSecret = sshSecret.trim()
        val parsedPort = sshPort.trim().toIntOrNull()
        val credentialRef = sshCredentialRef?.takeIf { it.isNotBlank() }
            ?: "ssh:${trimmedUsername}@${trimmedHost}:${parsedPort ?: sshPort.trim()}:${sshAuthenticationMethod.name.lowercase()}"

        return when {
            trimmedHost.isBlank() -> OperationResult.Failure(AppError.Validation("SSH host is required."))
            trimmedUsername.isBlank() -> OperationResult.Failure(AppError.Validation("SSH username is required."))
            parsedPort == null || parsedPort !in 1..65535 -> {
                OperationResult.Failure(AppError.Validation("SSH port must be between 1 and 65535."))
            }
            sshAuthenticationMethod == SshAuthenticationMethod.PublicKey && trimmedIdentityFile.isBlank() -> {
                OperationResult.Failure(AppError.Validation("SSH identity file is required."))
            }
            sshAuthenticationMethod == SshAuthenticationMethod.Password && sshCredentialRef == null && trimmedSecret.isBlank() -> {
                OperationResult.Failure(AppError.Validation("SSH password is required."))
            }

            else -> OperationResult.Success(
                SshTunnelConfig(
                    host = trimmedHost,
                    port = parsedPort,
                    username = trimmedUsername,
                    identityFile = trimmedIdentityFile.takeIf { it.isNotBlank() },
                    authenticationMethod = sshAuthenticationMethod,
                    credentialRef = credentialRef,
                ),
            )
        }
    }
}

enum class ConnectionMode {
    ReadOnly,
    ReadWrite,
}

enum class SshAuthenticationMethod {
    PublicKey,
    Password,
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

data class SshTunnelConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val identityFile: String? = null,
    val authenticationMethod: SshAuthenticationMethod = SshAuthenticationMethod.PublicKey,
    val credentialRef: String? = null,
) {
    init {
        require(host.isNotBlank()) { "SSH host cannot be blank." }
        require(username.isNotBlank()) { "SSH username cannot be blank." }
        require(port in 1..65535) { "SSH port must be between 1 and 65535." }
    }

    val target: String
        get() = "$username@$host"
}

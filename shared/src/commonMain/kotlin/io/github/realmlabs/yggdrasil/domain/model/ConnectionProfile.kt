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

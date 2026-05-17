package io.github.realmlabs.yggdrasil.storage

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ConnectionProfileRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class LocalConnectionProfileRepository(
    private val file: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : ConnectionProfileRepository {
    override suspend fun loadProfiles(): OperationResult<List<ConnectionProfile>> =
        readStore().map { store -> store.profiles.mapNotNull(ConnectionProfileRecord::toDomainOrNull) }

    override suspend fun saveProfile(profile: ConnectionProfile): OperationResult<Unit> {
        val current = when (val result = readStore()) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }

        val nextProfiles = current.profiles
            .filterNot { it.id == profile.id.value }
            .plus(ConnectionProfileRecord.fromDomain(profile))
            .sortedBy { it.name.lowercase() }

        return writeStore(ConnectionProfileStore(profiles = nextProfiles))
    }

    override suspend fun deleteProfile(id: ConnectionId): OperationResult<Unit> {
        val current = when (val result = readStore()) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }

        return writeStore(
            current.copy(profiles = current.profiles.filterNot { it.id == id.value }),
        )
    }

    private fun readStore(): OperationResult<ConnectionProfileStore> =
        try {
            if (!file.exists()) {
                OperationResult.Success(ConnectionProfileStore())
            } else {
                OperationResult.Success(json.decodeFromString<ConnectionProfileStore>(file.readText()))
            }
        } catch (exception: SerializationException) {
            OperationResult.Failure(
                AppError.Storage(
                    message = "Connection profile store is not valid JSON.",
                    cause = exception.message,
                ),
            )
        } catch (exception: IOException) {
            OperationResult.Failure(
                AppError.Storage(
                    message = "Could not read connection profiles.",
                    cause = exception.message,
                ),
            )
        }

    private fun writeStore(store: ConnectionProfileStore): OperationResult<Unit> =
        try {
            Files.createDirectories(file.parent)
            file.writeText(json.encodeToString(ConnectionProfileStore.serializer(), store))
            OperationResult.Success(Unit)
        } catch (exception: IOException) {
            OperationResult.Failure(
                AppError.Storage(
                    message = "Could not save connection profiles.",
                    cause = exception.message,
                ),
            )
        }
}

@Serializable
private data class ConnectionProfileStore(
    val profiles: List<ConnectionProfileRecord> = emptyList(),
)

@Serializable
private data class ConnectionProfileRecord(
    val id: String,
    val name: String,
    val connectionString: String,
    val chroot: String? = null,
    val sshTunnel: SshTunnelRecord? = null,
    val mode: String = ConnectionMode.ReadOnly.name,
    val tags: List<String> = emptyList(),
) {
    fun toDomainOrNull(): ConnectionProfile? {
        val parsedChroot = chroot?.let { raw ->
            when (val result = ZNodePath.parse(raw)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> return null
            }
        }

        val parsedMode = ConnectionMode.entries.firstOrNull { it.name == mode } ?: ConnectionMode.ReadOnly

        return try {
            ConnectionProfile(
                id = ConnectionId(id),
                name = name,
                connectionString = connectionString,
                chroot = parsedChroot,
                security = ConnectionSecurity.None,
                sshTunnel = sshTunnel?.toDomain(),
                mode = parsedMode,
                tags = tags.toSet(),
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        fun fromDomain(profile: ConnectionProfile): ConnectionProfileRecord =
            ConnectionProfileRecord(
                id = profile.id.value,
                name = profile.name,
                connectionString = profile.connectionString,
                chroot = profile.chroot?.value,
                sshTunnel = profile.sshTunnel?.let(SshTunnelRecord::fromDomain),
                mode = profile.mode.name,
                tags = profile.tags.sorted(),
            )
    }
}

@Serializable
private data class SshTunnelRecord(
    val host: String,
    val port: Int = 22,
    val username: String,
    val identityFile: String? = null,
    val authenticationMethod: String = SshAuthenticationMethod.PublicKey.name,
    val credentialRef: String? = null,
) {
    fun toDomain(): SshTunnelConfig? =
        try {
            val parsedAuthenticationMethod = SshAuthenticationMethod.entries
                .firstOrNull { it.name == authenticationMethod }
                ?: SshAuthenticationMethod.PublicKey
            SshTunnelConfig(
                host = host,
                port = port,
                username = username,
                identityFile = identityFile,
                authenticationMethod = parsedAuthenticationMethod,
                credentialRef = credentialRef,
            )
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        fun fromDomain(config: SshTunnelConfig): SshTunnelRecord =
            SshTunnelRecord(
                host = config.host,
                port = config.port,
                username = config.username,
                identityFile = config.identityFile,
                authenticationMethod = config.authenticationMethod.name,
                credentialRef = config.credentialRef,
            )
    }
}

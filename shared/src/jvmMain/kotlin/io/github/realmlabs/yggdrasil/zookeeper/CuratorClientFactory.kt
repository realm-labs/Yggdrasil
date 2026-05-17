package io.github.realmlabs.yggdrasil.zookeeper

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.CredentialRepository
import io.github.realmlabs.yggdrasil.storage.createCredentialAskPassScripts
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

internal class AsyncCuratorClient private constructor(
    private val client: CuratorFramework,
    val async: AsyncCuratorFramework,
    private val sshTunnel: SshTunnelProcess? = null,
) : AutoCloseable {
    override fun close() {
        client.close()
        sshTunnel?.close()
    }

    internal companion object {
        fun start(
            profile: ConnectionProfile,
            credentialRepository: CredentialRepository?,
            connectionTimeoutMillis: Int,
            sessionTimeoutMillis: Int,
        ): AsyncCuratorClient {
            val sshTunnel = profile.sshTunnel?.let { startSshTunnel(profile, credentialRepository) }
            try {
                val client = createCuratorClient(
                    profile = profile,
                    credentialRepository = credentialRepository,
                    connectionTimeoutMillis = connectionTimeoutMillis,
                    sessionTimeoutMillis = sessionTimeoutMillis,
                    tunneledConnectionString = sshTunnel?.connectionString,
                )
                try {
                    client.start()
                } catch (exception: Exception) {
                    client.close()
                    throw exception
                }
                return AsyncCuratorClient(
                    client = client,
                    async = AsyncCuratorFramework.wrap(client),
                    sshTunnel = sshTunnel,
                )
            } catch (exception: Exception) {
                sshTunnel?.close()
                throw exception
            }
        }
    }
}

internal fun createAsyncCuratorClient(
    profile: ConnectionProfile,
    credentialRepository: CredentialRepository? = null,
    connectionTimeoutMillis: Int = 5_000,
    sessionTimeoutMillis: Int = 10_000,
): AsyncCuratorClient =
    AsyncCuratorClient.start(
        profile = profile,
        credentialRepository = credentialRepository,
        connectionTimeoutMillis = connectionTimeoutMillis,
        sessionTimeoutMillis = sessionTimeoutMillis,
    )

internal suspend fun AsyncCuratorClient.awaitConnected(
    profile: ConnectionProfile,
    connectionTimeoutMillis: Int,
): OperationResult<Unit> =
    try {
        withTimeout(connectionTimeoutMillis.toLong().milliseconds) {
            async.checkExists().forPath("/").await()
        }
        OperationResult.Success(Unit)
    } catch (exception: Exception) {
        OperationResult.Failure(
            AppError.Connection(
                message = "Could not connect to ${profile.connectionString}.",
                cause = exception.message,
            ),
        )
    }

private fun createCuratorClient(
    profile: ConnectionProfile,
    credentialRepository: CredentialRepository?,
    connectionTimeoutMillis: Int = 5_000,
    sessionTimeoutMillis: Int = 10_000,
    tunneledConnectionString: String? = null,
): CuratorFramework {
    val connectString = (tunneledConnectionString ?: profile.connectionString) +
            (profile.chroot?.value?.takeIf { it != "/" } ?: "")

    val builder = CuratorFrameworkFactory.builder()
        .connectString(connectString)
        .connectionTimeoutMs(connectionTimeoutMillis)
        .sessionTimeoutMs(sessionTimeoutMillis)
        .retryPolicy(ExponentialBackoffRetry(1_000, 3))

    when (val security = profile.security) {
        is ConnectionSecurity.Digest -> {
            val password = readCredentialOrThrow(credentialRepository, security.credentialRef)
            builder.authorization("digest", "${security.username}:$password".encodeToByteArray())
        }

        ConnectionSecurity.None,
        is ConnectionSecurity.Sasl -> Unit
    }

    return builder.build()
}

private fun startSshTunnel(
    profile: ConnectionProfile,
    credentialRepository: CredentialRepository?,
): SshTunnelProcess {
    val tunnel = requireNotNull(profile.sshTunnel)
    val credentialRef = requireNotNull(tunnel.credentialRef)
    readCredentialOrThrow(credentialRepository, credentialRef)
    val connection = parseZooKeeperConnectionString(profile.connectionString)
    val localEndpoints = connection.endpoints.map { endpoint ->
        LocalZooKeeperEndpoint(
            localPort = findAvailableLocalPort(excluding = emptySet()),
            target = endpoint,
        )
    }.deduplicateLocalPorts()
    val askPassScripts = createCredentialAskPassScripts(credentialRef)
    val command = buildList {
        add("ssh")
        add("-N")
        localEndpoints.forEach { endpoint ->
            add("-L")
            add("127.0.0.1:${endpoint.localPort}:${endpoint.target.host}:${endpoint.target.port}")
        }
        add("-p")
        add(tunnel.port.toString())
        add("-o")
        add("ExitOnForwardFailure=yes")
        add("-o")
        add("BatchMode=no")
        add("-o")
        add("NumberOfPasswordPrompts=1")
        add("-o")
        add("StrictHostKeyChecking=accept-new")
        add("-o")
        add("ConnectTimeout=10")
        add("-o")
        add("IdentitiesOnly=yes")
        add("-o")
        add("IdentityAgent=none")
        when (tunnel.authenticationMethod) {
            SshAuthenticationMethod.Password -> {
                add("-o")
                add("PreferredAuthentications=password,keyboard-interactive")
            }
            SshAuthenticationMethod.PublicKey -> {
                add("-o")
                add("PreferredAuthentications=publickey")
            }
        }
        tunnel.identityFile?.let { identityFile ->
            add("-i")
            add(identityFile)
        }
        add(tunnel.target)
    }

    val processBuilder = ProcessBuilder(command)
        .redirectErrorStream(true)
    processBuilder.environment()["SSH_ASKPASS"] = askPassScripts.first().toAbsolutePath().toString()
    processBuilder.environment()["SSH_ASKPASS_REQUIRE"] = "force"
    processBuilder.environment()["DISPLAY"] = processBuilder.environment()["DISPLAY"] ?: "localhost:0"
    val process = try {
        processBuilder.start()
    } catch (exception: Exception) {
        askPassScripts.deleteIfExists()
        throw exception
    }
    try {
        process.outputStream.close()
        val exitedEarly = process.waitFor(700, TimeUnit.MILLISECONDS)
        if (exitedEarly) {
            val output = process.inputStream.bufferedReader().readText()
            askPassScripts.deleteIfExists()
            throw IllegalStateException(
                "SSH tunnel failed to start.${
                    output.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                }")
        }
    } catch (exception: Exception) {
        process.destroyForcibly()
        askPassScripts.deleteIfExists()
        throw exception
    }

    return SshTunnelProcess(
        process = process,
        connectionString = connection.toLocalConnectionString(localEndpoints),
        askPassScripts = askPassScripts,
    )
}

private fun readCredentialOrThrow(
    credentialRepository: CredentialRepository?,
    credentialRef: String,
): String {
    val repository = credentialRepository ?: throw IOException("Credential storage is not available.")
    return when (val result = kotlinx.coroutines.runBlocking { repository.readCredential(credentialRef) }) {
        is OperationResult.Success -> result.value
        is OperationResult.Failure -> throw IOException(result.error.cause ?: result.error.message)
    }
}

internal data class ZooKeeperEndpoint(
    val host: String,
    val port: Int,
)

internal data class ParsedZooKeeperConnectionString(
    val endpoints: List<ZooKeeperEndpoint>,
    val chroot: String?,
)

internal data class LocalZooKeeperEndpoint(
    val localPort: Int,
    val target: ZooKeeperEndpoint,
)

internal fun parseZooKeeperConnectionString(connectionString: String): ParsedZooKeeperConnectionString {
    val trimmedConnectionString = connectionString.trim()
    val endpointList = trimmedConnectionString.substringBefore("/")
    val chroot = trimmedConnectionString
        .substringAfter("/", missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.let { "/$it" }
    val endpoints = endpointList
        .split(",")
        .map { rawEndpoint -> parseZooKeeperEndpoint(rawEndpoint.trim()) }
    require(endpoints.isNotEmpty()) {
        "SSH tunnel requires at least one ZooKeeper host:port endpoint."
    }
    return ParsedZooKeeperConnectionString(
        endpoints = endpoints,
        chroot = chroot,
    )
}

private fun parseZooKeeperEndpoint(rawEndpoint: String): ZooKeeperEndpoint {
    val port = rawEndpoint.substringAfterLast(":", missingDelimiterValue = "").toIntOrNull()
    val host = rawEndpoint.substringBeforeLast(":", missingDelimiterValue = "")
    require(host.isNotBlank() && port != null && port in 1..65535) {
        "SSH tunnel requires ZooKeeper connection string in host:port form."
    }
    return ZooKeeperEndpoint(host = host, port = port)
}

internal fun ParsedZooKeeperConnectionString.toLocalConnectionString(
    localEndpoints: List<LocalZooKeeperEndpoint>,
): String {
    require(localEndpoints.size == endpoints.size) {
        "Local ZooKeeper endpoint count must match target endpoint count."
    }
    val connectString = localEndpoints.joinToString(",") { endpoint ->
        "127.0.0.1:${endpoint.localPort}"
    }
    return connectString + chroot.orEmpty()
}

private fun List<LocalZooKeeperEndpoint>.deduplicateLocalPorts(): List<LocalZooKeeperEndpoint> {
    val usedPorts = mutableSetOf<Int>()
    return map { endpoint ->
        var localPort = endpoint.localPort
        while (!usedPorts.add(localPort)) {
            localPort = findAvailableLocalPort(excluding = usedPorts)
        }
        endpoint.copy(localPort = localPort)
    }
}

private fun findAvailableLocalPort(excluding: Set<Int>): Int {
    while (true) {
        val port = ServerSocket(0).use { socket -> socket.localPort }
        if (port !in excluding) return port
    }
}

private fun List<Path>.deleteIfExists() {
    forEach { path ->
        java.nio.file.Files.deleteIfExists(path)
    }
}

private class SshTunnelProcess(
    private val process: Process,
    val connectionString: String,
    private val askPassScripts: List<Path>,
) : AutoCloseable {
    override fun close() {
        process.destroy()
        if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
        }
        askPassScripts.deleteIfExists()
    }
}

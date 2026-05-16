package io.github.realmlabs.yggdrasil.zookeeper

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.x.async.AsyncCuratorFramework
import java.net.ServerSocket
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
            connectionTimeoutMillis: Int,
            sessionTimeoutMillis: Int,
        ): AsyncCuratorClient {
            val sshTunnel = profile.sshTunnel?.let { startSshTunnel(profile) }
            val client = createCuratorClient(
                profile = profile,
                connectionTimeoutMillis = connectionTimeoutMillis,
                sessionTimeoutMillis = sessionTimeoutMillis,
                tunneledConnectionString = sshTunnel?.connectionString,
            )
            client.start()
            return AsyncCuratorClient(
                client = client,
                async = AsyncCuratorFramework.wrap(client),
                sshTunnel = sshTunnel,
            )
        }
    }
}

internal fun createAsyncCuratorClient(
    profile: ConnectionProfile,
    connectionTimeoutMillis: Int = 5_000,
    sessionTimeoutMillis: Int = 10_000,
): AsyncCuratorClient =
    AsyncCuratorClient.start(
        profile = profile,
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
    connectionTimeoutMillis: Int = 5_000,
    sessionTimeoutMillis: Int = 10_000,
    tunneledConnectionString: String? = null,
): CuratorFramework {
    val connectString = (tunneledConnectionString ?: profile.connectionString) +
            (profile.chroot?.value?.takeIf { it != "/" } ?: "")

    return CuratorFrameworkFactory.builder()
        .connectString(connectString)
        .connectionTimeoutMs(connectionTimeoutMillis)
        .sessionTimeoutMs(sessionTimeoutMillis)
        .retryPolicy(ExponentialBackoffRetry(1_000, 3))
        .build()
}

private fun startSshTunnel(profile: ConnectionProfile): SshTunnelProcess {
    val tunnel = requireNotNull(profile.sshTunnel)
    val endpoint = parseSingleZooKeeperEndpoint(profile.connectionString)
    val localPort = findAvailableLocalPort()
    val forwarding = "127.0.0.1:$localPort:${endpoint.host}:${endpoint.port}"
    val command = buildList {
        add("ssh")
        add("-N")
        add("-L")
        add(forwarding)
        add("-p")
        add(tunnel.port.toString())
        add("-o")
        add("ExitOnForwardFailure=yes")
        add("-o")
        add("BatchMode=yes")
        add("-o")
        add("NumberOfPasswordPrompts=0")
        add("-o")
        add("StrictHostKeyChecking=accept-new")
        add("-o")
        add("ConnectTimeout=10")
        tunnel.identityFile?.takeIf { it.isNotBlank() }?.let { identityFile ->
            add("-i")
            add(identityFile)
        }
        add(tunnel.target)
    }

    val process = ProcessBuilder(command)
        .redirectErrorStream(true)
        .start()
    process.outputStream.close()
    val exitedEarly = process.waitFor(700, TimeUnit.MILLISECONDS)
    if (exitedEarly) {
        val output = process.inputStream.bufferedReader().readText()
        throw IllegalStateException(
            "SSH tunnel failed to start.${
                output.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
            }")
    }

    return SshTunnelProcess(
        process = process,
        connectionString = "127.0.0.1:$localPort",
    )
}

private data class ZooKeeperEndpoint(
    val host: String,
    val port: Int,
)

private fun parseSingleZooKeeperEndpoint(connectionString: String): ZooKeeperEndpoint {
    require(!connectionString.contains(",")) {
        "SSH tunnel currently supports a single ZooKeeper host:port connection string."
    }
    val endpoint = connectionString.substringBefore("/")
    val port = endpoint.substringAfterLast(":", missingDelimiterValue = "").toIntOrNull()
    val host = endpoint.substringBeforeLast(":", missingDelimiterValue = "")
    require(host.isNotBlank() && port != null) {
        "SSH tunnel requires ZooKeeper connection string in host:port form."
    }
    return ZooKeeperEndpoint(host = host, port = port)
}

private fun findAvailableLocalPort(): Int =
    ServerSocket(0).use { socket -> socket.localPort }

private class SshTunnelProcess(
    private val process: Process,
    val connectionString: String,
) : AutoCloseable {
    override fun close() {
        process.destroy()
        if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
        }
    }
}

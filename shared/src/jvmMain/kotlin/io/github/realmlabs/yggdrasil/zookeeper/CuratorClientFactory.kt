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
import kotlin.time.Duration.Companion.milliseconds

internal class AsyncCuratorClient private constructor(
    private val client: CuratorFramework,
    val async: AsyncCuratorFramework,
) : AutoCloseable {
    override fun close() {
        client.close()
    }

    internal companion object {
        fun start(
            profile: ConnectionProfile,
            connectionTimeoutMillis: Int,
            sessionTimeoutMillis: Int,
        ): AsyncCuratorClient {
            val client = createCuratorClient(
                profile = profile,
                connectionTimeoutMillis = connectionTimeoutMillis,
                sessionTimeoutMillis = sessionTimeoutMillis,
            )
            client.start()
            return AsyncCuratorClient(
                client = client,
                async = AsyncCuratorFramework.wrap(client),
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
): CuratorFramework {
    val connectString = profile.connectionString + (profile.chroot?.value?.takeIf { it != "/" } ?: "")

    return CuratorFrameworkFactory.builder()
        .connectString(connectString)
        .connectionTimeoutMs(connectionTimeoutMillis)
        .sessionTimeoutMs(sessionTimeoutMillis)
        .retryPolicy(ExponentialBackoffRetry(1_000, 3))
        .build()
}

package io.github.realmlabs.yggdrasil.zookeeper

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.repository.ZooKeeperConnectionTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import java.util.concurrent.TimeUnit

class CuratorZooKeeperConnectionTester(
    private val connectionTimeoutMillis: Int = 5_000,
    private val sessionTimeoutMillis: Int = 10_000,
) : ZooKeeperConnectionTester {
    override suspend fun testConnection(profile: ConnectionProfile): OperationResult<Unit> =
        withContext(Dispatchers.IO) {
            val retryPolicy = ExponentialBackoffRetry(1_000, 3)
            val client = CuratorFrameworkFactory.builder()
                .connectString(profile.connectionString)
                .connectionTimeoutMs(connectionTimeoutMillis)
                .sessionTimeoutMs(sessionTimeoutMillis)
                .retryPolicy(retryPolicy)
                .build()

            try {
                client.start()
                if (client.blockUntilConnected(connectionTimeoutMillis, TimeUnit.MILLISECONDS)) {
                    OperationResult.Success(Unit)
                } else {
                    OperationResult.Failure(
                        AppError.Connection("Timed out connecting to ${profile.connectionString}."),
                    )
                }
            } catch (exception: Exception) {
                OperationResult.Failure(
                    AppError.Connection(
                        message = "Could not connect to ${profile.connectionString}.",
                        cause = exception.message,
                    ),
                )
            } finally {
                client.close()
            }
        }
}

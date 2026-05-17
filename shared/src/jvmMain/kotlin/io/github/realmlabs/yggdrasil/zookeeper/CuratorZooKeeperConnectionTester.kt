package io.github.realmlabs.yggdrasil.zookeeper

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.repository.CredentialRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZooKeeperConnectionTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CuratorZooKeeperConnectionTester(
    private val credentialRepository: CredentialRepository? = null,
    private val connectionTimeoutMillis: Int = 5_000,
    private val sessionTimeoutMillis: Int = 10_000,
) : ZooKeeperConnectionTester {
    override suspend fun testConnection(profile: ConnectionProfile): OperationResult<Unit> {
        val client = try {
            withContext(Dispatchers.IO) {
                createAsyncCuratorClient(
                    profile = profile,
                    credentialRepository = credentialRepository,
                    connectionTimeoutMillis = connectionTimeoutMillis,
                    sessionTimeoutMillis = sessionTimeoutMillis,
                )
            }
        } catch (exception: Exception) {
            return OperationResult.Failure(
                AppError.Connection(
                    message = "Could not connect to ${profile.connectionString}.",
                    cause = exception.message,
                ),
            )
        }

        return try {
            client.awaitConnected(profile, connectionTimeoutMillis)
        } catch (exception: Exception) {
            OperationResult.Failure(
                AppError.Connection(
                    message = "Could not connect to ${profile.connectionString}.",
                    cause = exception.message,
                ),
            )
        } finally {
            withContext(Dispatchers.IO) {
                client.close()
            }
        }
    }
}

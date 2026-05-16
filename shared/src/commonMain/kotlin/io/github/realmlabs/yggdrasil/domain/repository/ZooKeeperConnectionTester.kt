package io.github.realmlabs.yggdrasil.domain.repository

import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.OperationResult

interface ZooKeeperConnectionTester {
    suspend fun testConnection(profile: ConnectionProfile): OperationResult<Unit>
}

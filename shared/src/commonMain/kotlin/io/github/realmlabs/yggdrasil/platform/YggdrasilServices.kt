package io.github.realmlabs.yggdrasil.platform

import io.github.realmlabs.yggdrasil.domain.repository.ConnectionProfileRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZooKeeperConnectionTester

class YggdrasilServices(
    val connectionProfileRepository: ConnectionProfileRepository,
    val zooKeeperConnectionTester: ZooKeeperConnectionTester,
)

expect fun createYggdrasilServices(): YggdrasilServices

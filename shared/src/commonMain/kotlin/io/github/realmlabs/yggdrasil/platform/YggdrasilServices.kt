package io.github.realmlabs.yggdrasil.platform

import io.github.realmlabs.yggdrasil.domain.repository.AppSettingsRepository
import io.github.realmlabs.yggdrasil.domain.repository.ConnectionProfileRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import io.github.realmlabs.yggdrasil.domain.repository.ZooKeeperConnectionTester

class YggdrasilServices(
    val appSettingsRepository: AppSettingsRepository,
    val connectionProfileRepository: ConnectionProfileRepository,
    val zooKeeperConnectionTester: ZooKeeperConnectionTester,
    val zNodeRepository: ZNodeRepository,
)

expect fun createYggdrasilServices(): YggdrasilServices

package io.github.realmlabs.yggdrasil.platform

import io.github.realmlabs.yggdrasil.domain.repository.*

class YggdrasilServices(
    val appSettingsRepository: AppSettingsRepository,
    val connectionProfileRepository: ConnectionProfileRepository,
    val sshCredentialRepository: SshCredentialRepository,
    val zooKeeperConnectionTester: ZooKeeperConnectionTester,
    val zNodeRepository: ZNodeRepository,
)

expect fun createYggdrasilServices(): YggdrasilServices

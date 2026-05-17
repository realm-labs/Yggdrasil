package io.github.realmlabs.yggdrasil.platform

import io.github.realmlabs.yggdrasil.storage.LocalAppSettingsRepository
import io.github.realmlabs.yggdrasil.storage.LocalConnectionProfileRepository
import io.github.realmlabs.yggdrasil.storage.SystemCredentialRepository
import io.github.realmlabs.yggdrasil.zookeeper.CuratorZNodeRepository
import io.github.realmlabs.yggdrasil.zookeeper.CuratorZooKeeperConnectionTester
import java.nio.file.Path

actual fun createYggdrasilServices(): YggdrasilServices {
    val configDirectory = Path.of(System.getProperty("user.home"), ".yggdrasil")
    val credentialRepository = SystemCredentialRepository()

    return YggdrasilServices(
        appSettingsRepository = LocalAppSettingsRepository(configDirectory.resolve("settings.json")),
        connectionProfileRepository = LocalConnectionProfileRepository(configDirectory.resolve("connections.json")),
        credentialRepository = credentialRepository,
        zooKeeperConnectionTester = CuratorZooKeeperConnectionTester(credentialRepository),
        zNodeRepository = CuratorZNodeRepository(credentialRepository),
    )
}

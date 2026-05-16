package io.github.realmlabs.yggdrasil.platform

import io.github.realmlabs.yggdrasil.storage.LocalConnectionProfileRepository
import io.github.realmlabs.yggdrasil.zookeeper.CuratorZNodeRepository
import io.github.realmlabs.yggdrasil.zookeeper.CuratorZooKeeperConnectionTester
import java.nio.file.Path

actual fun createYggdrasilServices(): YggdrasilServices {
    val configDirectory = Path.of(System.getProperty("user.home"), ".yggdrasil")

    return YggdrasilServices(
        connectionProfileRepository = LocalConnectionProfileRepository(configDirectory.resolve("connections.json")),
        zooKeeperConnectionTester = CuratorZooKeeperConnectionTester(),
        zNodeRepository = CuratorZNodeRepository(),
    )
}

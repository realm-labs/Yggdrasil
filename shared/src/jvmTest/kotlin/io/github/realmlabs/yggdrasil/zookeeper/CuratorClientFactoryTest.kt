package io.github.realmlabs.yggdrasil.zookeeper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CuratorClientFactoryTest {
    @Test
    fun parsesSingleZooKeeperEndpoint() {
        val connection = parseZooKeeperConnectionString("zk1.example.com:2181")

        assertEquals(
            listOf(ZooKeeperEndpoint("zk1.example.com", 2181)),
            connection.endpoints,
        )
        assertEquals(null, connection.chroot)
    }

    @Test
    fun parsesEnsembleZooKeeperEndpointsWithChroot() {
        val connection = parseZooKeeperConnectionString(
            "zk1.example.com:2181,zk2.example.com:2182,zk3.example.com:2183/app",
        )

        assertEquals(
            listOf(
                ZooKeeperEndpoint("zk1.example.com", 2181),
                ZooKeeperEndpoint("zk2.example.com", 2182),
                ZooKeeperEndpoint("zk3.example.com", 2183),
            ),
            connection.endpoints,
        )
        assertEquals("/app", connection.chroot)
    }

    @Test
    fun rewritesEnsembleToLocalConnectionString() {
        val connection = parseZooKeeperConnectionString("zk1:2181,zk2:2181/app")

        val localConnectionString = connection.toLocalConnectionString(
            listOf(
                LocalZooKeeperEndpoint(38181, ZooKeeperEndpoint("zk1", 2181)),
                LocalZooKeeperEndpoint(38182, ZooKeeperEndpoint("zk2", 2181)),
            ),
        )

        assertEquals("127.0.0.1:38181,127.0.0.1:38182/app", localConnectionString)
    }

    @Test
    fun rejectsEndpointWithoutPort() {
        assertFailsWith<IllegalArgumentException> {
            parseZooKeeperConnectionString("zk1.example.com")
        }
    }

    @Test
    fun rejectsEndpointWithInvalidPort() {
        assertFailsWith<IllegalArgumentException> {
            parseZooKeeperConnectionString("zk1.example.com:70000")
        }
    }

    @Test
    fun rejectsEmptyEnsembleMember() {
        assertFailsWith<IllegalArgumentException> {
            parseZooKeeperConnectionString("zk1.example.com:2181,,zk3.example.com:2181")
        }
    }
}

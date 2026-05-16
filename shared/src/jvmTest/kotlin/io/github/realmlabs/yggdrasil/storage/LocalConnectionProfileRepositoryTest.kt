package io.github.realmlabs.yggdrasil.storage

import io.github.realmlabs.yggdrasil.domain.model.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocalConnectionProfileRepositoryTest {
    @Test
    fun savesLoadsAndDeletesProfiles() {
        runBlocking {
            val directory = Files.createTempDirectory("yggdrasil-connections")
            val repository = LocalConnectionProfileRepository(directory.resolve("connections.json"))
            val profile = ConnectionProfile(
                id = ConnectionId("local"),
                name = "Local",
                connectionString = "localhost:2181",
                chroot = ZNodePath.requireValid("/app"),
                sshTunnel = SshTunnelConfig(
                    host = "bastion.example.com",
                    port = 2222,
                    username = "deploy",
                    identityFile = "~/.ssh/id_ed25519",
                ),
                mode = ConnectionMode.ReadWrite,
            )

            assertIs<OperationResult.Success<Unit>>(repository.saveProfile(profile))

            val loaded = assertIs<OperationResult.Success<List<ConnectionProfile>>>(repository.loadProfiles()).value
            assertEquals(listOf(profile), loaded)

            assertIs<OperationResult.Success<Unit>>(repository.deleteProfile(profile.id))
            val afterDelete = assertIs<OperationResult.Success<List<ConnectionProfile>>>(repository.loadProfiles()).value
            assertEquals(emptyList(), afterDelete)
        }
    }

    @Test
    fun reportsInvalidJsonAsStorageError() {
        runBlocking {
            val directory = Files.createTempDirectory("yggdrasil-connections")
            val file = directory.resolve("connections.json")
            Files.writeString(file, "{")

            val result = LocalConnectionProfileRepository(file).loadProfiles()

            assertIs<OperationResult.Failure>(result)
        }
    }
}

package io.github.realmlabs.yggdrasil.application.workflow

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ZkCliCommandServiceTest {
    private val readWriteProfile = ConnectionProfile(
        id = ConnectionId("local"),
        name = "Local",
        connectionString = "localhost:2181",
        mode = ConnectionMode.ReadWrite,
    )

    @Test
    fun lsReturnsChildNames() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(readWriteProfile, ZkCliCommandRequest("ls /"))

        val output = assertIs<OperationResult.Success<ZkCliCommandResult>>(result).value.output
        assertEquals("[app]", output)
    }

    @Test
    fun lsSupportsRecursiveFlagBeforePath() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(readWriteProfile, ZkCliCommandRequest("ls -R /"))

        val output = assertIs<OperationResult.Success<ZkCliCommandResult>>(result).value.output
        assertEquals(
            """
            /
            /app
            /app/config
            """.trimIndent(),
            output,
        )
    }

    @Test
    fun lsSupportsStatFlagBeforePath() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(readWriteProfile, ZkCliCommandRequest("ls -s /"))

        val output = assertIs<OperationResult.Success<ZkCliCommandResult>>(result).value.output
        assertEquals(true, output.startsWith("[app]\ncZxid ="))
    }

    @Test
    fun getShowsStatsOnlyWhenRequested() = runBlocking {
        val repository = FakeZNodeRepository()
        val service = ZkCliCommandService(repository)

        val plain = service.execute(readWriteProfile, ZkCliCommandRequest("get /app"))
        val withStat = service.execute(readWriteProfile, ZkCliCommandRequest("get -s /app"))

        assertEquals("ready", assertIs<OperationResult.Success<ZkCliCommandResult>>(plain).value.output)
        assertEquals(
            true,
            assertIs<OperationResult.Success<ZkCliCommandResult>>(withStat).value.output.startsWith("ready\ncZxid =")
        )
    }

    @Test
    fun getAclSupportsStatFlag() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(readWriteProfile, ZkCliCommandRequest("getAcl -s /app"))

        val output = assertIs<OperationResult.Success<ZkCliCommandResult>>(result).value.output
        assertEquals(true, output.startsWith("world:anyone:r\ncZxid ="))
    }

    @Test
    fun setSupportsQuotedDataWithSpaces() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("set /app \"hello world\""),
        )

        assertIs<OperationResult.Success<ZkCliCommandResult>>(result)
        assertEquals("hello world", repository.nodes.getValue("/app").data.decodeToString())
    }

    @Test
    fun setSupportsExpectedVersion() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("set -v 7 /app next"),
        )

        assertIs<OperationResult.Success<ZkCliCommandResult>>(result)
        assertEquals(7, repository.updatedDataRequest?.expectedVersion)
    }

    @Test
    fun setSupportsStatFlag() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("set -s /app next"),
        )

        val output = assertIs<OperationResult.Success<ZkCliCommandResult>>(result).value.output
        assertEquals(true, output.startsWith("cZxid ="))
    }

    @Test
    fun createSupportsEphemeralSequentialFlags() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("create -e -s /created value"),
        )

        assertIs<OperationResult.Success<ZkCliCommandResult>>(result)
        assertEquals(ZNodeCreateMode.EphemeralSequential, repository.createdRequest?.mode)
        assertEquals("value", repository.createdRequest?.data?.decodeToString())
    }

    @Test
    fun deleteSupportsExpectedVersion() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("delete -v 3 /app"),
        )

        assertIs<OperationResult.Success<ZkCliCommandResult>>(result)
        assertEquals(3, repository.deleteRequest?.expectedVersion)
    }

    @Test
    fun setAclSupportsVersionStatAndRecursiveFlags() = runBlocking {
        val repository = FakeZNodeRepository()
        val service = ZkCliCommandService(repository)

        val statResult = service.execute(
            readWriteProfile,
            ZkCliCommandRequest("setAcl -s -v 2 /app world:anyone:rw"),
        )
        val recursiveResult = service.execute(
            readWriteProfile,
            ZkCliCommandRequest("setAcl -R /app world:anyone:r"),
        )

        assertEquals(
            true,
            assertIs<OperationResult.Success<ZkCliCommandResult>>(statResult).value.output.startsWith("cZxid =")
        )
        assertIs<OperationResult.Success<ZkCliCommandResult>>(recursiveResult)
        assertEquals(2, repository.aclRequests.first().expectedAversion)
        assertEquals(listOf("/app", "/app", "/app/config"), repository.aclRequests.map { it.path.value })
    }

    @Test
    fun rejectsUnsupportedWatchFlag() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("ls -w /"),
        )

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("Usage: ls [-s] [-R] [path]", failure.error.message)
    }

    @Test
    fun rejectsUnsupportedGetWatchFlag() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("get -w /app"),
        )

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("Usage: get [-s] <path>", failure.error.message)
    }

    @Test
    fun rejectsOldDeleteVersionPosition() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("delete /app 3"),
        )

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("Usage: delete [-v version] <path>", failure.error.message)
    }

    @Test
    fun rejectsUnsupportedCreateAclArgument() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("create /created value world:anyone:r"),
        )

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("Usage: create [-e] [-s] <path> [data]", failure.error.message)
    }

    @Test
    fun rejectsUnsupportedLs2Flags() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("ls2 -R /"),
        )

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("Usage: ls2 [path]", failure.error.message)
    }

    @Test
    fun rejectsUnsupportedSyncCommand() = runBlocking {
        val repository = FakeZNodeRepository()
        val result = ZkCliCommandService(repository).execute(
            readWriteProfile,
            ZkCliCommandRequest("sync /"),
        )

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("Unsupported zk command: sync. Type help for supported commands.", failure.error.message)
    }

    @Test
    fun writeCommandRequiresReadWriteConnection() = runBlocking {
        val repository = FakeZNodeRepository()
        val readOnlyProfile = readWriteProfile.copy(mode = ConnectionMode.ReadOnly)

        val result = ZkCliCommandService(repository).execute(
            readOnlyProfile,
            ZkCliCommandRequest("create /created value"),
        )

        val failure = assertIs<OperationResult.Failure>(result)
        assertEquals("This connection is read only.", failure.error.message)
        assertFalse(repository.nodes.containsKey("/created"))
    }

    private class FakeZNodeRepository : ZNodeRepository {
        var createdRequest: CreateZNodeRequest? = null
        var updatedDataRequest: UpdateZNodeDataRequest? = null
        var deleteRequest: DeleteZNodeRequest? = null
        val aclRequests = mutableListOf<UpdateZNodeAclRequest>()
        val nodes = mutableMapOf(
            "/" to ZNodeDetail(path = ZNodePath.Root),
            "/app" to ZNodeDetail(
                path = ZNodePath.requireValid("/app"),
                data = "ready".encodeToByteArray(),
                stat = ZNodeStat(version = 1, dataLength = 5),
                acl = listOf(ZNodeAcl("world", "anyone", setOf(ZNodePermission.Read))),
            ),
            "/app/config" to ZNodeDetail(
                path = ZNodePath.requireValid("/app/config"),
                data = "{}".encodeToByteArray(),
                stat = ZNodeStat(version = 1, dataLength = 2),
            ),
        )

        override fun closeConnection(connectionId: ConnectionId) = Unit

        override suspend fun loadChildren(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): OperationResult<List<ZNodeSummary>> {
            val prefix = if (path == ZNodePath.Root) "/" else path.value + "/"
            val children = nodes.keys
                .filter { it != path.value && it.startsWith(prefix) }
                .map { it.removePrefix(prefix).substringBefore("/") }
                .distinct()
                .sorted()
                .map { child -> ZNodeSummary(path.child(child)) }
            return OperationResult.Success(children)
        }

        override suspend fun loadDetail(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): OperationResult<ZNodeDetail> =
            nodes[path.value]?.let { OperationResult.Success(it) }
                ?: OperationResult.Failure(AppError.ZooKeeper("Node does not exist."))

        override suspend fun createNode(
            profile: ConnectionProfile,
            request: CreateZNodeRequest,
        ): OperationResult<ZNodePath> {
            createdRequest = request
            nodes[request.path.value] = ZNodeDetail(path = request.path, data = request.data)
            return OperationResult.Success(request.path)
        }

        override suspend fun updateData(
            profile: ConnectionProfile,
            request: UpdateZNodeDataRequest,
        ): OperationResult<ZNodeDetail> {
            updatedDataRequest = request
            val current = nodes[request.path.value] ?: return OperationResult.Failure(AppError.ZooKeeper("Node does not exist."))
            val updated = current.copy(data = request.data, stat = current.stat.copy(version = current.stat.version + 1))
            nodes[request.path.value] = updated
            return OperationResult.Success(updated)
        }

        override suspend fun previewDelete(
            profile: ConnectionProfile,
            request: DeleteZNodeRequest,
        ): OperationResult<DeleteZNodePreview> =
            OperationResult.Success(DeleteZNodePreview(request.path, request.recursive, listOf(request.path)))

        override suspend fun deleteNode(
            profile: ConnectionProfile,
            request: DeleteZNodeRequest,
        ): OperationResult<Unit> {
            deleteRequest = request
            nodes.remove(request.path.value)
            return OperationResult.Success(Unit)
        }

        override suspend fun updateAcl(
            profile: ConnectionProfile,
            request: UpdateZNodeAclRequest,
        ): OperationResult<ZNodeDetail> {
            aclRequests += request
            val current = nodes[request.path.value] ?: return OperationResult.Failure(AppError.ZooKeeper("Node does not exist."))
            val updated = current.copy(acl = request.acl)
            nodes[request.path.value] = updated
            return OperationResult.Success(updated)
        }

        override fun watch(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): Flow<ZNodeWatchEvent> =
            emptyFlow()
    }
}

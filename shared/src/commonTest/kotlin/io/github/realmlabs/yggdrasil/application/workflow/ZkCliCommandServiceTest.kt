package io.github.realmlabs.yggdrasil.application.workflow

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

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
        val nodes = mutableMapOf(
            "/" to ZNodeDetail(path = ZNodePath.Root),
            "/app" to ZNodeDetail(
                path = ZNodePath.requireValid("/app"),
                data = "ready".encodeToByteArray(),
                stat = ZNodeStat(version = 1, dataLength = 5),
                acl = listOf(ZNodeAcl("world", "anyone", setOf(ZNodePermission.Read))),
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
            nodes[request.path.value] = ZNodeDetail(path = request.path, data = request.data)
            return OperationResult.Success(request.path)
        }

        override suspend fun updateData(
            profile: ConnectionProfile,
            request: UpdateZNodeDataRequest,
        ): OperationResult<ZNodeDetail> {
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
            nodes.remove(request.path.value)
            return OperationResult.Success(Unit)
        }

        override suspend fun updateAcl(
            profile: ConnectionProfile,
            request: UpdateZNodeAclRequest,
        ): OperationResult<ZNodeDetail> {
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

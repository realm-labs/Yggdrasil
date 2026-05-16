package io.github.realmlabs.yggdrasil.application.state

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class YggdrasilStateHolderTest {
    @Test
    fun selectConnectionSetsActiveConnectionAndClearsSelection() {
        val connection = ConnectionProfile(
            id = ConnectionId("local"),
            name = "Local",
            connectionString = "localhost:2181",
            mode = ConnectionMode.ReadWrite,
        )
        val holder = YggdrasilStateHolder(
            initialState = AppState(
                connections = listOf(connection),
                nodeSelection = NodeSelectionState.SelectedPath(ZNodePath.requireValid("/app")),
            ),
        )

        holder.selectConnection(connection.id)

        assertEquals(connection.id, holder.state.activeConnectionId)
        assertIs<NodeSelectionState.None>(holder.state.nodeSelection)
        assertTrue(!holder.state.isReadOnly)
    }

    @Test
    fun setConnectionsClearsRemovedActiveConnection() {
        val holder = YggdrasilStateHolder(
            initialState = AppState(
                connections = listOf(
                    ConnectionProfile(
                        id = ConnectionId("local"),
                        name = "Local",
                        connectionString = "localhost:2181",
                    ),
                ),
                activeConnectionId = ConnectionId("local"),
                nodeSelection = NodeSelectionState.SelectedPath(ZNodePath.Root),
            ),
        )

        holder.setConnections(emptyList())

        assertNull(holder.state.activeConnectionId)
        assertIs<NodeSelectionState.None>(holder.state.nodeSelection)
    }

    @Test
    fun selectPathStoresSelectedPath() {
        val holder = YggdrasilStateHolder()
        val path = ZNodePath.requireValid("/services")

        runBlocking {
            holder.selectPath(path)
        }

        val selection = assertIs<NodeSelectionState.SelectedPath>(holder.state.nodeSelection)
        assertEquals(path, selection.path)
    }

    @Test
    fun selectPathLoadsDetailAndChildrenFromRepository() {
        val connection = ConnectionProfile(
            id = ConnectionId("local"),
            name = "Local",
            connectionString = "localhost:2181",
        )
        val servicesPath = ZNodePath.requireValid("/services")
        val childPath = servicesPath.child("api")
        val holder = YggdrasilStateHolder(
            zNodeRepository = FakeZNodeRepository(
                children = listOf(ZNodeSummary(path = childPath, childCount = 2)),
                detail = ZNodeDetail(path = servicesPath, data = "ready".encodeToByteArray()),
            ),
            initialState = AppState(
                connections = listOf(connection),
                activeConnectionId = connection.id,
            ),
        )

        runBlocking {
            holder.selectPath(servicesPath)
        }

        val selection = assertIs<NodeSelectionState.SelectedPath>(holder.state.nodeSelection)
        assertEquals(servicesPath, selection.path)
        val detail = assertIs<ZNodeDetailState.Loaded>(holder.state.nodeDetail)
        assertEquals("ready", detail.detail.data.decodeToString())
        val children = assertIs<ZNodeChildrenState.Loaded>(holder.state.znodeChildren[servicesPath])
        assertEquals(listOf(childPath), children.children.map { it.path })
    }

    @Test
    fun createNodeUsesRepositoryAndSelectsCreatedPath() {
        val connection = ConnectionProfile(
            id = ConnectionId("local"),
            name = "Local",
            connectionString = "localhost:2181",
            mode = ConnectionMode.ReadWrite,
        )
        val path = ZNodePath.requireValid("/created")
        val repository = FakeZNodeRepository(
            children = emptyList(),
            detail = ZNodeDetail(path = path),
        )
        val holder = YggdrasilStateHolder(
            zNodeRepository = repository,
            initialState = AppState(
                connections = listOf(connection),
                activeConnectionId = connection.id,
            ),
        )

        runBlocking {
            holder.createNode(
                CreateZNodeRequest(
                    path = path,
                    data = "value".encodeToByteArray(),
                    mode = ZNodeCreateMode.Persistent,
                ),
            )
        }

        assertEquals(path, repository.createdRequest?.path)
        assertEquals(path, holder.state.selectedPath)
    }

    @Test
    fun updateSelectedNodeDataUsesExpectedVersion() {
        val connection = ConnectionProfile(
            id = ConnectionId("local"),
            name = "Local",
            connectionString = "localhost:2181",
            mode = ConnectionMode.ReadWrite,
        )
        val path = ZNodePath.requireValid("/services")
        val repository = FakeZNodeRepository(
            children = emptyList(),
            detail = ZNodeDetail(path = path, stat = ZNodeStat(version = 4)),
        )
        val holder = YggdrasilStateHolder(
            zNodeRepository = repository,
            initialState = AppState(
                connections = listOf(connection),
                activeConnectionId = connection.id,
                nodeSelection = NodeSelectionState.SelectedPath(path),
            ),
        )

        runBlocking {
            holder.updateSelectedNodeData("next".encodeToByteArray(), expectedVersion = 4)
        }

        assertEquals(4, repository.updatedDataRequest?.expectedVersion)
        assertEquals("next", repository.updatedDataRequest?.data?.decodeToString())
    }

    @Test
    fun deletePreviewRequiresConfirmationForRecursiveDelete() {
        val connection = ConnectionProfile(
            id = ConnectionId("local"),
            name = "Local",
            connectionString = "localhost:2181",
            mode = ConnectionMode.ReadWrite,
        )
        val path = ZNodePath.requireValid("/services")
        val repository = FakeZNodeRepository(
            children = emptyList(),
            detail = ZNodeDetail(path = path),
        )
        val holder = YggdrasilStateHolder(
            zNodeRepository = repository,
            initialState = AppState(
                connections = listOf(connection),
                activeConnectionId = connection.id,
                nodeSelection = NodeSelectionState.SelectedPath(path),
            ),
        )

        runBlocking {
            holder.previewDeleteSelectedNode(recursive = true)
            holder.deletePreviewedNode(confirmation = "wrong")
        }

        assertNull(repository.deleteRequest)

        runBlocking {
            holder.deletePreviewedNode(confirmation = path.value)
        }

        assertEquals(path, repository.deleteRequest?.path)
        assertTrue(repository.deleteRequest?.recursive == true)
    }

    @Test
    fun updateAclUsesExpectedAversion() {
        val connection = ConnectionProfile(
            id = ConnectionId("local"),
            name = "Local",
            connectionString = "localhost:2181",
            mode = ConnectionMode.ReadWrite,
        )
        val path = ZNodePath.requireValid("/services")
        val repository = FakeZNodeRepository(
            children = emptyList(),
            detail = ZNodeDetail(path = path, stat = ZNodeStat(aversion = 7)),
        )
        val holder = YggdrasilStateHolder(
            zNodeRepository = repository,
            initialState = AppState(
                connections = listOf(connection),
                activeConnectionId = connection.id,
                nodeSelection = NodeSelectionState.SelectedPath(path),
            ),
        )
        val acl = listOf(
            ZNodeAcl(
                scheme = "world",
                id = "anyone",
                permissions = setOf(ZNodePermission.Read),
            ),
        )

        runBlocking {
            holder.updateSelectedAcl(acl, expectedAversion = 7)
        }

        assertEquals(7, repository.updatedAclRequest?.expectedAversion)
        assertEquals(acl, repository.updatedAclRequest?.acl)
    }

    private class FakeZNodeRepository(
        private val children: List<ZNodeSummary>,
        private val detail: ZNodeDetail,
    ) : ZNodeRepository {
        var createdRequest: CreateZNodeRequest? = null
        var updatedDataRequest: UpdateZNodeDataRequest? = null
        var deleteRequest: DeleteZNodeRequest? = null
        var updatedAclRequest: UpdateZNodeAclRequest? = null

        override suspend fun loadChildren(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): OperationResult<List<ZNodeSummary>> =
            OperationResult.Success(children)

        override suspend fun loadDetail(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): OperationResult<ZNodeDetail> =
            OperationResult.Success(detail)

        override suspend fun createNode(
            profile: ConnectionProfile,
            request: CreateZNodeRequest,
        ): OperationResult<ZNodePath> {
            createdRequest = request
            return OperationResult.Success(request.path)
        }

        override suspend fun updateData(
            profile: ConnectionProfile,
            request: UpdateZNodeDataRequest,
        ): OperationResult<ZNodeDetail> {
            updatedDataRequest = request
            return OperationResult.Success(detail.copy(data = request.data))
        }

        override suspend fun previewDelete(
            profile: ConnectionProfile,
            request: DeleteZNodeRequest,
        ): OperationResult<DeleteZNodePreview> =
            OperationResult.Success(
                DeleteZNodePreview(
                    rootPath = request.path,
                    recursive = request.recursive,
                    paths = listOf(request.path),
                ),
            )

        override suspend fun deleteNode(
            profile: ConnectionProfile,
            request: DeleteZNodeRequest,
        ): OperationResult<Unit> {
            deleteRequest = request
            return OperationResult.Success(Unit)
        }

        override suspend fun updateAcl(
            profile: ConnectionProfile,
            request: UpdateZNodeAclRequest,
        ): OperationResult<ZNodeDetail> {
            updatedAclRequest = request
            return OperationResult.Success(detail.copy(acl = request.acl))
        }

        override fun watch(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): Flow<ZNodeWatchEvent> =
            emptyFlow()
    }
}

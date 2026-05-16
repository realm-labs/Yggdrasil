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

    private class FakeZNodeRepository(
        private val children: List<ZNodeSummary>,
        private val detail: ZNodeDetail,
    ) : ZNodeRepository {
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

        override fun watch(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): Flow<ZNodeWatchEvent> =
            emptyFlow()
    }
}

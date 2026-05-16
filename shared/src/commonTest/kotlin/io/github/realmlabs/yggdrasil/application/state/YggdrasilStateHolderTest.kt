package io.github.realmlabs.yggdrasil.application.state

import io.github.realmlabs.yggdrasil.domain.model.ConnectionId
import io.github.realmlabs.yggdrasil.domain.model.ConnectionMode
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        holder.selectPath(path)

        val selection = assertIs<NodeSelectionState.SelectedPath>(holder.state.nodeSelection)
        assertEquals(path, selection.path)
    }
}

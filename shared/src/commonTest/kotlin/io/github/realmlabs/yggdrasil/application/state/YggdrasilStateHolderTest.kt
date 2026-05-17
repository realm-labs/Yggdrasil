package io.github.realmlabs.yggdrasil.application.state

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ConnectionProfileRepository
import io.github.realmlabs.yggdrasil.domain.repository.CredentialRepository
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

        runBlocking {
            holder.selectConnection(connection.id)
        }

        assertEquals(connection.id, holder.state.activeConnectionId)
        assertEquals(ZNodePath.Root, holder.state.selectedPath)
        assertTrue(!holder.state.isReadOnly)
    }

    @Test
    fun setConnectionsClearsRemovedActiveConnection() {
        val repository = FakeZNodeRepository(
            children = emptyList(),
            detail = ZNodeDetail(path = ZNodePath.Root),
        )
        val holder = YggdrasilStateHolder(
            zNodeRepository = repository,
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
        assertEquals(listOf(ConnectionId("local")), repository.closedConnectionIds)
    }

    @Test
    fun selectConnectionClosesPreviousZNodeSession() {
        val local = ConnectionProfile(
            id = ConnectionId("local"),
            name = "Local",
            connectionString = "localhost:2181",
        )
        val staging = ConnectionProfile(
            id = ConnectionId("staging"),
            name = "Staging",
            connectionString = "staging:2181",
        )
        val repository = FakeZNodeRepository(
            children = emptyList(),
            detail = ZNodeDetail(path = ZNodePath.Root),
        )
        val holder = YggdrasilStateHolder(
            zNodeRepository = repository,
            initialState = AppState(
                connections = listOf(local, staging),
                activeConnectionId = local.id,
            ),
        )

        runBlocking {
            holder.selectConnection(staging.id)
        }

        assertEquals(staging.id, holder.state.activeConnectionId)
        assertEquals(listOf(local.id), repository.closedConnectionIds)
    }

    @Test
    fun updateConnectionPreservesIdAndReloadsActiveConnection() {
        val connection = ConnectionProfile(
            id = ConnectionId("local"),
            name = "Local",
            connectionString = "localhost:2181",
            mode = ConnectionMode.ReadOnly,
        )
        val profileRepository = FakeConnectionProfileRepository()
        val zNodeRepository = FakeZNodeRepository(
            children = emptyList(),
            detail = ZNodeDetail(path = ZNodePath.Root),
        )
        val holder = YggdrasilStateHolder(
            connectionProfileRepository = profileRepository,
            zNodeRepository = zNodeRepository,
            initialState = AppState(
                connections = listOf(connection),
                activeConnectionId = connection.id,
                nodeSelection = NodeSelectionState.SelectedPath(ZNodePath.requireValid("/old")),
            ),
        )

        runBlocking {
            holder.updateConnection(
                connectionId = connection.id,
                draft = ConnectionProfileDraft(
                    name = "Local RW",
                    connectionString = "localhost:2182",
                    mode = ConnectionMode.ReadWrite,
                ),
            )
        }

        val saved = assertNotNull(profileRepository.savedProfile)
        assertEquals(connection.id, saved.id)
        assertEquals("Local RW", saved.name)
        assertEquals("localhost:2182", saved.connectionString)
        assertEquals(ConnectionMode.ReadWrite, saved.mode)
        assertEquals(listOf(connection.id), zNodeRepository.closedConnectionIds)
        assertEquals(ZNodePath.Root, holder.state.selectedPath)
        assertFalse(holder.state.isReadOnly)
    }

    @Test
    fun createConnectionStoresSshSecretInCredentialRepository() {
        val profileRepository = FakeConnectionProfileRepository()
        val credentialRepository = FakeCredentialRepository()
        val holder = YggdrasilStateHolder(
            connectionProfileRepository = profileRepository,
            credentialRepository = credentialRepository,
        )

        runBlocking {
            holder.createConnection(
                ConnectionProfileDraft(
                    name = "Remote",
                    connectionString = "zk.internal:2181",
                    sshTunnelEnabled = true,
                    sshHost = "bastion.example.com",
                    sshUsername = "deploy",
                    sshAuthenticationMethod = SshAuthenticationMethod.Password,
                    sshSecret = "secret",
                ),
            )
        }

        val savedProfile = assertNotNull(profileRepository.savedProfile)
        val credentialRef = assertNotNull(savedProfile.sshTunnel?.credentialRef)
        assertEquals("secret", credentialRepository.savedCredentials[credentialRef])
        assertTrue(!savedProfile.toString().contains("secret"))
    }

    @Test
    fun createConnectionStoresDigestSecretInCredentialRepository() {
        val profileRepository = FakeConnectionProfileRepository()
        val credentialRepository = FakeCredentialRepository()
        val holder = YggdrasilStateHolder(
            connectionProfileRepository = profileRepository,
            credentialRepository = credentialRepository,
        )

        runBlocking {
            holder.createConnection(
                ConnectionProfileDraft(
                    name = "Secure",
                    connectionString = "zk.internal:2181",
                    zkDigestAuthEnabled = true,
                    zkDigestUsername = "app",
                    zkDigestPassword = "secret",
                ),
            )
        }

        val savedProfile = assertNotNull(profileRepository.savedProfile)
        val security = assertIs<ConnectionSecurity.Digest>(savedProfile.security)
        assertEquals("secret", credentialRepository.savedCredentials[security.credentialRef])
        assertTrue(!savedProfile.toString().contains("secret"))
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
    fun deletePreviewCanSkipConfirmationForRecursiveDelete() {
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
            holder.deletePreviewedNode()
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
        val closedConnectionIds = mutableListOf<ConnectionId>()

        override fun closeConnection(connectionId: ConnectionId) {
            closedConnectionIds += connectionId
        }

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

    private class FakeConnectionProfileRepository : ConnectionProfileRepository {
        var savedProfile: ConnectionProfile? = null

        override suspend fun loadProfiles(): OperationResult<List<ConnectionProfile>> =
            OperationResult.Success(emptyList())

        override suspend fun saveProfile(profile: ConnectionProfile): OperationResult<Unit> {
            savedProfile = profile
            return OperationResult.Success(Unit)
        }

        override suspend fun deleteProfile(id: ConnectionId): OperationResult<Unit> =
            OperationResult.Success(Unit)
    }

    private class FakeCredentialRepository : CredentialRepository {
        val savedCredentials = mutableMapOf<String, String>()

        override suspend fun saveCredential(ref: String, secret: String): OperationResult<Unit> {
            savedCredentials[ref] = secret
            return OperationResult.Success(Unit)
        }

        override suspend fun readCredential(ref: String): OperationResult<String> =
            savedCredentials[ref]?.let { OperationResult.Success(it) }
                ?: OperationResult.Failure(AppError.Storage("Credential not found."))

        override suspend fun deleteCredential(ref: String): OperationResult<Unit> {
            savedCredentials.remove(ref)
            return OperationResult.Success(Unit)
        }
    }
}

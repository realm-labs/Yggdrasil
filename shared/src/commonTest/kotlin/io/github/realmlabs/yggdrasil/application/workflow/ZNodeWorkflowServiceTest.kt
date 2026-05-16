package io.github.realmlabs.yggdrasil.application.workflow

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ZNodeWorkflowServiceTest {
    private val profile = ConnectionProfile(
        id = ConnectionId("local"),
        name = "Local",
        connectionString = "localhost:2181",
    )

    @Test
    fun searchScansPathAndDataWithLimits() = runBlocking {
        val repository = FakeTreeRepository(
            nodes = mapOf(
                "/" to ZNodeDetail(path = ZNodePath.Root),
                "/app" to ZNodeDetail(path = ZNodePath.requireValid("/app"), data = "service-ready".encodeToByteArray()),
                "/app/cache" to ZNodeDetail(path = ZNodePath.requireValid("/app/cache"), data = "redis".encodeToByteArray()),
            ),
        )

        val result = ZNodeWorkflowService(repository).search(
            profile = profile,
            request = ZNodeSearchRequest(
                rootPath = ZNodePath.Root,
                query = "ready",
                searchPath = false,
                searchData = true,
                maxDepth = 4,
                maxNodes = 10,
            ),
        )

        val report = assertIs<OperationResult.Success<ZNodeSearchReport>>(result).value
        assertEquals(listOf("/app"), report.hits.map { it.path.value })
        assertEquals(3, report.scannedNodes)
        assertEquals(ZNodeTraversalStopReason.Completed, report.stopReason)
    }

    @Test
    fun exportUsesPerNodeBase64ForBinaryData() = runBlocking {
        val repository = FakeTreeRepository(
            nodes = mapOf(
                "/" to ZNodeDetail(path = ZNodePath.Root, data = byteArrayOf(0xC3.toByte(), 0x28)),
            ),
        )

        val result = ZNodeWorkflowService(repository).exportSubtree(
            profile = profile,
            request = ZNodeExportRequest(
                rootPath = ZNodePath.Root,
                includeAcl = false,
                dataEncoding = ZNodeDataEncoding.Text,
            ),
        )

        val report = assertIs<OperationResult.Success<ZNodeExportReport>>(result).value
        assertTrue(report.json.contains("\"dataEncoding\": \"Base64\""), report.json)
    }

    @Test
    fun importDryRunDoesNotMutateRepository() = runBlocking {
        val sourceRepository = FakeTreeRepository(
            nodes = mapOf(
                "/" to ZNodeDetail(path = ZNodePath.Root),
                "/new" to ZNodeDetail(path = ZNodePath.requireValid("/new"), data = "value".encodeToByteArray()),
            ),
        )
        val export = assertIs<OperationResult.Success<ZNodeExportReport>>(
            ZNodeWorkflowService(sourceRepository).exportSubtree(
                profile = profile,
                request = ZNodeExportRequest(rootPath = ZNodePath.requireValid("/new")),
            ),
        ).value
        val targetRepository = FakeTreeRepository(nodes = mapOf("/" to ZNodeDetail(path = ZNodePath.Root)))

        val result = ZNodeWorkflowService(targetRepository).importSubtree(
            profile = profile,
            request = ZNodeImportRequest(
                json = export.json,
                dryRun = true,
                conflictStrategy = ZNodeImportConflictStrategy.Overwrite,
            ),
        )

        val report = assertIs<OperationResult.Success<ZNodeImportReport>>(result).value
        assertEquals(listOf(ZNodeImportOperationType.Create), report.operations.map { it.type })
        assertFalse(targetRepository.nodes.containsKey("/new"))
    }

    @Test
    fun compareReportsOnlyDifferences() = runBlocking {
        val repository = RoutingTreeRepository(
            mapOf(
                ConnectionId("left") to mapOf(
                    "/" to ZNodeDetail(path = ZNodePath.Root),
                    "/same" to ZNodeDetail(path = ZNodePath.requireValid("/same"), data = "same".encodeToByteArray()),
                    "/diff" to ZNodeDetail(path = ZNodePath.requireValid("/diff"), data = "left".encodeToByteArray()),
                ),
                ConnectionId("right") to mapOf(
                    "/" to ZNodeDetail(path = ZNodePath.Root),
                    "/same" to ZNodeDetail(path = ZNodePath.requireValid("/same"), data = "same".encodeToByteArray()),
                    "/diff" to ZNodeDetail(path = ZNodePath.requireValid("/diff"), data = "right".encodeToByteArray()),
                    "/right-only" to ZNodeDetail(path = ZNodePath.requireValid("/right-only")),
                ),
            ),
        )
        val left = profile.copy(id = ConnectionId("left"))
        val right = profile.copy(id = ConnectionId("right"))

        val result = ZNodeWorkflowService(repository).compare(
            leftProfile = left,
            rightProfile = right,
            request = ZNodeCompareRequest(
                leftConnectionId = left.id,
                leftRootPath = ZNodePath.Root,
                rightConnectionId = right.id,
                rightRootPath = ZNodePath.Root,
            ),
        )

        val report = assertIs<OperationResult.Success<ZNodeCompareReport>>(result).value
        assertEquals(
            listOf(ZNodeCompareDifferenceType.DataDifferent, ZNodeCompareDifferenceType.MissingLeft),
            report.differences.map { it.type },
        )
    }

    private open class FakeTreeRepository(
        nodes: Map<String, ZNodeDetail>,
    ) : ZNodeRepository {
        val nodes: MutableMap<String, ZNodeDetail> = nodes.toMutableMap()

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
                .map { childName ->
                    val childPath = path.child(childName)
                    val childPrefix = childPath.value + "/"
                    ZNodeSummary(
                        path = childPath,
                        childCount = nodes.keys.count { it.startsWith(childPrefix) && it.removePrefix(childPrefix).substringBefore("/") == it.removePrefix(childPrefix) },
                        hasChildren = nodes.keys.any { it.startsWith(childPrefix) },
                    )
                }
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
            val updated = current.copy(data = request.data)
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

    private class RoutingTreeRepository(
        private val routeNodes: Map<ConnectionId, Map<String, ZNodeDetail>>,
    ) : FakeTreeRepository(emptyMap()) {
        private fun delegate(profile: ConnectionProfile): FakeTreeRepository =
            FakeTreeRepository(routeNodes.getValue(profile.id))

        override suspend fun loadChildren(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): OperationResult<List<ZNodeSummary>> =
            delegate(profile).loadChildren(profile, path)

        override suspend fun loadDetail(
            profile: ConnectionProfile,
            path: ZNodePath,
        ): OperationResult<ZNodeDetail> =
            delegate(profile).loadDetail(profile, path)
    }
}

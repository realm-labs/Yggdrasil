package io.github.realmlabs.yggdrasil.zookeeper

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.curator.x.async.AsyncCuratorFramework
import org.apache.curator.x.async.WatchMode
import org.apache.curator.x.async.api.DeleteOption
import org.apache.zookeeper.*
import org.apache.zookeeper.data.ACL
import org.apache.zookeeper.data.Id
import org.apache.zookeeper.data.Stat
import java.util.concurrent.CompletionStage
import kotlin.time.Duration.Companion.milliseconds

class CuratorZNodeRepository(
    private val connectionTimeoutMillis: Int = 5_000,
    private val sessionTimeoutMillis: Int = 10_000,
) : ZNodeRepository {
    override suspend fun loadChildren(
        profile: ConnectionProfile,
        path: ZNodePath,
    ): OperationResult<List<ZNodeSummary>> =
        withAsyncClient(profile) { async ->
            val names = async.children.forPath(path.value).await().sorted()
            val summaries = names.mapNotNull { name ->
                val childPath = path.child(name)
                val stat = async.checkExists().forPath(childPath.value).await() ?: return@mapNotNull null
                ZNodeSummary(
                    path = childPath,
                    childCount = stat.numChildren,
                    hasChildren = stat.numChildren > 0,
                    isEphemeral = stat.ephemeralOwner != 0L,
                )
            }
            OperationResult.Success(summaries)
        }

    override suspend fun loadDetail(
        profile: ConnectionProfile,
        path: ZNodePath,
    ): OperationResult<ZNodeDetail> =
        withAsyncClient(profile) { async -> loadDetail(async, path) }

    override suspend fun createNode(
        profile: ConnectionProfile,
        request: CreateZNodeRequest,
    ): OperationResult<ZNodePath> =
        withAsyncClient(profile) { async ->
            val createdPath = async.create()
                .withMode(request.mode.toZooKeeperCreateMode())
                .forPath(request.path.value, request.data)
                .await()
            OperationResult.Success(ZNodePath.requireValid(createdPath))
        }

    override suspend fun updateData(
        profile: ConnectionProfile,
        request: UpdateZNodeDataRequest,
    ): OperationResult<ZNodeDetail> =
        withAsyncClient(profile) { async ->
            async.setData()
                .withVersion(request.expectedVersion)
                .forPath(request.path.value, request.data)
                .await()
            loadDetail(async, request.path)
        }

    override suspend fun previewDelete(
        profile: ConnectionProfile,
        request: DeleteZNodeRequest,
    ): OperationResult<DeleteZNodePreview> =
        withAsyncClient(profile) { async ->
            val paths = if (request.recursive) {
                loadSubtreePaths(async, request.path)
            } else {
                val stat = async.checkExists().forPath(request.path.value).await()
                    ?: return@withAsyncClient OperationResult.Failure(
                        AppError.ZooKeeper("Node does not exist."),
                    )
                if (stat.numChildren > 0) {
                    return@withAsyncClient OperationResult.Failure(
                        AppError.ZooKeeper("Node has children. Enable recursive delete to preview the full delete list."),
                    )
                }
                listOf(request.path)
            }

            OperationResult.Success(
                DeleteZNodePreview(
                    rootPath = request.path,
                    recursive = request.recursive,
                    paths = paths,
                ),
            )
        }

    override suspend fun deleteNode(
        profile: ConnectionProfile,
        request: DeleteZNodeRequest,
    ): OperationResult<Unit> =
        withAsyncClient(profile) { async ->
            val delete = when {
                request.recursive && request.expectedVersion != null -> async.delete()
                    .withOptionsAndVersion(setOf(DeleteOption.deletingChildrenIfNeeded), request.expectedVersion)

                request.recursive -> async.delete()
                    .withOptions(setOf(DeleteOption.deletingChildrenIfNeeded))

                request.expectedVersion != null -> async.delete().withVersion(request.expectedVersion)
                else -> async.delete()
            }

            delete.forPath(request.path.value).await()
            OperationResult.Success(Unit)
        }

    override suspend fun updateAcl(
        profile: ConnectionProfile,
        request: UpdateZNodeAclRequest,
    ): OperationResult<ZNodeDetail> =
        withAsyncClient(profile) { async ->
            async.setACL()
                .withACL(request.acl.map(ZNodeAcl::toZooKeeperAcl), request.expectedAversion)
                .forPath(request.path.value)
                .await()
            loadDetail(async, request.path)
        }

    override fun watch(
        profile: ConnectionProfile,
        path: ZNodePath,
    ): Flow<ZNodeWatchEvent> =
        callbackFlow {
            val client = createAsyncCuratorClient(
                profile = profile,
                connectionTimeoutMillis = connectionTimeoutMillis,
                sessionTimeoutMillis = sessionTimeoutMillis,
            )

            fun mapEvent(event: WatchedEvent): ZNodeWatchEvent =
                ZNodeWatchEvent(
                    path = event.path?.let(ZNodePath::requireValid) ?: path,
                    type = event.type.toDomain(),
                    receivedAtMillis = System.currentTimeMillis(),
                )

            fun registerWatches(
                asyncClient: AsyncCuratorFramework,
                watchPath: ZNodePath,
                eventMapper: (WatchedEvent) -> ZNodeWatchEvent,
            ) {
                val watched = asyncClient.with(WatchMode.successOnly).watched()

                fun CompletionStage<WatchedEvent>.sendAndRegisterNextFromLocal() {
                    thenAccept { event ->
                        trySend(eventMapper(event))
                        registerWatches(asyncClient, watchPath, eventMapper)
                    }.exceptionally { exception ->
                        close(exception)
                        null
                    }
                }

                val existsStage = watched.checkExists().forPath(watchPath.value)
                existsStage.event().sendAndRegisterNextFromLocal()
                existsStage.thenAccept { stat ->
                    if (stat != null) {
                        watched.data.forPath(watchPath.value).event().sendAndRegisterNextFromLocal()
                        watched.children.forPath(watchPath.value).event().sendAndRegisterNextFromLocal()
                    }
                }.exceptionally {
                    null
                }
            }

            try {
                withTimeout(connectionTimeoutMillis.toLong().milliseconds) {
                    client.async.checkExists().forPath("/").await()
                }
                registerWatches(client.async, path, ::mapEvent)
            } catch (exception: Exception) {
                close(exception)
            }

            awaitClose {
                client.close()
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun <T> withAsyncClient(
        profile: ConnectionProfile,
        block: suspend (AsyncCuratorFramework) -> OperationResult<T>,
    ): OperationResult<T> =
        withContext(Dispatchers.IO) {
            val client = createAsyncCuratorClient(
                profile = profile,
                connectionTimeoutMillis = connectionTimeoutMillis,
                sessionTimeoutMillis = sessionTimeoutMillis,
            )

            try {
                when (val connectionResult = client.awaitConnected(profile, connectionTimeoutMillis)) {
                    is OperationResult.Failure -> return@withContext connectionResult
                    is OperationResult.Success -> Unit
                }
                block(client.async)
            } catch (exception: KeeperException.NodeExistsException) {
                OperationResult.Failure(
                    AppError.ZooKeeper("Node already exists.", exception.message),
                )
            } catch (exception: KeeperException.NoNodeException) {
                OperationResult.Failure(
                    AppError.ZooKeeper("Node does not exist.", exception.message),
                )
            } catch (exception: KeeperException.NotEmptyException) {
                OperationResult.Failure(
                    AppError.ZooKeeper("Node has children. Use recursive delete to remove it.", exception.message),
                )
            } catch (exception: KeeperException.BadVersionException) {
                OperationResult.Failure(
                    AppError.ZooKeeper("Node version changed. Reload before saving.", exception.message),
                )
            } catch (exception: KeeperException.InvalidACLException) {
                OperationResult.Failure(
                    AppError.ZooKeeper("Invalid ACL.", exception.message),
                )
            } catch (exception: KeeperException.NoAuthException) {
                OperationResult.Failure(
                    AppError.ZooKeeper("Not authorized to read this node.", exception.message),
                )
            } catch (exception: Exception) {
                OperationResult.Failure(
                    AppError.ZooKeeper("ZooKeeper operation failed.", exception.message),
                )
            } finally {
                client.close()
            }
        }
}

private suspend fun loadDetail(
    async: AsyncCuratorFramework,
    path: ZNodePath,
): OperationResult<ZNodeDetail> {
    val stat = Stat()
    val data = async.data.storingStatIn(stat).forPath(path.value).await() ?: byteArrayOf()
    val acl = async.acl.forPath(path.value).await().map(ACL::toDomain)

    return OperationResult.Success(
        ZNodeDetail(
            path = path,
            data = data,
            stat = stat.toDomain(),
            detectedFormat = detectDataFormat(data),
            acl = acl,
        ),
    )
}

private suspend fun loadSubtreePaths(
    async: AsyncCuratorFramework,
    path: ZNodePath,
): List<ZNodePath> {
    val children = async.children.forPath(path.value).await().sorted()
    return buildList {
        add(path)
        children.forEach { child ->
            addAll(loadSubtreePaths(async, path.child(child)))
        }
    }
}

private fun Stat.toDomain(): ZNodeStat =
    ZNodeStat(
        version = version,
        cversion = cversion,
        aversion = aversion,
        ctimeMillis = ctime,
        mtimeMillis = mtime,
        czxid = czxid,
        mzxid = mzxid,
        pzxid = pzxid,
        ephemeralOwner = ephemeralOwner,
        dataLength = dataLength,
        numChildren = numChildren,
    )

private fun ACL.toDomain(): ZNodeAcl =
    ZNodeAcl(
        scheme = id.scheme,
        id = id.id,
        permissions = buildSet {
            if (perms and ZooDefs.Perms.READ != 0) add(ZNodePermission.Read)
            if (perms and ZooDefs.Perms.WRITE != 0) add(ZNodePermission.Write)
            if (perms and ZooDefs.Perms.CREATE != 0) add(ZNodePermission.Create)
            if (perms and ZooDefs.Perms.DELETE != 0) add(ZNodePermission.Delete)
            if (perms and ZooDefs.Perms.ADMIN != 0) add(ZNodePermission.Admin)
        },
    )

private fun ZNodeAcl.toZooKeeperAcl(): ACL =
    ACL(
        permissions.toZooKeeperPerms(),
        Id(scheme, id),
    )

private fun Set<ZNodePermission>.toZooKeeperPerms(): Int =
    fold(0) { permissions, permission ->
        permissions or when (permission) {
            ZNodePermission.Read -> ZooDefs.Perms.READ
            ZNodePermission.Write -> ZooDefs.Perms.WRITE
            ZNodePermission.Create -> ZooDefs.Perms.CREATE
            ZNodePermission.Delete -> ZooDefs.Perms.DELETE
            ZNodePermission.Admin -> ZooDefs.Perms.ADMIN
        }
    }

private fun ZNodeCreateMode.toZooKeeperCreateMode(): CreateMode =
    when (this) {
        ZNodeCreateMode.Persistent -> CreateMode.PERSISTENT
        ZNodeCreateMode.Ephemeral -> CreateMode.EPHEMERAL
        ZNodeCreateMode.PersistentSequential -> CreateMode.PERSISTENT_SEQUENTIAL
        ZNodeCreateMode.EphemeralSequential -> CreateMode.EPHEMERAL_SEQUENTIAL
    }

private fun Watcher.Event.EventType.toDomain(): ZNodeWatchEventType =
    when (this) {
        Watcher.Event.EventType.NodeCreated -> ZNodeWatchEventType.NodeCreated
        Watcher.Event.EventType.NodeDeleted -> ZNodeWatchEventType.NodeDeleted
        Watcher.Event.EventType.NodeDataChanged -> ZNodeWatchEventType.NodeDataChanged
        Watcher.Event.EventType.NodeChildrenChanged -> ZNodeWatchEventType.ChildrenChanged
        Watcher.Event.EventType.None -> ZNodeWatchEventType.ConnectionStateChanged
        else -> ZNodeWatchEventType.Unknown
    }

private fun detectDataFormat(data: ByteArray): ZNodeDataFormat {
    if (data.isEmpty()) return ZNodeDataFormat.Text

    val text = data.toString(Charsets.UTF_8)
    val replacementCount = text.count { it == '\uFFFD' }
    if (replacementCount > data.size / 10) return ZNodeDataFormat.Hex

    val trimmed = text.trim()
    return when {
        trimmed.startsWith("{") && trimmed.endsWith("}") -> ZNodeDataFormat.Json
        trimmed.startsWith("[") && trimmed.endsWith("]") -> ZNodeDataFormat.Json
        else -> ZNodeDataFormat.Text
    }
}

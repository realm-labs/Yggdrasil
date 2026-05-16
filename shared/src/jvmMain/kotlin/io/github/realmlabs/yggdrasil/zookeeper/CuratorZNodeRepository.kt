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
import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher
import org.apache.zookeeper.ZooDefs
import org.apache.zookeeper.data.ACL
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
        withAsyncClient(profile) { async ->
            val stat = Stat()
            val data = async.data.storingStatIn(stat).forPath(path.value).await() ?: byteArrayOf()
            val acl = async.acl.forPath(path.value).await().map(ACL::toDomain)

            OperationResult.Success(
                ZNodeDetail(
                    path = path,
                    data = data,
                    stat = stat.toDomain(),
                    detectedFormat = detectDataFormat(data),
                    acl = acl,
                ),
            )
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
            } catch (exception: KeeperException.NoNodeException) {
                OperationResult.Failure(
                    AppError.ZooKeeper("Node does not exist.", exception.message),
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

package io.github.realmlabs.yggdrasil.application.workflow

import io.github.realmlabs.yggdrasil.domain.model.*
import io.github.realmlabs.yggdrasil.domain.repository.ZNodeRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ZNodeWorkflowService(
    private val repository: ZNodeRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun search(
        profile: ConnectionProfile,
        request: ZNodeSearchRequest,
        onProgress: (Int) -> Unit = {},
    ): OperationResult<ZNodeSearchReport> {
        val query = request.query.trim()
        if (query.isEmpty()) {
            return OperationResult.Failure(AppError.Validation("Search query cannot be empty."))
        }
        if (!request.searchPath && !request.searchData) {
            return OperationResult.Failure(AppError.Validation("Search at least path or data."))
        }

        val hits = mutableListOf<ZNodeSearchHit>()
        var scanned = 0
        var stopReason = ZNodeTraversalStopReason.Completed
        val queue = ArrayDeque<TraversalNode>()
        queue.add(TraversalNode(path = request.rootPath, depth = 0))

        try {
            while (queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if (scanned >= request.maxNodes) {
                    stopReason = ZNodeTraversalStopReason.MaxNodesReached
                    break
                }

                val node = queue.removeFirst()
                scanned += 1
                onProgress(scanned)

                val detail = if (request.searchData) {
                    when (val result = repository.loadDetail(profile, node.path)) {
                        is OperationResult.Success -> result.value
                        is OperationResult.Failure -> null
                    }
                } else {
                    null
                }

                val matchedPath = request.searchPath && node.path.value.contains(query, ignoreCase = true)
                val dataText = detail?.data?.toDisplayText()
                val matchedData = request.searchData && dataText?.contains(query, ignoreCase = true) == true
                if (matchedPath || matchedData) {
                    hits += ZNodeSearchHit(
                        path = node.path,
                        matchedPath = matchedPath,
                        matchedData = matchedData,
                        dataPreview = if (matchedData) dataText.compactPreview(query) else null,
                    )
                }

                if (node.depth >= request.maxDepth) {
                    if (hasChildren(profile, node.path)) {
                        stopReason = maxOfStopReason(stopReason, ZNodeTraversalStopReason.MaxDepthReached)
                    }
                } else {
                    when (val children = repository.loadChildren(profile, node.path)) {
                        is OperationResult.Success -> children.value.forEach { child ->
                            queue.add(TraversalNode(path = child.path, depth = node.depth + 1))
                        }

                        is OperationResult.Failure -> Unit
                    }
                }
            }
        } catch (exception: CancellationException) {
            return OperationResult.Success(
                ZNodeSearchReport(
                    request = request,
                    hits = hits,
                    scannedNodes = scanned,
                    stopReason = ZNodeTraversalStopReason.Canceled,
                ),
            )
        }

        return OperationResult.Success(
            ZNodeSearchReport(
                request = request,
                hits = hits,
                scannedNodes = scanned,
                stopReason = stopReason,
            ),
        )
    }

    suspend fun exportSubtree(
        profile: ConnectionProfile,
        request: ZNodeExportRequest,
    ): OperationResult<ZNodeExportReport> {
        val nodes = mutableListOf<ExportedZNode>()
        val queue = ArrayDeque<ZNodePath>()
        queue.add(request.rootPath)

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val path = queue.removeFirst()
            val detail = when (val result = repository.loadDetail(profile, path)) {
                is OperationResult.Success -> result.value
                is OperationResult.Failure -> return result
            }
            nodes += detail.toExportedNode(request)

            when (val children = repository.loadChildren(profile, path)) {
                is OperationResult.Success -> children.value.forEach { queue.add(it.path) }
                is OperationResult.Failure -> return children
            }
        }

        val tree = ExportedZNodeTree(
            rootPath = request.rootPath.value,
            dataEncoding = request.dataEncoding,
            includesAcl = request.includeAcl,
            nodes = nodes,
        )
        return OperationResult.Success(
            ZNodeExportReport(
                request = request,
                json = json.encodeToString(tree),
                exportedNodes = nodes.size,
            ),
        )
    }

    suspend fun importSubtree(
        profile: ConnectionProfile,
        request: ZNodeImportRequest,
    ): OperationResult<ZNodeImportReport> {
        val tree = when (val result = parseExportedTree(request.json)) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }
        val operations = mutableListOf<ZNodeImportOperation>()

        for (node in tree.nodes.sortedBy { it.path.pathDepth() }) {
            currentCoroutineContext().ensureActive()
            val path = ZNodePath.requireValid(node.path)
            val existing = repository.loadDetail(profile, path)
            val current = (existing as? OperationResult.Success)?.value
            val missing = existing is OperationResult.Failure &&
                    existing.error.message.contains("does not exist", ignoreCase = true)

            when {
                current != null && request.conflictStrategy == ZNodeImportConflictStrategy.Skip -> {
                    operations += ZNodeImportOperation(path, ZNodeImportOperationType.SkipExisting, "Skipped existing node.")
                }

                current != null && request.conflictStrategy == ZNodeImportConflictStrategy.CreateOnly -> {
                    operations += ZNodeImportOperation(
                        path = path,
                        type = ZNodeImportOperationType.Conflict,
                        message = "Node already exists; create-only import will not overwrite it.",
                    )
                }

                current != null && request.conflictStrategy == ZNodeImportConflictStrategy.Overwrite -> {
                    val data = try {
                        node.decodeData()
                    } catch (exception: IllegalArgumentException) {
                        operations += ZNodeImportOperation(path, ZNodeImportOperationType.Failed, "Invalid node data: ${exception.message}")
                        continue
                    }
                    val acl = node.acl?.map(ExportedZNodeAcl::toDomain).orEmpty()
                    if (!request.dryRun) {
                        when (val update = repository.updateData(
                            profile,
                            UpdateZNodeDataRequest(path = path, data = data, expectedVersion = current.stat.version),
                        )) {
                            is OperationResult.Success -> Unit
                            is OperationResult.Failure -> {
                                operations += ZNodeImportOperation(path, ZNodeImportOperationType.Failed, update.error.message)
                                continue
                            }
                        }
                    }
                    operations += ZNodeImportOperation(
                        path = path,
                        type = ZNodeImportOperationType.OverwriteData,
                        message = "Overwrite existing data using current version ${current.stat.version}.",
                    )

                    if (tree.includesAcl && acl.isNotEmpty()) {
                        if (!request.dryRun) {
                            when (val updateAcl = repository.updateAcl(
                                profile,
                                UpdateZNodeAclRequest(path = path, acl = acl, expectedAversion = current.stat.aversion),
                            )) {
                                is OperationResult.Success -> Unit
                                is OperationResult.Failure -> {
                                    operations += ZNodeImportOperation(path, ZNodeImportOperationType.Failed, updateAcl.error.message)
                                    continue
                                }
                            }
                        }
                        operations += ZNodeImportOperation(
                            path = path,
                            type = ZNodeImportOperationType.UpdateAcl,
                            message = "Update ACL using current aversion ${current.stat.aversion}.",
                        )
                    }
                }

                missing -> {
                    val data = try {
                        node.decodeData()
                    } catch (exception: IllegalArgumentException) {
                        operations += ZNodeImportOperation(path, ZNodeImportOperationType.Failed, "Invalid node data: ${exception.message}")
                        continue
                    }
                    if (!request.dryRun) {
                        when (val create = repository.createNode(
                            profile,
                            CreateZNodeRequest(path = path, data = data, mode = ZNodeCreateMode.Persistent),
                        )) {
                            is OperationResult.Success -> Unit
                            is OperationResult.Failure -> {
                                operations += ZNodeImportOperation(path, ZNodeImportOperationType.Failed, create.error.message)
                                continue
                            }
                        }
                        val acl = node.acl?.map(ExportedZNodeAcl::toDomain).orEmpty()
                        if (tree.includesAcl && acl.isNotEmpty()) {
                            when (val updateAcl = repository.updateAcl(
                                profile,
                                UpdateZNodeAclRequest(path = path, acl = acl, expectedAversion = 0),
                            )) {
                                is OperationResult.Success -> Unit
                                is OperationResult.Failure -> {
                                    operations += ZNodeImportOperation(path, ZNodeImportOperationType.Failed, updateAcl.error.message)
                                    continue
                                }
                            }
                        }
                    }
                    operations += ZNodeImportOperation(path, ZNodeImportOperationType.Create, "Create persistent node.")
                }

                else -> {
                    val message = (existing as OperationResult.Failure).error.message
                    operations += ZNodeImportOperation(path, ZNodeImportOperationType.Failed, message)
                }
            }
        }

        return OperationResult.Success(
            ZNodeImportReport(
                dryRun = request.dryRun,
                conflictStrategy = request.conflictStrategy,
                operations = operations,
            ),
        )
    }

    suspend fun compare(
        leftProfile: ConnectionProfile,
        rightProfile: ConnectionProfile,
        request: ZNodeCompareRequest,
        onProgress: (Int) -> Unit = {},
    ): OperationResult<ZNodeCompareReport> {
        val left = when (val result = snapshotSubtree(leftProfile, request.leftRootPath, request.maxNodes, onProgress)) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }
        val right = when (val result = snapshotSubtree(rightProfile, request.rightRootPath, request.maxNodes) { scanned ->
            onProgress(left.scannedNodes + scanned)
        }) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure -> return result
        }

        val differences = mutableListOf<ZNodeCompareDifference>()
        val allRelativePaths = (left.nodes.keys + right.nodes.keys).sorted()
        allRelativePaths.forEach { relativePath ->
            val leftDetail = left.nodes[relativePath]
            val rightDetail = right.nodes[relativePath]
            when {
                leftDetail == null && rightDetail != null -> differences += ZNodeCompareDifference(
                    relativePath = relativePath,
                    leftPath = null,
                    rightPath = rightDetail.path,
                    type = ZNodeCompareDifferenceType.MissingLeft,
                    message = "Only exists on right.",
                )

                leftDetail != null && rightDetail == null -> differences += ZNodeCompareDifference(
                    relativePath = relativePath,
                    leftPath = leftDetail.path,
                    rightPath = null,
                    type = ZNodeCompareDifferenceType.MissingRight,
                    message = "Only exists on left.",
                )

                leftDetail != null && rightDetail != null -> {
                    if (!leftDetail.data.contentEquals(rightDetail.data)) {
                        differences += ZNodeCompareDifference(
                            relativePath = relativePath,
                            leftPath = leftDetail.path,
                            rightPath = rightDetail.path,
                            type = ZNodeCompareDifferenceType.DataDifferent,
                            message = "Data differs.",
                        )
                    }
                    if (request.includeAcl && leftDetail.acl.normalized() != rightDetail.acl.normalized()) {
                        differences += ZNodeCompareDifference(
                            relativePath = relativePath,
                            leftPath = leftDetail.path,
                            rightPath = rightDetail.path,
                            type = ZNodeCompareDifferenceType.AclDifferent,
                            message = "ACL differs.",
                        )
                    }
                }
            }
        }

        return OperationResult.Success(
            ZNodeCompareReport(
                request = request,
                differences = differences,
                scannedNodes = left.scannedNodes + right.scannedNodes,
                stopReason = maxOfStopReason(left.stopReason, right.stopReason),
            ),
        )
    }

    private suspend fun snapshotSubtree(
        profile: ConnectionProfile,
        rootPath: ZNodePath,
        maxNodes: Int,
        onProgress: (Int) -> Unit,
    ): OperationResult<SubtreeSnapshot> {
        val nodes = mutableMapOf<String, ZNodeDetail>()
        val queue = ArrayDeque<ZNodePath>()
        queue.add(rootPath)
        var scanned = 0
        var stopReason = ZNodeTraversalStopReason.Completed

        try {
            while (queue.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if (scanned >= maxNodes) {
                    stopReason = ZNodeTraversalStopReason.MaxNodesReached
                    break
                }
                val path = queue.removeFirst()
                scanned += 1
                onProgress(scanned)

                val detail = when (val detailResult = repository.loadDetail(profile, path)) {
                    is OperationResult.Success -> detailResult.value
                    is OperationResult.Failure -> return detailResult
                }
                nodes[path.relativeTo(rootPath)] = detail

                when (val children = repository.loadChildren(profile, path)) {
                    is OperationResult.Success -> children.value.forEach { queue.add(it.path) }
                    is OperationResult.Failure -> return children
                }
            }
        } catch (exception: CancellationException) {
            return OperationResult.Success(SubtreeSnapshot(nodes, scanned, ZNodeTraversalStopReason.Canceled))
        }

        return OperationResult.Success(SubtreeSnapshot(nodes, scanned, stopReason))
    }

    private fun parseExportedTree(rawJson: String): OperationResult<ExportedZNodeTree> =
        try {
            val tree = json.decodeFromString<ExportedZNodeTree>(rawJson)
            when {
                tree.format != "yggdrasil.znode-tree" -> OperationResult.Failure(
                    AppError.Validation("Unsupported import format."),
                )

                tree.version != 1 -> OperationResult.Failure(
                    AppError.Validation("Unsupported import version ${tree.version}."),
                )

                tree.nodes.isEmpty() -> OperationResult.Failure(
                    AppError.Validation("Import JSON does not contain nodes."),
                )

                tree.nodes.any { !ZNodePath.isValid(it.path) } -> OperationResult.Failure(
                    AppError.Validation("Import JSON contains invalid znode paths."),
                )

                else -> OperationResult.Success(tree)
            }
        } catch (exception: SerializationException) {
            OperationResult.Failure(AppError.Validation("Import JSON cannot be parsed.", exception.message))
        } catch (exception: IllegalArgumentException) {
            OperationResult.Failure(AppError.Validation("Import JSON is invalid.", exception.message))
        }

    private suspend fun hasChildren(profile: ConnectionProfile, path: ZNodePath): Boolean =
        when (val result = repository.loadChildren(profile, path)) {
            is OperationResult.Success -> result.value.isNotEmpty()
            is OperationResult.Failure -> false
        }
}

private data class TraversalNode(
    val path: ZNodePath,
    val depth: Int,
)

private data class SubtreeSnapshot(
    val nodes: Map<String, ZNodeDetail>,
    val scannedNodes: Int,
    val stopReason: ZNodeTraversalStopReason,
)

private fun ZNodeDetail.toExportedNode(request: ZNodeExportRequest): ExportedZNode {
    val encodedData = data.encodeData(request.dataEncoding)
    return ExportedZNode(
        path = path.value,
        data = encodedData.data,
        dataEncoding = encodedData.encoding,
        stat = ExportedZNodeStat(
            version = stat.version,
            cversion = stat.cversion,
            aversion = stat.aversion,
            dataLength = stat.dataLength,
            numChildren = stat.numChildren,
            ephemeralOwner = stat.ephemeralOwner,
        ),
        acl = if (request.includeAcl) acl.map(ZNodeAcl::toExported) else null,
    )
}

private fun ZNodeAcl.toExported(): ExportedZNodeAcl =
    ExportedZNodeAcl(scheme = scheme, id = id, permissions = permissions)

private fun ExportedZNodeAcl.toDomain(): ZNodeAcl =
    ZNodeAcl(scheme = scheme, id = id, permissions = permissions)

private data class EncodedData(
    val data: String,
    val encoding: ZNodeDataEncoding,
)

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeData(encoding: ZNodeDataEncoding): EncodedData =
    when (encoding) {
        ZNodeDataEncoding.Text -> runCatching { decodeToString(throwOnInvalidSequence = true) }
            .fold(
                onSuccess = { EncodedData(it, ZNodeDataEncoding.Text) },
                onFailure = { EncodedData(Base64.Default.encode(this), ZNodeDataEncoding.Base64) },
            )

        ZNodeDataEncoding.Base64 -> EncodedData(Base64.Default.encode(this), ZNodeDataEncoding.Base64)
    }

@OptIn(ExperimentalEncodingApi::class)
private fun ExportedZNode.decodeData(): ByteArray =
    when (dataEncoding) {
        ZNodeDataEncoding.Text -> data.encodeToByteArray()
        ZNodeDataEncoding.Base64 -> Base64.Default.decode(data)
    }

private fun ByteArray.toDisplayText(): String? =
    runCatching { decodeToString(throwOnInvalidSequence = true) }.getOrNull()

private fun String.compactPreview(query: String): String {
    val index = indexOf(query, ignoreCase = true)
    val start = (index - 32).coerceAtLeast(0)
    val end = (index + query.length + 64).coerceAtMost(length)
    return substring(start, end).replace('\n', ' ').replace('\r', ' ')
}

private fun String.pathDepth(): Int =
    if (this == "/") 0 else count { it == '/' }

private fun ZNodePath.relativeTo(root: ZNodePath): String =
    when {
        value == root.value -> "/"
        root == ZNodePath.Root -> value
        value.startsWith(root.value + "/") -> value.removePrefix(root.value)
        else -> value
    }

private fun List<ZNodeAcl>.normalized(): List<String> =
    map { acl ->
        "${acl.scheme}:${acl.id}:${acl.permissions.sortedBy { it.name }.joinToString(",")}"
    }.sorted()

private fun maxOfStopReason(
    left: ZNodeTraversalStopReason,
    right: ZNodeTraversalStopReason,
): ZNodeTraversalStopReason {
    val priority = listOf(
        ZNodeTraversalStopReason.Completed,
        ZNodeTraversalStopReason.MaxDepthReached,
        ZNodeTraversalStopReason.MaxNodesReached,
        ZNodeTraversalStopReason.Canceled,
    )
    return if (priority.indexOf(left) >= priority.indexOf(right)) left else right
}

package io.github.realmlabs.yggdrasil.domain.model

import kotlinx.serialization.Serializable

data class ZNodeSearchRequest(
    val rootPath: ZNodePath,
    val query: String,
    val searchPath: Boolean = true,
    val searchData: Boolean = false,
    val maxDepth: Int = 8,
    val maxNodes: Int = 1_000,
)

data class ZNodeSearchHit(
    val path: ZNodePath,
    val matchedPath: Boolean,
    val matchedData: Boolean,
    val dataPreview: String? = null,
)

data class ZNodeSearchReport(
    val request: ZNodeSearchRequest,
    val hits: List<ZNodeSearchHit>,
    val scannedNodes: Int,
    val stopReason: ZNodeTraversalStopReason,
)

enum class ZNodeTraversalStopReason {
    Completed,
    MaxDepthReached,
    MaxNodesReached,
    Canceled,
}

enum class ZNodeDataEncoding {
    Text,
    Base64,
}

data class ZNodeExportRequest(
    val rootPath: ZNodePath,
    val includeAcl: Boolean = false,
    val dataEncoding: ZNodeDataEncoding = ZNodeDataEncoding.Text,
)

data class ZNodeExportReport(
    val request: ZNodeExportRequest,
    val json: String,
    val exportedNodes: Int,
)

enum class ZNodeImportConflictStrategy {
    Skip,
    Overwrite,
    CreateOnly,
}

data class ZNodeImportRequest(
    val json: String,
    val dryRun: Boolean,
    val conflictStrategy: ZNodeImportConflictStrategy,
)

data class ZNodeImportOperation(
    val path: ZNodePath,
    val type: ZNodeImportOperationType,
    val message: String,
)

enum class ZNodeImportOperationType {
    Create,
    OverwriteData,
    UpdateAcl,
    SkipExisting,
    Conflict,
    Failed,
}

data class ZNodeImportReport(
    val dryRun: Boolean,
    val conflictStrategy: ZNodeImportConflictStrategy,
    val operations: List<ZNodeImportOperation>,
) {
    val failureCount: Int
        get() = operations.count { it.type == ZNodeImportOperationType.Failed || it.type == ZNodeImportOperationType.Conflict }

    val appliedCount: Int
        get() = operations.count {
            it.type == ZNodeImportOperationType.Create ||
                    it.type == ZNodeImportOperationType.OverwriteData ||
                    it.type == ZNodeImportOperationType.UpdateAcl
        }
}

data class ZNodeCompareRequest(
    val leftConnectionId: ConnectionId,
    val leftRootPath: ZNodePath,
    val rightConnectionId: ConnectionId,
    val rightRootPath: ZNodePath,
    val includeAcl: Boolean = true,
    val maxNodes: Int = 1_000,
)

data class ZNodeCompareDifference(
    val relativePath: String,
    val leftPath: ZNodePath?,
    val rightPath: ZNodePath?,
    val type: ZNodeCompareDifferenceType,
    val message: String,
)

enum class ZNodeCompareDifferenceType {
    MissingLeft,
    MissingRight,
    DataDifferent,
    AclDifferent,
}

data class ZNodeCompareReport(
    val request: ZNodeCompareRequest,
    val differences: List<ZNodeCompareDifference>,
    val scannedNodes: Int,
    val stopReason: ZNodeTraversalStopReason,
)

@Serializable
data class ExportedZNodeTree(
    val format: String = "yggdrasil.znode-tree",
    val version: Int = 1,
    val rootPath: String,
    val dataEncoding: ZNodeDataEncoding,
    val includesAcl: Boolean,
    val nodes: List<ExportedZNode>,
)

@Serializable
data class ExportedZNode(
    val path: String,
    val data: String,
    val dataEncoding: ZNodeDataEncoding,
    val stat: ExportedZNodeStat,
    val acl: List<ExportedZNodeAcl>? = null,
)

@Serializable
data class ExportedZNodeStat(
    val version: Int,
    val cversion: Int,
    val aversion: Int,
    val dataLength: Int,
    val numChildren: Int,
    val ephemeralOwner: Long,
)

@Serializable
data class ExportedZNodeAcl(
    val scheme: String,
    val id: String,
    val permissions: Set<ZNodePermission>,
)

package io.github.realmlabs.yggdrasil.domain.model

data class ZNodeSummary(
    val path: ZNodePath,
    val childCount: Int = 0,
    val hasChildren: Boolean = childCount > 0,
    val isEphemeral: Boolean = false,
)

data class ZNodeDetail(
    val path: ZNodePath,
    val data: ByteArray = byteArrayOf(),
    val stat: ZNodeStat = ZNodeStat(),
    val detectedFormat: ZNodeDataFormat = ZNodeDataFormat.Unknown,
    val acl: List<ZNodeAcl> = emptyList(),
)

data class ZNodeStat(
    val version: Int = 0,
    val cversion: Int = 0,
    val aversion: Int = 0,
    val ctimeMillis: Long = 0,
    val mtimeMillis: Long = 0,
    val czxid: Long = 0,
    val mzxid: Long = 0,
    val pzxid: Long = 0,
    val ephemeralOwner: Long = 0,
    val dataLength: Int = 0,
    val numChildren: Int = 0,
)

data class ZNodeAcl(
    val scheme: String,
    val id: String,
    val permissions: Set<ZNodePermission>,
)

enum class ZNodePermission {
    Read,
    Write,
    Create,
    Delete,
    Admin,
}

enum class ZNodeDataFormat {
    Text,
    Json,
    Yaml,
    Properties,
    Base64,
    Hex,
    Unknown,
}

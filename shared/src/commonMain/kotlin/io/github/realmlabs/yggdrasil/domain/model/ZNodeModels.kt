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
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZNodeDetail) return false

        if (path != other.path) return false
        if (!data.contentEquals(other.data)) return false
        if (stat != other.stat) return false
        if (detectedFormat != other.detectedFormat) return false
        if (acl != other.acl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + stat.hashCode()
        result = 31 * result + detectedFormat.hashCode()
        result = 31 * result + acl.hashCode()
        return result
    }
}

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

data class CreateZNodeRequest(
    val path: ZNodePath,
    val data: ByteArray,
    val mode: ZNodeCreateMode,
)

enum class ZNodeCreateMode {
    Persistent,
    Ephemeral,
    PersistentSequential,
    EphemeralSequential,
}

data class UpdateZNodeDataRequest(
    val path: ZNodePath,
    val data: ByteArray,
    val expectedVersion: Int,
)

data class DeleteZNodeRequest(
    val path: ZNodePath,
    val recursive: Boolean,
    val expectedVersion: Int? = null,
)

data class DeleteZNodePreview(
    val rootPath: ZNodePath,
    val recursive: Boolean,
    val paths: List<ZNodePath>,
)

data class UpdateZNodeAclRequest(
    val path: ZNodePath,
    val acl: List<ZNodeAcl>,
    val expectedAversion: Int,
)

data class ZNodeWatchEvent(
    val path: ZNodePath,
    val type: ZNodeWatchEventType,
    val receivedAtMillis: Long,
)

enum class ZNodeWatchEventType {
    NodeCreated,
    NodeDeleted,
    NodeDataChanged,
    ChildrenChanged,
    ConnectionStateChanged,
    Unknown,
}

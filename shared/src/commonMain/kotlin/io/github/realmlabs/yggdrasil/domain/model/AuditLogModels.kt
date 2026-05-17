package io.github.realmlabs.yggdrasil.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AuditLogEntry(
    val timestampMillis: Long,
    val action: AuditAction,
    val connectionId: String?,
    val connectionName: String?,
    val path: String?,
    val summary: String,
)

@Serializable
enum class AuditAction {
    Connect,
    Create,
    UpdateData,
    Delete,
    UpdateAcl,
    Import,
    Export,
    ZkCli,
}

package io.github.realmlabs.yggdrasil.domain.repository

import io.github.realmlabs.yggdrasil.domain.model.AuditLogEntry
import io.github.realmlabs.yggdrasil.domain.model.OperationResult

interface AuditLogRepository {
    suspend fun loadRecent(limit: Int = 50): OperationResult<List<AuditLogEntry>>

    suspend fun append(entry: AuditLogEntry): OperationResult<List<AuditLogEntry>>
}

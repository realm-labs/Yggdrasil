package io.github.realmlabs.yggdrasil.storage

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.AuditLogEntry
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.repository.AuditLogRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class LocalAuditLogRepository(
    private val file: Path,
    private val maxEntries: Int = 500,
) : AuditLogRepository {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    private val serializer = ListSerializer(AuditLogEntry.serializer())

    override suspend fun loadRecent(limit: Int): OperationResult<List<AuditLogEntry>> =
        when (val loaded = loadAll()) {
            is OperationResult.Success -> OperationResult.Success(loaded.value.takeLast(limit).reversed())
            is OperationResult.Failure -> loaded
        }

    override suspend fun append(entry: AuditLogEntry): OperationResult<List<AuditLogEntry>> =
        try {
            Files.createDirectories(file.parent)
            val entries = when (val loaded = loadAll()) {
                is OperationResult.Success -> loaded.value
                is OperationResult.Failure -> return loaded
            }
            val next = (entries + entry).takeLast(maxEntries)
            Files.writeString(file, json.encodeToString(serializer, next))
            OperationResult.Success(next.takeLast(50).reversed())
        } catch (exception: Exception) {
            OperationResult.Failure(AppError.Storage("Could not write audit log.", exception.message))
        }

    private fun loadAll(): OperationResult<List<AuditLogEntry>> =
        try {
            if (!Files.exists(file)) return OperationResult.Success(emptyList())
            OperationResult.Success(json.decodeFromString(serializer, Files.readString(file)))
        } catch (exception: SerializationException) {
            OperationResult.Failure(AppError.Storage("Audit log is not valid JSON.", exception.message))
        } catch (exception: Exception) {
            OperationResult.Failure(AppError.Storage("Could not read audit log.", exception.message))
        }
}

package io.github.realmlabs.yggdrasil.storage

import io.github.realmlabs.yggdrasil.application.state.AppSettings
import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.repository.AppSettingsRepository
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class LocalAppSettingsRepository(
    private val file: Path,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : AppSettingsRepository {
    override suspend fun loadSettings(): OperationResult<AppSettings> =
        try {
            if (!file.exists()) {
                OperationResult.Success(AppSettings())
            } else {
                OperationResult.Success(json.decodeFromString<AppSettings>(file.readText()))
            }
        } catch (exception: SerializationException) {
            OperationResult.Failure(
                AppError.Storage(
                    message = "Settings store is not valid JSON.",
                    cause = exception.message,
                ),
            )
        } catch (exception: IOException) {
            OperationResult.Failure(
                AppError.Storage(
                    message = "Could not read settings.",
                    cause = exception.message,
                ),
            )
        }

    override suspend fun saveSettings(settings: AppSettings): OperationResult<Unit> =
        try {
            Files.createDirectories(file.parent)
            file.writeText(json.encodeToString(AppSettings.serializer(), settings))
            OperationResult.Success(Unit)
        } catch (exception: IOException) {
            OperationResult.Failure(
                AppError.Storage(
                    message = "Could not save settings.",
                    cause = exception.message,
                ),
            )
        }
}

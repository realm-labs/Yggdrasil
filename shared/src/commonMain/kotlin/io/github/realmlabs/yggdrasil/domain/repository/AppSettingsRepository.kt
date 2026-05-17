package io.github.realmlabs.yggdrasil.domain.repository

import io.github.realmlabs.yggdrasil.application.state.AppSettings
import io.github.realmlabs.yggdrasil.domain.model.OperationResult

interface AppSettingsRepository {
    suspend fun loadSettings(): OperationResult<AppSettings>
    suspend fun saveSettings(settings: AppSettings): OperationResult<Unit>
}

package io.github.realmlabs.yggdrasil.domain.repository

import io.github.realmlabs.yggdrasil.domain.model.ConnectionId
import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.OperationResult

interface ConnectionProfileRepository {
    suspend fun loadProfiles(): OperationResult<List<ConnectionProfile>>

    suspend fun saveProfile(profile: ConnectionProfile): OperationResult<Unit>

    suspend fun deleteProfile(id: ConnectionId): OperationResult<Unit>
}

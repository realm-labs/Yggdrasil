package io.github.realmlabs.yggdrasil.domain.repository

import io.github.realmlabs.yggdrasil.domain.model.ConnectionProfile
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.model.ZNodeDetail
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath
import io.github.realmlabs.yggdrasil.domain.model.ZNodeSummary
import io.github.realmlabs.yggdrasil.domain.model.ZNodeWatchEvent
import kotlinx.coroutines.flow.Flow

interface ZNodeRepository {
    suspend fun loadChildren(
        profile: ConnectionProfile,
        path: ZNodePath,
    ): OperationResult<List<ZNodeSummary>>

    suspend fun loadDetail(
        profile: ConnectionProfile,
        path: ZNodePath,
    ): OperationResult<ZNodeDetail>

    fun watch(
        profile: ConnectionProfile,
        path: ZNodePath,
    ): Flow<ZNodeWatchEvent>
}

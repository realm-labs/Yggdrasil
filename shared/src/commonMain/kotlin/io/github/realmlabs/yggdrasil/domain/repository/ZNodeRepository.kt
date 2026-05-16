package io.github.realmlabs.yggdrasil.domain.repository

import io.github.realmlabs.yggdrasil.domain.model.*
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

    suspend fun createNode(
        profile: ConnectionProfile,
        request: CreateZNodeRequest,
    ): OperationResult<ZNodePath>

    suspend fun updateData(
        profile: ConnectionProfile,
        request: UpdateZNodeDataRequest,
    ): OperationResult<ZNodeDetail>

    suspend fun previewDelete(
        profile: ConnectionProfile,
        request: DeleteZNodeRequest,
    ): OperationResult<DeleteZNodePreview>

    suspend fun deleteNode(
        profile: ConnectionProfile,
        request: DeleteZNodeRequest,
    ): OperationResult<Unit>

    suspend fun updateAcl(
        profile: ConnectionProfile,
        request: UpdateZNodeAclRequest,
    ): OperationResult<ZNodeDetail>

    fun watch(
        profile: ConnectionProfile,
        path: ZNodePath,
    ): Flow<ZNodeWatchEvent>
}

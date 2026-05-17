package io.github.realmlabs.yggdrasil.domain.repository

import io.github.realmlabs.yggdrasil.domain.model.OperationResult

interface SshCredentialRepository {
    suspend fun saveCredential(ref: String, secret: String): OperationResult<Unit>

    suspend fun deleteCredential(ref: String): OperationResult<Unit>
}

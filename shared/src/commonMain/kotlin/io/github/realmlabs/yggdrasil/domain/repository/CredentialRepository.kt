package io.github.realmlabs.yggdrasil.domain.repository

import io.github.realmlabs.yggdrasil.domain.model.OperationResult

interface CredentialRepository {
    suspend fun saveCredential(ref: String, secret: String): OperationResult<Unit>

    suspend fun readCredential(ref: String): OperationResult<String>

    suspend fun deleteCredential(ref: String): OperationResult<Unit>
}

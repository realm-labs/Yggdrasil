package io.github.realmlabs.yggdrasil.storage

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import io.github.realmlabs.yggdrasil.domain.repository.SshCredentialRepository
import java.io.IOException
import java.util.concurrent.TimeUnit

class MacOsKeychainSshCredentialRepository(
    private val serviceName: String = SshKeychainServiceName,
) : SshCredentialRepository {
    override suspend fun saveCredential(ref: String, secret: String): OperationResult<Unit> {
        if (!isMacOs()) {
            return OperationResult.Failure(AppError.Storage("SSH credential storage requires macOS Keychain."))
        }
        return runSecurityCommand(
            listOf(
                "add-generic-password",
                "-s",
                serviceName,
                "-a",
                ref,
                "-w",
                secret,
                "-U",
            ),
            failureMessage = "Could not save SSH credential.",
        )
    }

    override suspend fun deleteCredential(ref: String): OperationResult<Unit> {
        if (!isMacOs()) return OperationResult.Success(Unit)
        val result = runSecurityCommand(
            listOf(
                "delete-generic-password",
                "-s",
                serviceName,
                "-a",
                ref,
            ),
            failureMessage = "Could not delete SSH credential.",
        )
        return when {
            result is OperationResult.Success -> result
            result is OperationResult.Failure && result.error.cause?.contains("could not be found") == true -> {
                OperationResult.Success(Unit)
            }
            else -> result
        }
    }

    private fun runSecurityCommand(
        arguments: List<String>,
        failureMessage: String,
    ): OperationResult<Unit> =
        try {
            val process = ProcessBuilder(listOf("/usr/bin/security") + arguments)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(10, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText().trim()
            when {
                !completed -> {
                    process.destroyForcibly()
                    OperationResult.Failure(AppError.Storage(failureMessage, "security command timed out."))
                }
                process.exitValue() == 0 -> OperationResult.Success(Unit)
                else -> OperationResult.Failure(AppError.Storage(failureMessage, output.ifBlank { null }))
            }
        } catch (exception: IOException) {
            OperationResult.Failure(AppError.Storage(failureMessage, exception.message))
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            OperationResult.Failure(AppError.Storage(failureMessage, exception.message))
        }

    private fun isMacOs(): Boolean =
        System.getProperty("os.name").contains("Mac", ignoreCase = true)
}

const val SshKeychainServiceName = "io.github.realmlabs.yggdrasil.ssh"

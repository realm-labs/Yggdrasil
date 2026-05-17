package io.github.realmlabs.yggdrasil.platform

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.OperationResult
import java.awt.FileDialog
import java.awt.Frame
import java.awt.HeadlessException
import java.io.File

actual fun chooseFilePath(
    title: String,
    currentPath: String?,
): String? =
    try {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        val currentFile = currentPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        currentFile?.parentFile?.takeIf { it.exists() }?.let { parent ->
            dialog.directory = parent.absolutePath
        }
        currentFile?.name?.takeIf { it.isNotBlank() }?.let { name ->
            dialog.file = name
        }
        dialog.isMultipleMode = false
        dialog.isVisible = true

        val file = dialog.file ?: return null
        File(dialog.directory ?: "", file).absolutePath
    } catch (_: HeadlessException) {
        null
    }

actual fun chooseSaveFilePath(
    title: String,
    currentPath: String?,
): String? =
    try {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        val currentFile = currentPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        currentFile?.parentFile?.takeIf { it.exists() }?.let { parent ->
            dialog.directory = parent.absolutePath
        }
        currentFile?.name?.takeIf { it.isNotBlank() }?.let { name ->
            dialog.file = name
        }
        dialog.isVisible = true

        val file = dialog.file ?: return null
        File(dialog.directory ?: "", file).absolutePath
    } catch (_: HeadlessException) {
        null
    }

actual fun readTextFile(path: String): OperationResult<String> =
    try {
        OperationResult.Success(File(path).readText())
    } catch (exception: Exception) {
        OperationResult.Failure(AppError.Storage("Could not read file.", exception.message))
    }

actual fun writeTextFile(path: String, text: String): OperationResult<Unit> =
    try {
        File(path).writeText(text)
        OperationResult.Success(Unit)
    } catch (exception: Exception) {
        OperationResult.Failure(AppError.Storage("Could not write file.", exception.message))
    }

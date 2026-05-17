package io.github.realmlabs.yggdrasil.platform

import java.awt.FileDialog
import java.awt.HeadlessException
import java.awt.Frame
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

package io.github.realmlabs.yggdrasil.platform

import io.github.realmlabs.yggdrasil.domain.model.OperationResult

expect fun chooseFilePath(
    title: String,
    currentPath: String? = null,
): String?

expect fun chooseSaveFilePath(
    title: String,
    currentPath: String? = null,
): String?

expect fun readTextFile(path: String): OperationResult<String>

expect fun writeTextFile(path: String, text: String): OperationResult<Unit>

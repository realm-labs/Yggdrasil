package io.github.realmlabs.yggdrasil.platform

expect fun chooseFilePath(
    title: String,
    currentPath: String? = null,
): String?

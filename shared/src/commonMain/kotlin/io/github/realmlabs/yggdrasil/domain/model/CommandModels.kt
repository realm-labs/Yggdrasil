package io.github.realmlabs.yggdrasil.domain.model

data class ZkCliCommandRequest(
    val commandLine: String,
)

data class ZkCliCommandResult(
    val commandLine: String,
    val output: String,
)

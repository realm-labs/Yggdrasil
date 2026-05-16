package io.github.realmlabs.yggdrasil.domain.model

data class PendingChange(
    val targetPath: ZNodePath,
    val type: PendingChangeType,
    val summary: String,
    val risk: ChangeRisk = ChangeRisk.Normal,
)

enum class PendingChangeType {
    CreateNode,
    UpdateData,
    DeleteNode,
    UpdateAcl,
    ImportSubtree,
}

enum class ChangeRisk {
    Low,
    Normal,
    High,
    Destructive,
}

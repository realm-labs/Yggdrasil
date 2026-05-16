package io.github.realmlabs.yggdrasil.application.state

import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.DeleteZNodePreview
import io.github.realmlabs.yggdrasil.domain.model.ZNodeDetail
import io.github.realmlabs.yggdrasil.domain.model.ZNodePath
import io.github.realmlabs.yggdrasil.domain.model.ZNodeSummary
import io.github.realmlabs.yggdrasil.domain.model.ZNodeWatchEvent


sealed interface ConnectionRuntimeStatus {
    data object Disconnected : ConnectionRuntimeStatus
    data object Connecting : ConnectionRuntimeStatus
    data object Connected : ConnectionRuntimeStatus
    data object Suspended : ConnectionRuntimeStatus
    data object Lost : ConnectionRuntimeStatus
    data class Failed(val error: AppError) : ConnectionRuntimeStatus
}

sealed interface NodeSelectionState {
    data object None : NodeSelectionState
    data class SelectedPath(val path: ZNodePath) : NodeSelectionState
    data class Loading(val path: ZNodePath) : NodeSelectionState
    data class Failed(val path: ZNodePath, val error: AppError) : NodeSelectionState
}

sealed interface ZNodeChildrenState {
    data object Unloaded : ZNodeChildrenState
    data object Loading : ZNodeChildrenState
    data class Loaded(val children: List<ZNodeSummary>) : ZNodeChildrenState
    data class Failed(val error: AppError) : ZNodeChildrenState
}

sealed interface ZNodeDetailState {
    data object None : ZNodeDetailState
    data class Loading(val path: ZNodePath) : ZNodeDetailState
    data class Loaded(val detail: ZNodeDetail) : ZNodeDetailState
    data class Failed(val path: ZNodePath, val error: AppError) : ZNodeDetailState
}

sealed interface DeletePreviewState {
    data object None : DeletePreviewState
    data class Loading(val path: ZNodePath, val recursive: Boolean) : DeletePreviewState
    data class Loaded(val preview: DeleteZNodePreview) : DeletePreviewState
    data class Failed(val path: ZNodePath, val error: AppError) : DeletePreviewState
}

data class ZNodeWatchState(
    val watchedPath: ZNodePath? = null,
    val lastEvent: ZNodeWatchEvent? = null,
    val error: AppError? = null,
) {
    val isRegistered: Boolean
        get() = watchedPath != null && error == null
}

enum class ThemePreference {
    System,
    Light,
    Dark,
}

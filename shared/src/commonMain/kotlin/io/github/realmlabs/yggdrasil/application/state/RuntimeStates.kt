package io.github.realmlabs.yggdrasil.application.state

import io.github.realmlabs.yggdrasil.domain.model.*


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

sealed interface ZNodeSearchState {
    data object Idle : ZNodeSearchState
    data class Running(val request: ZNodeSearchRequest, val scannedNodes: Int = 0) : ZNodeSearchState
    data class Loaded(val report: ZNodeSearchReport) : ZNodeSearchState
    data class Failed(val request: ZNodeSearchRequest?, val error: AppError) : ZNodeSearchState
}

sealed interface ZNodeExportState {
    data object Idle : ZNodeExportState
    data class Running(val request: ZNodeExportRequest) : ZNodeExportState
    data class Loaded(val report: ZNodeExportReport) : ZNodeExportState
    data class Failed(val request: ZNodeExportRequest?, val error: AppError) : ZNodeExportState
}

sealed interface ZNodeImportState {
    data object Idle : ZNodeImportState
    data class Running(val request: ZNodeImportRequest) : ZNodeImportState
    data class Loaded(val report: ZNodeImportReport) : ZNodeImportState
    data class Failed(val request: ZNodeImportRequest?, val error: AppError) : ZNodeImportState
}

sealed interface ZNodeCompareState {
    data object Idle : ZNodeCompareState
    data class Running(val request: ZNodeCompareRequest, val scannedNodes: Int = 0) : ZNodeCompareState
    data class Loaded(val report: ZNodeCompareReport) : ZNodeCompareState
    data class Failed(val request: ZNodeCompareRequest?, val error: AppError) : ZNodeCompareState
}

enum class ThemePreference {
    System,
    Light,
    Dark,
}

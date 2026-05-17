package io.github.realmlabs.yggdrasil.application.state

import io.github.realmlabs.yggdrasil.domain.model.*
import kotlinx.serialization.Serializable


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
    val events: List<ZNodeWatchEvent> = emptyList(),
    val error: AppError? = null,
    val enabled: Boolean = false,
) {
    val isRegistered: Boolean
        get() = enabled && watchedPath != null && error == null
}

sealed interface StatusMessage {
    data object Ready : StatusMessage
    data object LoadingConnections : StatusMessage
    data object NoSavedConnections : StatusMessage
    data class LoadedConnections(val count: Int) : StatusMessage
    data class SelectedConnection(val name: String) : StatusMessage
    data class SavedConnection(val name: String) : StatusMessage
    data class UpdatedConnection(val name: String) : StatusMessage
    data class DeletedConnection(val name: String) : StatusMessage
    data class TestingConnection(val name: String) : StatusMessage
    data class ConnectedTo(val name: String) : StatusMessage
    data class DisconnectedFrom(val name: String) : StatusMessage
    data class ReconnectingTo(val name: String) : StatusMessage
    data class LoadingPath(val path: ZNodePath) : StatusMessage
    data class LoadedPath(val path: ZNodePath) : StatusMessage
    data class LoadingChildren(val path: ZNodePath) : StatusMessage
    data class LoadedChildren(val count: Int, val path: ZNodePath) : StatusMessage
    data class CreatingNode(val path: ZNodePath) : StatusMessage
    data class CreatedNode(val path: ZNodePath) : StatusMessage
    data class SavingData(val path: ZNodePath) : StatusMessage
    data class SavedData(val path: ZNodePath) : StatusMessage
    data class PreviewingDelete(val path: ZNodePath) : StatusMessage
    data class PreviewedDelete(val count: Int) : StatusMessage
    data class DeletingNode(val path: ZNodePath) : StatusMessage
    data class DeletedNode(val path: ZNodePath) : StatusMessage
    data class SavingAcl(val path: ZNodePath) : StatusMessage
    data class SavedAcl(val path: ZNodePath) : StatusMessage
    data class SearchingFrom(val path: ZNodePath) : StatusMessage
    data class SearchingProgress(val path: ZNodePath, val scanned: Int) : StatusMessage
    data class SearchFound(val hits: Int, val scanned: Int) : StatusMessage
    data class SearchCanceled(val scanned: Int) : StatusMessage
    data class Exporting(val path: ZNodePath) : StatusMessage
    data class Exported(val count: Int, val path: ZNodePath) : StatusMessage
    data object PlanningImport : StatusMessage
    data object ImportingZNodes : StatusMessage
    data class ImportDryRunPlanned(val operations: Int) : StatusMessage
    data class ImportCompleted(val applied: Int, val failed: Int) : StatusMessage
    data class ComparingConnections(val left: String, val right: String) : StatusMessage
    data class ComparingProgress(val scanned: Int) : StatusMessage
    data class CompareFound(val differences: Int, val scanned: Int) : StatusMessage
    data class CompareCanceled(val scanned: Int) : StatusMessage
    data object RunningZkCommand : StatusMessage
    data object ZkCommandCompleted : StatusMessage
    data class WatchEvent(val type: String, val path: ZNodePath) : StatusMessage
    data class WatchEnabled(val path: ZNodePath) : StatusMessage
    data object WatchDisabled : StatusMessage
    data object SelectionCleared : StatusMessage
    data object SettingsUpdated : StatusMessage
    data class LoadingDetail(val path: ZNodePath) : StatusMessage
    data class Error(val error: AppError) : StatusMessage
}

sealed interface ZkCliState {
    data object Idle : ZkCliState
    data class Running(val request: ZkCliCommandRequest) : ZkCliState
    data class Loaded(val result: ZkCliCommandResult) : ZkCliState
    data class Failed(val request: ZkCliCommandRequest, val error: AppError) : ZkCliState
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

enum class TerminalThemePreference {
    Auto,
    Light,
    Dark,
}

enum class AppLanguage(val localeTag: String) {
    English("en"),
    Chinese("zh-CN"),
}

@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.English,
    val themePreference: ThemePreference = ThemePreference.System,
    val startAtRoot: Boolean = true,
    val autoWatchSelectedNode: Boolean = true,
    val defaultSearchPath: Boolean = true,
    val defaultSearchData: Boolean = true,
    val inspectorExpandedByDefault: Boolean = true,
    val embeddedTerminalEnabled: Boolean = true,
    val terminalExpandedByDefault: Boolean = true,
    val terminalFontSize: Int = 13,
    val terminalThemePreference: TerminalThemePreference = TerminalThemePreference.Auto,
    val terminalShowTimestamps: Boolean = true,
    val clearTerminalOnConnectionChange: Boolean = false,
    val workspace: AppWorkspaceState = AppWorkspaceState(),
)

@Serializable
data class AppWorkspaceState(
    val favoritePaths: Map<String, List<String>> = emptyMap(),
    val recentPaths: Map<String, List<String>> = emptyMap(),
)

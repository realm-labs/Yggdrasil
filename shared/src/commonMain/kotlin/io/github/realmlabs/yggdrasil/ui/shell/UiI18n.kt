package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.runtime.Composable
import io.github.realmlabs.yggdrasil.application.state.StatusMessage
import io.github.realmlabs.yggdrasil.domain.model.AppError
import io.github.realmlabs.yggdrasil.domain.model.ZNodeCompareDifference
import io.github.realmlabs.yggdrasil.domain.model.ZNodeCompareDifferenceType
import io.github.realmlabs.yggdrasil.domain.model.ZNodeImportOperation
import io.github.realmlabs.yggdrasil.domain.model.ZNodeImportOperationType
import io.github.realmlabs.yggdrasil.domain.model.ZNodeTraversalStopReason
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.Res
import yggdrasil.shared.generated.resources.*

@Composable
internal fun StatusMessage.localized(): String {
    val strings = Res.string
    return when (this) {
        StatusMessage.Ready -> stringResource(strings.status_ready)
        StatusMessage.LoadingConnections -> stringResource(strings.connection_loading_title)
        StatusMessage.NoSavedConnections -> stringResource(strings.connection_none_saved)
        is StatusMessage.LoadedConnections -> stringResource(strings.status_loaded_connections, count)
        is StatusMessage.SelectedConnection -> stringResource(strings.status_selected_connection, name)
        is StatusMessage.SavedConnection -> stringResource(strings.status_saved_connection, name)
        is StatusMessage.UpdatedConnection -> stringResource(strings.status_updated_connection, name)
        is StatusMessage.DeletedConnection -> stringResource(strings.status_deleted_connection, name)
        is StatusMessage.TestingConnection -> stringResource(strings.status_testing_connection, name)
        is StatusMessage.ConnectedTo -> stringResource(strings.status_connected_to, name)
        is StatusMessage.LoadingPath -> stringResource(strings.status_loading_path, path.value)
        is StatusMessage.LoadedPath -> stringResource(strings.status_loaded_path, path.value)
        is StatusMessage.LoadingChildren -> stringResource(strings.status_loading_children, path.value)
        is StatusMessage.LoadedChildren -> stringResource(strings.status_loaded_children, count, path.value)
        is StatusMessage.CreatingNode -> stringResource(strings.status_creating_node, path.value)
        is StatusMessage.CreatedNode -> stringResource(strings.status_created_node, path.value)
        is StatusMessage.SavingData -> stringResource(strings.status_saving_data, path.value)
        is StatusMessage.SavedData -> stringResource(strings.status_saved_data, path.value)
        is StatusMessage.PreviewingDelete -> stringResource(strings.status_previewing_delete, path.value)
        is StatusMessage.PreviewedDelete -> stringResource(strings.status_previewed_delete, count)
        is StatusMessage.DeletingNode -> stringResource(strings.status_deleting_node, path.value)
        is StatusMessage.DeletedNode -> stringResource(strings.status_deleted_node, path.value)
        is StatusMessage.SavingAcl -> stringResource(strings.status_saving_acl, path.value)
        is StatusMessage.SavedAcl -> stringResource(strings.status_saved_acl, path.value)
        is StatusMessage.SearchingFrom -> stringResource(strings.status_searching_from, path.value)
        is StatusMessage.SearchingProgress -> stringResource(strings.status_searching_progress, path.value, scanned)
        is StatusMessage.SearchFound -> stringResource(strings.status_search_found, hits, scanned)
        is StatusMessage.SearchCanceled -> stringResource(strings.status_search_canceled, scanned)
        is StatusMessage.Exporting -> stringResource(strings.status_exporting, path.value)
        is StatusMessage.Exported -> stringResource(strings.status_exported, count, path.value)
        StatusMessage.PlanningImport -> stringResource(strings.status_planning_import)
        StatusMessage.ImportingZNodes -> stringResource(strings.status_importing_znodes)
        is StatusMessage.ImportDryRunPlanned -> stringResource(strings.status_import_dry_run_planned, operations)
        is StatusMessage.ImportCompleted -> stringResource(strings.status_import_completed, applied, failed)
        is StatusMessage.ComparingConnections -> stringResource(strings.status_comparing_connections, left, right)
        is StatusMessage.ComparingProgress -> stringResource(strings.status_comparing_progress, scanned)
        is StatusMessage.CompareFound -> stringResource(strings.status_compare_found, differences, scanned)
        is StatusMessage.CompareCanceled -> stringResource(strings.status_compare_canceled, scanned)
        StatusMessage.RunningZkCommand -> stringResource(strings.status_running_zk_command)
        StatusMessage.ZkCommandCompleted -> stringResource(strings.status_zk_command_completed)
        is StatusMessage.WatchEvent -> stringResource(strings.status_watch_event, type, path.value)
        StatusMessage.SelectionCleared -> stringResource(strings.status_selection_cleared)
        StatusMessage.SettingsUpdated -> stringResource(strings.status_settings_updated)
        is StatusMessage.LoadingDetail -> stringResource(strings.status_loading_detail, path.value)
        is StatusMessage.Error -> error.localized()
    }
}

@Composable
internal fun AppError.localized(): String {
    val strings = Res.string
    val message = message
    return when {
        message == "Connection name is required." -> stringResource(strings.error_connection_name_required)
        message == "ZooKeeper connection string is required." -> stringResource(strings.error_zk_connection_required)
        message == "Connection profile is not valid." -> stringResource(strings.error_connection_profile_invalid)
        message == "SSH host is required." -> stringResource(strings.error_ssh_host_required)
        message == "SSH username is required." -> stringResource(strings.error_ssh_username_required)
        message == "SSH port must be between 1 and 65535." -> stringResource(strings.error_ssh_port_invalid)
        message.startsWith("Invalid ZooKeeper path: ") -> {
            stringResource(strings.error_invalid_zk_path, message.removePrefix("Invalid ZooKeeper path: "))
        }
        message == "Command cannot be empty." -> stringResource(strings.error_command_empty)
        message.startsWith("Unsupported zk command: ") && message.endsWith(". Type help for supported commands.") -> {
            val command = message
                .removePrefix("Unsupported zk command: ")
                .removeSuffix(". Type help for supported commands.")
            stringResource(strings.error_unsupported_zk_command, command)
        }
        message == "Deleting the root znode is not supported." -> stringResource(strings.error_delete_root_unsupported)
        message == "Usage: setAcl <path> <scheme:id:perms[,scheme:id:perms]>" -> stringResource(strings.error_set_acl_usage)
        message == "This connection is read only." -> stringResource(strings.error_read_only_connection)
        message == "Search query cannot be empty." -> stringResource(strings.error_search_query_empty)
        message == "Search at least path or data." -> stringResource(strings.error_search_target_required)
        message == "Cannot create the root znode." -> stringResource(strings.error_cannot_create_root)
        message.startsWith("Type ") && message.endsWith(" to confirm deletion.") -> {
            val path = message.removePrefix("Type ").removeSuffix(" to confirm deletion.")
            stringResource(strings.error_type_path_to_confirm_deletion, path)
        }
        message == "ACL entries require scheme, id, and at least one permission." -> stringResource(strings.dialog_acl_validation)
        message == "Select a ZooKeeper connection first." -> stringResource(strings.error_select_connection_first)
        message == "Select a root path to export." -> stringResource(strings.error_select_export_root)
        message == "Select two saved connections to compare." -> stringResource(strings.error_select_two_connections)
        message == "Unsupported import format." -> stringResource(strings.error_unsupported_import_format)
        message.startsWith("Unsupported import version ") -> {
            val version = message.removePrefix("Unsupported import version ").removeSuffix(".")
            stringResource(strings.error_unsupported_import_version, version)
        }
        message == "Import JSON does not contain nodes." -> stringResource(strings.error_import_empty)
        message == "Import JSON contains invalid znode paths." -> stringResource(strings.error_import_invalid_paths)
        message == "Import JSON cannot be parsed." -> stringResource(strings.error_import_parse_failed)
        message == "Import JSON is invalid." -> stringResource(strings.error_import_invalid)
        message == "Settings store is not valid JSON." -> stringResource(strings.error_settings_store_invalid_json)
        message == "Could not read settings." -> stringResource(strings.error_settings_read_failed)
        message == "Could not save settings." -> stringResource(strings.error_settings_save_failed)
        message == "Watch failed." -> stringResource(strings.error_watch_failed)
        message == "Node does not exist." -> stringResource(strings.error_node_does_not_exist)
        message == "Node has children. Enable recursive delete to preview the full delete list." -> {
            stringResource(strings.error_node_has_children_preview)
        }
        message == "Node already exists." -> stringResource(strings.error_node_already_exists)
        message == "Node has children. Use recursive delete to remove it." -> stringResource(strings.error_node_has_children_delete)
        message == "Node version changed. Reload before saving." -> stringResource(strings.error_node_version_changed)
        message == "Invalid ACL." -> stringResource(strings.error_invalid_acl)
        message == "Not authorized to read this node." -> stringResource(strings.error_not_authorized)
        message == "ZooKeeper operation failed." -> stringResource(strings.error_zookeeper_operation_failed)
        else -> message
    }
}

@Composable
internal fun localizedZkCliOutput(output: String): String {
    val strings = Res.string
    return when {
        output.startsWith("Created ") -> stringResource(strings.terminal_output_created, output.removePrefix("Created "))
        output.startsWith("Deleted ") -> stringResource(strings.terminal_output_deleted, output.removePrefix("Deleted "))
        output.startsWith("Set ACL for ") -> stringResource(strings.terminal_output_set_acl, output.removePrefix("Set ACL for "))
        output.startsWith("Set ") && output.contains(" version ") -> {
            val body = output.removePrefix("Set ")
            val path = body.substringBefore(" version ")
            val version = body.substringAfter(" version ")
            stringResource(strings.terminal_output_set_version, path, version)
        }
        else -> output
    }
}

@Composable
internal fun ZNodeTraversalStopReason.localized(): String {
    val strings = Res.string
    return when (this) {
        ZNodeTraversalStopReason.Completed -> stringResource(strings.stop_reason_completed)
        ZNodeTraversalStopReason.MaxDepthReached -> stringResource(strings.stop_reason_max_depth)
        ZNodeTraversalStopReason.MaxNodesReached -> stringResource(strings.stop_reason_max_nodes)
        ZNodeTraversalStopReason.Canceled -> stringResource(strings.stop_reason_canceled)
    }
}

@Composable
internal fun ZNodeImportOperation.localizedType(): String {
    val strings = Res.string
    return when (type) {
        ZNodeImportOperationType.Create -> stringResource(strings.import_operation_create)
        ZNodeImportOperationType.OverwriteData -> stringResource(strings.import_operation_overwrite_data)
        ZNodeImportOperationType.UpdateAcl -> stringResource(strings.import_operation_update_acl)
        ZNodeImportOperationType.SkipExisting -> stringResource(strings.import_operation_skip_existing)
        ZNodeImportOperationType.Conflict -> stringResource(strings.import_operation_conflict)
        ZNodeImportOperationType.Failed -> stringResource(strings.import_operation_failed)
    }
}

@Composable
internal fun ZNodeImportOperation.localizedMessage(): String {
    val strings = Res.string
    return when {
        message == "Skipped existing node." -> stringResource(strings.import_message_skipped_existing)
        message == "Node already exists; create-only import will not overwrite it." -> {
            stringResource(strings.import_message_create_only_conflict)
        }
        message.startsWith("Invalid node data: ") -> {
            stringResource(strings.import_message_invalid_node_data, message.removePrefix("Invalid node data: "))
        }
        message.startsWith("Overwrite existing data using current version ") -> {
            val version = message.removePrefix("Overwrite existing data using current version ").removeSuffix(".").toIntOrNull()
            if (version != null) stringResource(strings.import_message_overwrite_data_version, version) else message
        }
        message.startsWith("Update ACL using current aversion ") -> {
            val aversion = message.removePrefix("Update ACL using current aversion ").removeSuffix(".").toIntOrNull()
            if (aversion != null) stringResource(strings.import_message_update_acl_aversion, aversion) else message
        }
        message == "Create persistent node." -> stringResource(strings.import_message_create_persistent_node)
        message == "Node does not exist." -> stringResource(strings.error_node_does_not_exist)
        message == "Node has children. Enable recursive delete to preview the full delete list." -> {
            stringResource(strings.error_node_has_children_preview)
        }
        message == "Node already exists." -> stringResource(strings.error_node_already_exists)
        message == "Node has children. Use recursive delete to remove it." -> stringResource(strings.error_node_has_children_delete)
        message == "Node version changed. Reload before saving." -> stringResource(strings.error_node_version_changed)
        message == "Invalid ACL." -> stringResource(strings.error_invalid_acl)
        message == "Not authorized to read this node." -> stringResource(strings.error_not_authorized)
        message == "ZooKeeper operation failed." -> stringResource(strings.error_zookeeper_operation_failed)
        else -> message
    }
}

@Composable
internal fun ZNodeCompareDifference.localizedType(): String {
    val strings = Res.string
    return when (type) {
        ZNodeCompareDifferenceType.MissingLeft -> stringResource(strings.compare_difference_missing_left)
        ZNodeCompareDifferenceType.MissingRight -> stringResource(strings.compare_difference_missing_right)
        ZNodeCompareDifferenceType.DataDifferent -> stringResource(strings.compare_difference_data_different)
        ZNodeCompareDifferenceType.AclDifferent -> stringResource(strings.compare_difference_acl_different)
    }
}

@Composable
internal fun ZNodeCompareDifference.localizedMessage(): String {
    val strings = Res.string
    return when (message) {
        "Only exists on right." -> stringResource(strings.compare_message_exists_on_right)
        "Only exists on left." -> stringResource(strings.compare_message_exists_on_left)
        "Data differs." -> stringResource(strings.compare_message_data_differs)
        "ACL differs." -> stringResource(strings.compare_message_acl_differs)
        else -> message
    }
}

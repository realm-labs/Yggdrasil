package io.github.realmlabs.yggdrasil.ui.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import yggdrasil.shared.generated.resources.*

@Composable
fun SettingsPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = Res.string
    var search by remember { mutableStateOf("") }
    var selectedPage by remember { mutableStateOf(SettingsSection.General) }
    val pages = SettingsSection.entries.filter { section ->
        search.isBlank() || section.matches(search)
    }

    LaunchedEffect(search) {
        if (pages.isNotEmpty() && selectedPage !in pages) {
            selectedPage = pages.first()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SettingsHeader(search = search, onSearchChange = { search = it }, onClose = onClose)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            SettingsSidebar(
                items = pages,
                selected = selectedPage,
                onSelect = { selectedPage = it },
                modifier = Modifier.width(286.dp).fillMaxHeight(),
            )
            DividerLine(vertical = true)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 50.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (pages.isEmpty()) {
                    EmptyPanelMessage(
                        stringResource(strings.settings_no_match_title),
                        stringResource(strings.settings_no_match_body),
                    )
                } else {
                    SettingsContentHeader(selectedPage)
                    when (selectedPage) {
                        SettingsSection.General -> GeneralSettings(settings, onSettingsChange)
                        SettingsSection.Appearance -> AppearanceSettings(settings, onSettingsChange)
                        SettingsSection.Explorer -> ExplorerSettings(settings, onSettingsChange)
                        SettingsSection.Terminal -> TerminalSettings(settings, onSettingsChange)
                        SettingsSection.Safety -> SafetySettings()
                        SettingsSection.Shortcuts -> ShortcutsSettings()
                    }
                }
            }
        }
        DividerLine(vertical = false)
        SettingsFooter(
            onRestoreDefaults = { onSettingsChange(AppSettings()) },
            onClose = onClose,
        )
    }
}

@Composable
private fun GeneralSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val strings = Res.string
    SettingsCard(title = stringResource(strings.settings_startup)) {
        SettingSwitchRow(
            label = stringResource(strings.settings_select_root_on_open),
            checked = settings.startAtRoot,
            onCheckedChange = { onSettingsChange(settings.copy(startAtRoot = it)) },
        )
        SettingSwitchRow(
            label = stringResource(strings.settings_inspector_expanded),
            checked = settings.inspectorExpandedByDefault,
            onCheckedChange = { onSettingsChange(settings.copy(inspectorExpandedByDefault = it)) },
        )
    }
    SettingsCard(title = stringResource(strings.settings_search_defaults)) {
        SettingSwitchRow(
            label = stringResource(strings.settings_search_paths),
            checked = settings.defaultSearchPath,
            onCheckedChange = { onSettingsChange(settings.copy(defaultSearchPath = it)) },
        )
        SettingSwitchRow(
            label = stringResource(strings.settings_search_data),
            checked = settings.defaultSearchData,
            onCheckedChange = { onSettingsChange(settings.copy(defaultSearchData = it)) },
        )
        Text(
            text = stringResource(strings.settings_search_defaults_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val strings = Res.string
    SettingsCard(title = stringResource(strings.settings_language_card)) {
        SettingSegmentRow(
            label = stringResource(strings.settings_application_language),
            options = AppLanguage.entries.map { SettingOption(it, it.label()) },
            selected = settings.language,
            onSelect = { onSettingsChange(settings.copy(language = it)) },
        )
    }
    SettingsCard(title = stringResource(strings.settings_theme_card)) {
        SettingSegmentRow(
            label = stringResource(strings.settings_application_theme),
            options = ThemePreference.entries.map { SettingOption(it, it.label()) },
            selected = settings.themePreference,
            onSelect = { onSettingsChange(settings.copy(themePreference = it)) },
        )
    }
}

@Composable
private fun ExplorerSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val strings = Res.string
    SettingsCard(title = stringResource(strings.settings_explorer)) {
        SettingSwitchRow(
            label = stringResource(strings.settings_auto_watch),
            checked = settings.autoWatchSelectedNode,
            onCheckedChange = { onSettingsChange(settings.copy(autoWatchSelectedNode = it)) },
        )
        SettingSwitchRow(
            label = stringResource(strings.settings_inspector_expanded),
            checked = settings.inspectorExpandedByDefault,
            onCheckedChange = { onSettingsChange(settings.copy(inspectorExpandedByDefault = it)) },
        )
        Text(
            text = stringResource(strings.settings_tree_behavior_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TerminalSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val strings = Res.string
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsCard(title = stringResource(strings.settings_embedded_zkcli), modifier = Modifier.weight(1f)) {
            SettingSwitchRow(
                label = stringResource(strings.settings_terminal_enable),
                checked = settings.embeddedTerminalEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(embeddedTerminalEnabled = it)) },
            )
            SettingSwitchRow(
                label = stringResource(strings.settings_terminal_expanded),
                checked = settings.terminalExpandedByDefault,
                onCheckedChange = { onSettingsChange(settings.copy(terminalExpandedByDefault = it)) },
            )
            SettingSwitchRow(
                label = stringResource(strings.settings_terminal_clear_on_connection),
                checked = settings.clearTerminalOnConnectionChange,
                onCheckedChange = { onSettingsChange(settings.copy(clearTerminalOnConnectionChange = it)) },
            )
        }
        SettingsCard(title = stringResource(strings.settings_terminal_appearance), modifier = Modifier.weight(1f)) {
            SettingSegmentRow(
                label = stringResource(strings.settings_font_size),
                values = listOf("12", "13", "14", "15"),
                selected = settings.terminalFontSize.toString(),
                onSelect = { onSettingsChange(settings.copy(terminalFontSize = it.toInt())) },
            )
            SettingSegmentRow(
                label = stringResource(strings.settings_theme),
                options = TerminalThemePreference.entries.map { SettingOption(it, it.label()) },
                selected = settings.terminalThemePreference,
                onSelect = { onSettingsChange(settings.copy(terminalThemePreference = it)) },
            )
            SettingSwitchRow(
                label = stringResource(strings.settings_show_timestamps),
                checked = settings.terminalShowTimestamps,
                onCheckedChange = { onSettingsChange(settings.copy(terminalShowTimestamps = it)) },
            )
            TerminalPreview(settings)
        }
    }
}

@Composable
private fun SafetySettings() {
    val strings = Res.string
    SettingsCard(title = stringResource(strings.settings_destructive_operations)) {
        DangerousCommandBox()
    }
}

@Composable
private fun ShortcutsSettings() {
    val strings = Res.string
    SettingsCard(title = stringResource(strings.settings_keyboard_shortcuts)) {
        ShortcutRow(stringResource(strings.settings_open_command_workflow), "⌘ Command")
        ShortcutRow(stringResource(strings.settings_run_top_search), stringResource(strings.settings_search_button))
        ShortcutRow(stringResource(strings.settings_clear_terminal), stringResource(strings.common_clear))
        Text(
            text = stringResource(strings.settings_shortcut_editing_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsHeader(
    search: String,
    onSearchChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val strings = Res.string
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Spacer(Modifier.height(ShellMetrics.TitleBarTopInset))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.weight(1f))
            ShellTextInput(
                value = search,
                onValueChange = onSearchChange,
                placeholder = stringResource(strings.settings_search_placeholder),
                modifier = Modifier.width(360.dp),
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.width(96.dp).height(ShellMetrics.ControlHeight),
                shape = ShellMetrics.FieldShape,
            ) {
                Text(stringResource(strings.common_close))
            }
        }
    }
}

@Composable
private fun SettingsSidebar(
    items: List<SettingsSection>,
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = Res.string
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 18.dp),
    ) {
        items.forEach { item ->
            val active = item == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(ShellMetrics.FieldShape)
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                    .border(
                        width = if (active) 1.dp else 0.dp,
                        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                        shape = ShellMetrics.FieldShape,
                    )
                    .clickable { onSelect(item) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.width(28.dp),
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("v1.12.0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusDot(ConnectionRuntimeStatus.Connected)
            Text(
                stringResource(strings.settings_local_settings),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingsContentHeader(section: SettingsSection) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(ShellMetrics.FieldShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), ShellMetrics.FieldShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = section.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Column {
            Text(
                stringResource(section.titleRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(section.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(ShellMetrics.CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), ShellMetrics.CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSegmentRow(
    label: String,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .width(260.dp)
                .height(32.dp)
                .clip(ShellMetrics.FieldShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), ShellMetrics.FieldShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            values.forEachIndexed { index, value ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        value,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                if (index < values.lastIndex) {
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

private data class SettingOption<T>(
    val value: T,
    val label: String,
)

@Composable
private fun <T> SettingSegmentRow(
    label: String,
    options: List<SettingOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier
                .width(260.dp)
                .height(32.dp)
                .clip(ShellMetrics.FieldShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), ShellMetrics.FieldShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val active = option.value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(option.value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                if (index < options.lastIndex) {
                    Box(
                        Modifier.width(1.dp).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalPreview(settings: AppSettings) {
    val background = when (settings.terminalThemePreference) {
        TerminalThemePreference.Dark -> Color(0xFF151515)
        TerminalThemePreference.Light -> Color(0xFFF8FAFA)
        TerminalThemePreference.Auto -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val textColor = if (settings.terminalThemePreference == TerminalThemePreference.Dark) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShellMetrics.FieldShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), ShellMetrics.FieldShape)
            .background(background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val prefix = if (settings.terminalShowTimestamps) "09:15:21  " else ""
        Text(
            "${prefix}[zk: 127.0.0.1:2181(CONNECTED) 0] ls /game",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF2E8E62),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "          [config, players, state, metrics]",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DangerousCommandBox() {
    val strings = Res.string
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShellMetrics.FieldShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), ShellMetrics.FieldShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(strings.settings_delete_preview_required),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DangerousCommandRow("delete", stringResource(strings.settings_delete_command_desc))
        DangerousCommandRow("rmr", stringResource(strings.settings_rmr_command_desc))
        DangerousCommandRow("setAcl", stringResource(strings.settings_set_acl_command_desc))
    }
}

@Composable
private fun DangerousCommandRow(command: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShortcutRow(label: String, shortcut: String) {
    Row(modifier = Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(
            shortcut,
            modifier = Modifier
                .clip(ShellMetrics.TreeRowShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsFooter(onRestoreDefaults: () -> Unit, onClose: () -> Unit) {
    val strings = Res.string
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onRestoreDefaults,
            modifier = Modifier.width(180.dp).height(ShellMetrics.ControlHeight),
            shape = ShellMetrics.FieldShape,
        ) {
            Text(stringResource(strings.common_restore_defaults))
        }
        Spacer(Modifier.width(16.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.width(98.dp).height(ShellMetrics.ControlHeight),
            shape = ShellMetrics.FieldShape,
        ) {
            Text(stringResource(strings.common_done))
        }
    }
}

private enum class SettingsSection(
    val icon: ImageVector,
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    private val keywords: List<String>,
) {
    General(
        Icons.Outlined.Settings,
        Res.string.settings_general,
        Res.string.settings_general_description,
        listOf("startup", "search", "inspector", "通用", "启动", "搜索", "检查器")
    ),
    Appearance(
        Icons.Outlined.Palette,
        Res.string.settings_appearance,
        Res.string.settings_appearance_description,
        listOf("language", "theme", "light", "dark", "语言", "主题"),
    ),
    Explorer(
        Icons.Outlined.Storage,
        Res.string.settings_explorer,
        Res.string.settings_explorer_description,
        listOf("znode", "tree", "watch", "inspector", "浏览器", "树", "监听", "检查器")
    ),
    Terminal(
        Icons.Outlined.Code,
        Res.string.settings_terminal,
        Res.string.settings_terminal_description,
        listOf("zkcli", "terminal", "font", "timestamp", "终端", "字号", "时间戳")
    ),
    Safety(
        Icons.Outlined.Security,
        Res.string.settings_safety,
        Res.string.settings_safety_description,
        listOf("delete", "dangerous", "confirmation", "acl", "安全", "删除", "确认")
    ),
    Shortcuts(
        Icons.Outlined.Keyboard,
        Res.string.settings_shortcuts,
        Res.string.settings_shortcuts_description,
        listOf("keyboard", "shortcut", "command", "快捷键", "命令")
    ),
    ;

    @Composable
    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        return stringResource(titleRes).lowercase().contains(needle) ||
                stringResource(descriptionRes).lowercase().contains(needle) ||
                name.lowercase().contains(needle) ||
            keywords.any { it.contains(needle) }
    }
}

@Composable
private fun AppLanguage.label(): String {
    val strings = Res.string
    return when (this) {
        AppLanguage.English -> stringResource(strings.language_english)
        AppLanguage.Chinese -> stringResource(strings.language_chinese)
    }
}

@Composable
private fun ThemePreference.label(): String {
    val strings = Res.string
    return when (this) {
        ThemePreference.System -> stringResource(strings.theme_system)
        ThemePreference.Light -> stringResource(strings.theme_light)
        ThemePreference.Dark -> stringResource(strings.theme_dark)
    }
}

@Composable
private fun TerminalThemePreference.label(): String {
    val strings = Res.string
    return when (this) {
        TerminalThemePreference.Auto -> stringResource(strings.terminal_theme_auto)
        TerminalThemePreference.Light -> stringResource(strings.theme_light)
        TerminalThemePreference.Dark -> stringResource(strings.theme_dark)
    }
}

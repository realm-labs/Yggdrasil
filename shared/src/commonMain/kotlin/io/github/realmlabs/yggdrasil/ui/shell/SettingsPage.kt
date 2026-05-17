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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.realmlabs.yggdrasil.application.state.AppSettings
import io.github.realmlabs.yggdrasil.application.state.ConnectionRuntimeStatus
import io.github.realmlabs.yggdrasil.application.state.TerminalThemePreference
import io.github.realmlabs.yggdrasil.application.state.ThemePreference

@Composable
fun SettingsPage(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    EmptyPanelMessage("No matching settings", "Try a different keyword.")
                } else {
                    SettingsContentHeader(selectedPage)
                    when (selectedPage) {
                        SettingsSection.General -> GeneralSettings(settings, onSettingsChange)
                        SettingsSection.Appearance -> AppearanceSettings(settings, onSettingsChange)
                        SettingsSection.Explorer -> ExplorerSettings(settings, onSettingsChange)
                        SettingsSection.Terminal -> TerminalSettings(settings, onSettingsChange)
                        SettingsSection.Safety -> SafetySettings(settings, onSettingsChange)
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
    SettingsCard(title = "Startup") {
        SettingSwitchRow(
            label = "Select the root znode when a connection opens",
            checked = settings.startAtRoot,
            onCheckedChange = { onSettingsChange(settings.copy(startAtRoot = it)) },
        )
        SettingSwitchRow(
            label = "Open inspector expanded by default",
            checked = settings.inspectorExpandedByDefault,
            onCheckedChange = { onSettingsChange(settings.copy(inspectorExpandedByDefault = it)) },
        )
    }
    SettingsCard(title = "Search defaults") {
        SettingSwitchRow(
            label = "Search znode paths",
            checked = settings.defaultSearchPath,
            onCheckedChange = { onSettingsChange(settings.copy(defaultSearchPath = it)) },
        )
        SettingSwitchRow(
            label = "Search znode data",
            checked = settings.defaultSearchData,
            onCheckedChange = { onSettingsChange(settings.copy(defaultSearchData = it)) },
        )
        Text(
            text = "These defaults are used by the top bar search.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SettingsCard(title = "Theme") {
        SettingSegmentRow(
            label = "Application theme",
            values = ThemePreference.entries.map { it.name },
            selected = settings.themePreference.name,
            onSelect = { onSettingsChange(settings.copy(themePreference = ThemePreference.valueOf(it))) },
        )
    }
}

@Composable
private fun ExplorerSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SettingsCard(title = "Znode Explorer") {
        SettingSwitchRow(
            label = "Automatically watch the selected znode",
            checked = settings.autoWatchSelectedNode,
            onCheckedChange = { onSettingsChange(settings.copy(autoWatchSelectedNode = it)) },
        )
        SettingSwitchRow(
            label = "Open inspector expanded by default",
            checked = settings.inspectorExpandedByDefault,
            onCheckedChange = { onSettingsChange(settings.copy(inspectorExpandedByDefault = it)) },
        )
        Text(
            text = "Tree loading and node refresh behavior are driven by the active ZooKeeper connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TerminalSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsCard(title = "Embedded zkCli", modifier = Modifier.weight(1f)) {
            SettingSwitchRow(
                label = "Enable embedded terminal panel",
                checked = settings.embeddedTerminalEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(embeddedTerminalEnabled = it)) },
            )
            SettingSwitchRow(
                label = "Open terminal expanded by default",
                checked = settings.terminalExpandedByDefault,
                onCheckedChange = { onSettingsChange(settings.copy(terminalExpandedByDefault = it)) },
            )
            SettingSwitchRow(
                label = "Clear output after switching connection",
                checked = settings.clearTerminalOnConnectionChange,
                onCheckedChange = { onSettingsChange(settings.copy(clearTerminalOnConnectionChange = it)) },
            )
        }
        SettingsCard(title = "Terminal appearance", modifier = Modifier.weight(1f)) {
            SettingSegmentRow(
                label = "Font size",
                values = listOf("12", "13", "14", "15"),
                selected = settings.terminalFontSize.toString(),
                onSelect = { onSettingsChange(settings.copy(terminalFontSize = it.toInt())) },
            )
            SettingSegmentRow(
                label = "Theme",
                values = TerminalThemePreference.entries.map { it.name },
                selected = settings.terminalThemePreference.name,
                onSelect = {
                    onSettingsChange(settings.copy(terminalThemePreference = TerminalThemePreference.valueOf(it)))
                },
            )
            SettingSwitchRow(
                label = "Show command timestamps",
                checked = settings.terminalShowTimestamps,
                onCheckedChange = { onSettingsChange(settings.copy(terminalShowTimestamps = it)) },
            )
            TerminalPreview(settings)
        }
    }
}

@Composable
private fun SafetySettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SettingsCard(title = "Destructive operations") {
        SettingSwitchRow(
            label = "Require full-path confirmation for dangerous deletes",
            checked = settings.requireDangerousConfirmation,
            onCheckedChange = { onSettingsChange(settings.copy(requireDangerousConfirmation = it)) },
        )
        DangerousCommandBox(settings.requireDangerousConfirmation)
    }
}

@Composable
private fun ShortcutsSettings() {
    SettingsCard(title = "Keyboard shortcuts") {
        ShortcutRow("Open command workflow", "⌘ Command")
        ShortcutRow("Run top bar search", "Search button")
        ShortcutRow("Clear terminal output", "Clear")
        Text(
            text = "Shortcut editing is not available yet, so editable controls were removed from this page.",
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
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
            Text("Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
        DividerLine(vertical = false)
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsBrandMark()
            Text("Yggdrasil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            ShellTextInput(
                value = search,
                onValueChange = onSearchChange,
                placeholder = "Search settings...",
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
                Text("Close")
            }
        }
        DividerLine(vertical = false)
    }
}

@Composable
private fun SettingsSidebar(
    items: List<SettingsSection>,
    selected: SettingsSection,
    onSelect: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    text = item.title,
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
            Text("Local settings", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
            Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun DangerousCommandBox(requireConfirmation: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShellMetrics.FieldShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), ShellMetrics.FieldShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (requireConfirmation) {
                "Recursive and multi-node deletes require typing the full root path before execution."
            } else {
                "Delete preview is still required, but full-path confirmation is skipped."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DangerousCommandRow("delete", "Delete a znode")
        DangerousCommandRow("rmr", "Recursively delete a znode and its children")
        DangerousCommandRow("setAcl", "Modify ACL permissions")
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
            Text("Restore Defaults")
        }
        Spacer(Modifier.width(16.dp))
        Button(
            onClick = onClose,
            modifier = Modifier.width(98.dp).height(ShellMetrics.ControlHeight),
            shape = ShellMetrics.FieldShape,
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun SettingsBrandMark() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(28.dp)) {
        val stroke = 2.1.dp.toPx()
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.12f), Offset(size.width * 0.5f, size.height * 0.88f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.26f), Offset(size.width * 0.8f, size.height * 0.26f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.50f), Offset(size.width * 0.8f, size.height * 0.50f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.74f), Offset(size.width * 0.8f, size.height * 0.74f), stroke, cap = StrokeCap.Round)
        drawCircle(color, radius = 2.7.dp.toPx(), center = Offset(size.width * 0.2f, size.height * 0.26f))
        drawCircle(color, radius = 2.7.dp.toPx(), center = Offset(size.width * 0.8f, size.height * 0.50f))
        drawCircle(color, radius = 2.7.dp.toPx(), center = Offset(size.width * 0.2f, size.height * 0.74f))
    }
}

private enum class SettingsSection(
    val icon: ImageVector,
    val title: String,
    val description: String,
    private val keywords: List<String>,
) {
    General(
        Icons.Outlined.Settings,
        "General",
        "Startup behavior and top-level defaults.",
        listOf("startup", "search", "inspector")
    ),
    Appearance(Icons.Outlined.Palette, "Appearance", "Application color theme.", listOf("theme", "light", "dark")),
    Explorer(
        Icons.Outlined.Storage,
        "Znode Explorer",
        "Tree, inspector, and watch behavior.",
        listOf("znode", "tree", "watch", "inspector")
    ),
    Terminal(
        Icons.Outlined.Code,
        "zkCli & Terminal",
        "Embedded zkCli behavior and terminal display.",
        listOf("zkcli", "terminal", "font", "timestamp")
    ),
    Safety(
        Icons.Outlined.Security,
        "Safety",
        "Confirmation behavior for destructive operations.",
        listOf("delete", "dangerous", "confirmation", "acl")
    ),
    Shortcuts(
        Icons.Outlined.Keyboard,
        "Shortcuts",
        "Currently available keyboard and toolbar actions.",
        listOf("keyboard", "shortcut", "command")
    ),
    ;

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        return title.lowercase().contains(needle) ||
            description.lowercase().contains(needle) ||
            keywords.any { it.contains(needle) }
    }
}

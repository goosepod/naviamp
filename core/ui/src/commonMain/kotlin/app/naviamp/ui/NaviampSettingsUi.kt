package app.naviamp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.playback.EqualizerBandFrequencies
import app.naviamp.domain.playback.EqualizerPreset
import app.naviamp.domain.playback.MaxEqualizerGainDb
import app.naviamp.domain.playback.MinEqualizerGainDb
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.radio.MaxArtistRunLength
import app.naviamp.domain.radio.MinArtistRunLength
import app.naviamp.domain.radio.RadioArtistSpread
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioFamiliarity
import app.naviamp.domain.radio.RadioArtistRunMode
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import app.naviamp.domain.settings.MaxWaveformBucketCount
import app.naviamp.domain.settings.MinWaveformBucketCount
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PreviousButtonBehavior
import app.naviamp.domain.settings.StreamBitrateKbpsOptions
import app.naviamp.domain.settings.StreamQualityMode
import app.naviamp.domain.settings.StreamQualityPreference
import app.naviamp.domain.settings.StreamingCodec
import app.naviamp.domain.settings.UpNextSelectionBehavior

data class NaviampDiagnosticsUi(
    val sections: List<NaviampDiagnosticsSectionUi> = emptyList(),
)

data class NaviampDiagnosticsSectionUi(
    val title: String,
    val rows: List<Pair<String, String>>,
)

data class NaviampAboutUi(
    val version: String = "Unknown",
    val buildNumber: String = "Unknown",
    val libraries: List<String> = DefaultNaviampLibraries,
    val changelog: List<String> = DefaultNaviampChangelog,
)

data class NaviampSavedConnectionUi(
    val id: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val current: Boolean = false,
)

enum class NaviampSettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Connections("Connections", "Servers and credentials", NaviampIcons.Settings),
    Playback("Playback", "Player behavior and lyrics", NaviampTransportIcons.Play),
    Cache("Cache", "Audio cache and downloads", NaviampIcons.Downloads),
    LocalData("Local data", "Cache, library, and database", NaviampIcons.Library),
    Diagnostics("Diagnostics", "Stats and debugging", NaviampIcons.Settings),
    About("About", "Version, libraries, changelog", NaviampIcons.Settings),
}

@Composable
fun NaviampSharedSettingsContent(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings = CacheSettings(),
    diagnostics: NaviampDiagnosticsUi = NaviampDiagnosticsUi(),
    about: NaviampAboutUi = NaviampAboutUi(),
    savedConnections: List<NaviampSavedConnectionUi> = emptyList(),
    isConnectionFormOpen: Boolean = false,
    isConnecting: Boolean = false,
    connectionStatus: String? = null,
    settingsSyncStatus: String? = null,
    connectionForm: ConnectionFormState = ConnectionFormState(),
    hasSavedConnection: Boolean = false,
    onEditConnection: () -> Unit,
    onNewConnection: () -> Unit = onEditConnection,
    onEditSavedConnection: (NaviampSavedConnectionUi) -> Unit = { onEditConnection() },
    onConnectSavedConnection: (NaviampSavedConnectionUi) -> Unit = {},
    onDeleteSavedConnection: (NaviampSavedConnectionUi) -> Unit = {},
    onImportSettingsSyncFile: (() -> Unit)? = null,
    onPasteSettingsSyncJson: (() -> Unit)? = null,
    onConnectionFormChanged: (ConnectionFormState) -> Unit = {},
    onConnect: () -> Unit = {},
    onCancelConnectionForm: () -> Unit = {},
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit = onPlaybackSettingsChanged,
    onCacheSettingsChanged: (CacheSettings) -> Unit = {},
    onClearCache: (() -> Unit)? = null,
    onClearLibrary: (() -> Unit)? = null,
    onResetDatabase: (() -> Unit)? = null,
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    supportsEqualizer: Boolean = false,
    supportsSonicSimilarity: Boolean = false,
    downloadBytes: Long = 0L,
    showQueueBehavior: Boolean = true,
    showDebugLogging: Boolean = true,
    showMobileNetworkQuality: Boolean = false,
) {
    var selectedCategory by remember { mutableStateOf<NaviampSettingsCategory?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        selectedCategory?.let { category ->
            SettingsDetailHeader(category = category, colors = colors, onBack = { selectedCategory = null })
            when (category) {
                NaviampSettingsCategory.Connections -> {
                    NaviampConnectionsSettingsSection(
                        colors = colors,
                        savedConnections = savedConnections,
                        isConnectionFormOpen = isConnectionFormOpen,
                        isConnecting = isConnecting,
                        connectionStatus = connectionStatus,
                        settingsSyncStatus = settingsSyncStatus,
                        connectionForm = connectionForm,
                        hasSavedConnection = hasSavedConnection,
                        onNewConnection = onNewConnection,
                        onEditConnection = onEditSavedConnection,
                        onConnectConnection = onConnectSavedConnection,
                        onDeleteConnection = onDeleteSavedConnection,
                        onImportSettingsSyncFile = onImportSettingsSyncFile,
                        onPasteSettingsSyncJson = onPasteSettingsSyncJson,
                        onConnectionFormChanged = onConnectionFormChanged,
                        onConnect = onConnect,
                        onCancelConnectionForm = onCancelConnectionForm,
                    )
                }
                NaviampSettingsCategory.Playback -> NaviampPlaybackSettingsSection(
                    colors = colors,
                    playbackSettings = playbackSettings,
                    supportsReplayGain = supportsReplayGain,
                    supportsGapless = supportsGapless,
                    supportsCrossfade = supportsCrossfade,
                    supportsEqualizer = supportsEqualizer,
                    supportsSonicSimilarity = supportsSonicSimilarity,
                    showReplayGain = true,
                    showCrossfade = true,
                    showQueueBehavior = showQueueBehavior,
                    showDebugLogging = showDebugLogging,
                    showMobileNetworkQuality = showMobileNetworkQuality,
                    showLrclibLyrics = true,
                    downloadBytes = downloadBytes,
                    onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                    onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
                )
                NaviampSettingsCategory.Cache -> SharedCacheSettingsSection(
                    colors = colors,
                    cacheSettings = cacheSettings,
                    diagnostics = NaviampDiagnosticsUi(
                        diagnostics.sections.filter { section ->
                            section.title == "Storage" || section.title == "Library sync"
                        },
                    ),
                    onCacheSettingsChanged = onCacheSettingsChanged,
                )
                NaviampSettingsCategory.LocalData -> SharedLocalDataActions(
                    colors = colors,
                    onClearCache = onClearCache,
                    onClearLibrary = onClearLibrary,
                    onResetDatabase = onResetDatabase,
                )
                NaviampSettingsCategory.Diagnostics -> NaviampDiagnosticsSettingsSection(
                    colors = colors,
                    title = "Diagnostics",
                    diagnostics = diagnostics,
                    emptyText = "Diagnostics will appear after the app initializes.",
                )
                NaviampSettingsCategory.About -> NaviampAboutSettingsSection(
                    colors = colors,
                    about = about,
                )
            }
        } ?: run {
            Text("Settings", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            NaviampSettingsCategory.entries.forEach { category ->
                SettingsCategoryRow(
                    category = category,
                    colors = colors,
                    enabled = when (category) {
                        NaviampSettingsCategory.LocalData -> onClearCache != null || onClearLibrary != null || onResetDatabase != null
                        else -> true
                    },
                    onClick = { selectedCategory = category },
                )
            }
        }
    }
}

@Composable
private fun SettingsDetailHeader(
    category: NaviampSettingsCategory,
    colors: NaviampColors,
    onBack: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(category.label, color = colors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(category.subtitle, color = colors.secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    category: NaviampSettingsCategory,
    colors: NaviampColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = if (enabled) colors.secondaryText else colors.mutedText,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(category.label, color = if (enabled) colors.primaryText else colors.mutedText, fontSize = 15.sp)
            Text(category.subtitle, color = colors.mutedText, fontSize = 12.sp)
        }
        Icon(NaviampIcons.ChevronRight, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun NaviampAboutSettingsSection(
    colors: NaviampColors,
    about: NaviampAboutUi,
) {
    SettingsSectionTitle("About", colors)
    AboutInfoRow("Version", about.version, colors)
    AboutInfoRow("Build number", about.buildNumber, colors)

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Libraries", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        about.libraries.forEach { library ->
            Text(library, color = colors.secondaryText, fontSize = 12.sp)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Changelog", color = colors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (about.changelog.isEmpty()) {
            Text("Changelog entries will appear here in a future release.", color = colors.secondaryText, fontSize = 12.sp)
        } else {
            about.changelog.forEach { entry ->
                Text(entry, color = colors.secondaryText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun NaviampConnectionsSettingsSection(
    colors: NaviampColors,
    savedConnections: List<NaviampSavedConnectionUi>,
    isConnectionFormOpen: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    settingsSyncStatus: String?,
    connectionForm: ConnectionFormState,
    hasSavedConnection: Boolean,
    onNewConnection: () -> Unit,
    onEditConnection: (NaviampSavedConnectionUi) -> Unit,
    onConnectConnection: (NaviampSavedConnectionUi) -> Unit,
    onDeleteConnection: (NaviampSavedConnectionUi) -> Unit,
    onImportSettingsSyncFile: (() -> Unit)?,
    onPasteSettingsSyncJson: (() -> Unit)?,
    onConnectionFormChanged: (ConnectionFormState) -> Unit,
    onConnect: () -> Unit,
    onCancelConnectionForm: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<NaviampSavedConnectionUi?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle("Connections", colors)
        if (savedConnections.isEmpty()) {
            Text("No saved connections yet.", color = colors.secondaryText, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                savedConnections.forEach { connection ->
                    NaviampSavedConnectionRow(
                        colors = colors,
                        connection = connection,
                        enabled = !isConnecting,
                        onEdit = { onEditConnection(connection) },
                        onDelete = { pendingDelete = connection },
                        onConnect = { onConnectConnection(connection) },
                    )
                }
            }
        }
        PrimaryButton("New connection", colors, enabled = !isConnecting, onClick = onNewConnection)
        onImportSettingsSyncFile?.let { importSettings ->
            HorizontalDivider(color = colors.border)
            SettingsSectionTitle("Settings Sync", colors)
            Text(
                "Import a shared Naviamp settings file from a synced folder.",
                color = colors.secondaryText,
                fontSize = 12.sp,
            )
            PrimarySettingsButton("Import shared settings", colors, enabled = !isConnecting, onClick = importSettings)
            onPasteSettingsSyncJson?.let { pasteSettings ->
                PrimarySettingsButton("Paste shared settings", colors, enabled = !isConnecting, onClick = pasteSettings)
            }
            settingsSyncStatus?.let {
                Text(it, color = colors.secondaryText, fontSize = 12.sp)
            }
        }
        connectionStatus?.takeUnless { isConnectionFormOpen }?.let {
            Text(it, color = colors.secondaryText, fontSize = 12.sp)
        }
        if (isConnectionFormOpen) {
            HorizontalDivider(color = colors.border)
            NaviampConnectionForm(
                form = connectionForm,
                colors = colors,
                isReconnect = hasSavedConnection,
                isConnecting = isConnecting,
                connectionStatus = connectionStatus,
                settingsSyncStatus = settingsSyncStatus,
                onFormChanged = onConnectionFormChanged,
                onConnect = onConnect,
                onImportSettingsSyncFile = onImportSettingsSyncFile,
                onPasteSettingsSyncJson = onPasteSettingsSyncJson,
                onCancel = onCancelConnectionForm,
            )
        }
        pendingDelete?.let { connection ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete Connection") },
                text = { Text("Delete ${connection.displayName}? This removes the saved server entry.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            onDeleteConnection(connection)
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun NaviampSavedConnectionRow(
    colors: NaviampColors,
    connection: NaviampSavedConnectionUi,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (connection.current) colors.accent else colors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    connection.displayName,
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (connection.current) {
                    Text("Current", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "${connection.username} - ${connection.serverUrl}",
                color = colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(enabled = enabled, onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(NaviampIcons.Edit, contentDescription = "Edit connection", tint = if (enabled) colors.primaryText else colors.mutedText)
        }
        IconButton(enabled = enabled, onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(NaviampIcons.Trash, contentDescription = "Delete connection", tint = if (enabled) colors.primaryText else colors.mutedText)
        }
        TextButton(enabled = enabled, onClick = onConnect) {
            Text(if (connection.current) "Reconnect" else "Connect", color = if (enabled) colors.accent else colors.mutedText)
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    colors: NaviampColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, color = colors.mutedText, fontSize = 12.sp, modifier = Modifier.weight(0.38f))
        Text(value, color = colors.primaryText, fontSize = 12.sp, modifier = Modifier.weight(0.62f))
    }
}

@Composable
fun NaviampDiagnosticsSettingsSection(
    colors: NaviampColors,
    title: String = "Diagnostics",
    diagnostics: NaviampDiagnosticsUi,
    emptyText: String = "No diagnostics yet.",
) {
    SettingsSectionTitle(title, colors)
    if (diagnostics.sections.isEmpty()) {
        Text(emptyText, color = colors.secondaryText, fontSize = 12.sp)
        return
    }
    diagnostics.sections.forEach { section ->
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(section.title, color = colors.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            section.rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        label,
                        color = colors.mutedText,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(
                        value,
                        color = colors.primaryText,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
        }
    }
}

private val DefaultNaviampLibraries = listOf(
    "Kotlin Multiplatform",
    "Compose Multiplatform and Material 3",
    "kotlinx.coroutines",
    "kotlinx.serialization",
    "Ktor",
    "SQLDelight and SQLite",
    "BASS audio engine and add-ons",
    "Skiko and Skia",
    "Navidrome and OpenSubsonic APIs",
)

private val DefaultNaviampChangelog = listOf(
    "Added multiple saved Navidrome connections on Android.",
    "Switched the app typography to Nunito Sans.",
)

@Composable
private fun SharedLocalDataActions(
    colors: NaviampColors,
    onClearCache: (() -> Unit)?,
    onClearLibrary: (() -> Unit)?,
    onResetDatabase: (() -> Unit)?,
) {
    var confirmAction by remember { mutableStateOf<SharedLocalDataAction?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle("Local data", colors)
        Text(
            "Manage local cache, indexed library data, downloads, and app database storage.",
            color = colors.secondaryText,
            fontSize = 12.sp,
        )
        PrimarySettingsButton("Clear cache", colors, enabled = onClearCache != null) {
            confirmAction = SharedLocalDataAction.ClearCache
        }
        PrimarySettingsButton("Clear library index", colors, enabled = onClearLibrary != null) {
            confirmAction = SharedLocalDataAction.ClearLibrary
        }
        PrimarySettingsButton("Reset database", colors, enabled = onResetDatabase != null) {
            confirmAction = SharedLocalDataAction.ResetDatabase
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAction = null
                        when (action) {
                            SharedLocalDataAction.ClearCache -> onClearCache?.invoke()
                            SharedLocalDataAction.ClearLibrary -> onClearLibrary?.invoke()
                            SharedLocalDataAction.ResetDatabase -> onResetDatabase?.invoke()
                        }
                    },
                ) {
                    Text(action.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SharedCacheSettingsSection(
    colors: NaviampColors,
    cacheSettings: CacheSettings,
    diagnostics: NaviampDiagnosticsUi,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
) {
    val normalized = cacheSettings.normalized()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle("Audio cache", colors)
        diagnosticRowValue(diagnostics, "Storage", "Audio cache")?.let { value ->
            Text(value, color = colors.secondaryText, fontSize = 12.sp)
        }
        SettingsCheckboxRow(
            colors = colors,
            checked = normalized.audioCachingEnabled,
            label = "Enable audio cache and prefetch",
            onCheckedChange = { enabled ->
                onCacheSettingsChanged(normalized.copy(audioCachingEnabled = enabled).normalized())
            },
        )
        DetentIntSettingsSlider(
            colors = colors,
            title = "Prefetch depth",
            value = normalized.audioPrefetchDepth,
            detents = PrefetchDepthOptions,
            enabled = normalized.audioCachingEnabled,
            valueLabel = { depth -> if (depth == 0) "Off" else "$depth tracks" },
            onValueChanged = { depth ->
                onCacheSettingsChanged(normalized.copy(audioPrefetchDepth = depth).normalized())
            },
        )
        DetentByteSettingsSlider(
            colors = colors,
            title = "Audio cache budget",
            valueBytes = normalized.maxAudioCacheBytes,
            detents = AudioCacheBudgetOptions,
            onValueChanged = { bytes ->
                onCacheSettingsChanged(normalized.copy(maxAudioCacheBytes = bytes).normalized())
            },
        )

        SettingsSectionTitle("Waveforms", colors)
        diagnosticRowValue(diagnostics, "Storage", "Waveforms")?.let { value ->
            Text(value, color = colors.secondaryText, fontSize = 12.sp)
        }
        SettingsCheckboxRow(
            colors = colors,
            checked = normalized.waveformsEnabled,
            label = "Generate track waveforms",
            onCheckedChange = { enabled ->
                onCacheSettingsChanged(normalized.copy(waveformsEnabled = enabled).normalized())
            },
        )
        DetentIntSettingsSlider(
            colors = colors,
            title = "Waveform detail",
            value = normalized.waveformBucketCount,
            detents = WaveformBucketCountOptions,
            enabled = normalized.waveformsEnabled,
            valueLabel = { count -> "$count steps" },
            onValueChanged = { count ->
                onCacheSettingsChanged(normalized.copy(waveformBucketCount = count).normalized())
            },
        )

        SettingsSectionTitle("Downloads", colors)
        diagnosticRowValue(diagnostics, "Storage", "Downloads")?.let { value ->
            Text(value, color = colors.secondaryText, fontSize = 12.sp)
        }
        SettingsCheckboxRow(
            colors = colors,
            checked = normalized.offlineModeEnabled,
            label = "Offline Mode",
            onCheckedChange = { enabled ->
                onCacheSettingsChanged(normalized.copy(offlineModeEnabled = enabled).normalized())
            },
        )
        Text(
            "Search only downloaded tracks while Offline Mode is enabled.",
            color = colors.secondaryText,
            fontSize = 12.sp,
        )
        DetentByteSettingsSlider(
            colors = colors,
            title = "Download storage budget",
            valueBytes = normalized.maxDownloadBytes,
            detents = DownloadBudgetOptions,
            onValueChanged = { bytes ->
                onCacheSettingsChanged(normalized.copy(maxDownloadBytes = bytes).normalized())
            },
        )

        NaviampDiagnosticsSettingsSection(
            colors = colors,
            title = "Storage",
            diagnostics = diagnostics,
            emptyText = "Cache sizes will appear after the app initializes storage.",
        )
    }
}

@Composable
private fun DetentIntSettingsSlider(
    colors: NaviampColors,
    title: String,
    value: Int,
    detents: List<Int>,
    enabled: Boolean = true,
    valueLabel: (Int) -> String,
    onValueChanged: (Int) -> Unit,
) {
    val normalizedDetents = detents.distinct().sorted()
    val selectedIndex = normalizedDetents.indexOf(value).takeIf { it >= 0 } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = colors.secondaryText, fontSize = 12.sp)
            Text(valueLabel(normalizedDetents.getOrElse(selectedIndex) { value }), color = colors.primaryText, fontSize = 12.sp)
        }
        Slider(
            enabled = enabled && normalizedDetents.size > 1,
            value = selectedIndex.toFloat(),
            onValueChange = { raw ->
                val index = raw.toInt().coerceIn(normalizedDetents.indices)
                onValueChanged(normalizedDetents[index])
            },
            valueRange = 0f..(normalizedDetents.lastIndex.coerceAtLeast(0)).toFloat(),
            steps = (normalizedDetents.size - 2).coerceAtLeast(0),
        )
    }
}

@Composable
private fun DetentByteSettingsSlider(
    colors: NaviampColors,
    title: String,
    valueBytes: Long,
    detents: List<Long>,
    onValueChanged: (Long) -> Unit,
) {
    val normalizedDetents = detents.distinct().sorted()
    val selectedIndex = normalizedDetents.indexOf(valueBytes).takeIf { it >= 0 }
        ?: normalizedDetents.indices.minByOrNull { index ->
            kotlin.math.abs(normalizedDetents[index] - valueBytes)
        }
        ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = colors.secondaryText, fontSize = 12.sp)
            Text(normalizedDetents.getOrElse(selectedIndex) { valueBytes }.storageBytesLabel(), color = colors.primaryText, fontSize = 12.sp)
        }
        Slider(
            enabled = normalizedDetents.size > 1,
            value = selectedIndex.toFloat(),
            onValueChange = { raw ->
                val index = raw.toInt().coerceIn(normalizedDetents.indices)
                onValueChanged(normalizedDetents[index])
            },
            valueRange = 0f..(normalizedDetents.lastIndex.coerceAtLeast(0)).toFloat(),
            steps = (normalizedDetents.size - 2).coerceAtLeast(0),
        )
    }
}

private fun diagnosticRowValue(
    diagnostics: NaviampDiagnosticsUi,
    sectionTitle: String,
    label: String,
): String? =
    diagnostics.sections
        .firstOrNull { section -> section.title == sectionTitle }
        ?.rows
        ?.firstOrNull { row -> row.first == label }
        ?.second

@Composable
private fun PrimarySettingsButton(
    label: String,
    colors: NaviampColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(label, color = if (enabled) colors.primaryText else colors.mutedText)
    }
}

private enum class SharedLocalDataAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
) {
    ClearCache(
        title = "Clear Cache",
        message = "This removes cached images, provider responses, prefetched audio, waveforms, and lyrics. Saved servers and the library index stay intact.",
        confirmLabel = "Clear cache",
    ),
    ClearLibrary(
        title = "Clear Library Index",
        message = "This removes the local library index for the active server. You can sync the library again after this finishes.",
        confirmLabel = "Clear library",
    ),
    ResetDatabase(
        title = "Reset Database",
        message = "This removes saved servers, local cache, downloads, library data, playback history, and local settings stored in the app database.",
        confirmLabel = "Reset database",
    ),
}

@Composable
fun NaviampPlaybackSettingsSection(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    supportsEqualizer: Boolean,
    supportsSonicSimilarity: Boolean = false,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit = onPlaybackSettingsChanged,
    showReplayGain: Boolean = true,
    showCrossfade: Boolean = true,
    showQueueBehavior: Boolean = true,
    showDebugLogging: Boolean = true,
    showLrclibLyrics: Boolean = true,
    showMobileNetworkQuality: Boolean = false,
    downloadBytes: Long = 0L,
) {
    var selectedSection by remember { mutableStateOf<NaviampPlaybackSettingsSection?>(null) }

    selectedSection?.let { section ->
        SettingsSubsectionHeader(section.title, section.subtitle, colors) { selectedSection = null }
        when (section) {
            NaviampPlaybackSettingsSection.AudioQuality -> StreamingQualitySettings(
                colors = colors,
                playbackSettings = playbackSettings,
                showMobileNetworkQuality = showMobileNetworkQuality,
                downloadBytes = downloadBytes,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
            )
            NaviampPlaybackSettingsSection.ReplayGain -> ReplayGainSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsReplayGain = supportsReplayGain,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.GaplessCrossfade -> GaplessCrossfadeSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsGapless = supportsGapless,
                supportsCrossfade = supportsCrossfade,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.Equalizer -> EqualizerSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsEqualizer = supportsEqualizer,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.QueueRules -> QueueRulesSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsSonicSimilarity = supportsSonicSimilarity,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.DjBuilder -> RadioDjSettingsSection(
                colors = colors,
                playbackSettings = playbackSettings,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.LyricsRelated -> LyricsRelatedSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                supportsSonicSimilarity = supportsSonicSimilarity,
                showLrclibLyrics = showLrclibLyrics,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
            NaviampPlaybackSettingsSection.Debug -> DebugSettings(
                colors = colors,
                playbackSettings = playbackSettings,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
            )
        }
    } ?: run {
        SettingsSectionTitle("Playback", colors)
        playbackSettingsSections(
            showReplayGain = showReplayGain,
            showCrossfade = showCrossfade,
            showQueueBehavior = showQueueBehavior,
            showDebugLogging = showDebugLogging,
        ).forEach { section ->
            SettingsRow(section.title, section.subtitle, colors) {
                selectedSection = section
            }
        }
    }
}

private enum class NaviampPlaybackSettingsSection(
    val title: String,
    val subtitle: String,
) {
    AudioQuality("Audio quality", "Streaming, downloads, and network quality"),
    ReplayGain("ReplayGain", "Track and album loudness leveling"),
    GaplessCrossfade("Gapless and crossfade", "Album flow and transition timing"),
    Equalizer("Equalizer", "10-band EQ and saved profiles"),
    QueueRules("Queue rules", "Back To, Up Next, and queue-end behavior"),
    DjBuilder("DJ Builder", "Saved radio personalities and tuning presets"),
    LyricsRelated("Lyrics and Related", "Lyrics downloads and sonic similarity"),
    Debug("Debug", "Diagnostic logging"),
}

private fun playbackSettingsSections(
    showReplayGain: Boolean,
    showCrossfade: Boolean,
    showQueueBehavior: Boolean,
    showDebugLogging: Boolean,
): List<NaviampPlaybackSettingsSection> =
    buildList {
        add(NaviampPlaybackSettingsSection.AudioQuality)
        if (showReplayGain) add(NaviampPlaybackSettingsSection.ReplayGain)
        if (showCrossfade) add(NaviampPlaybackSettingsSection.GaplessCrossfade)
        add(NaviampPlaybackSettingsSection.Equalizer)
        if (showQueueBehavior) {
            add(NaviampPlaybackSettingsSection.QueueRules)
            add(NaviampPlaybackSettingsSection.DjBuilder)
        }
        add(NaviampPlaybackSettingsSection.LyricsRelated)
        if (showDebugLogging) add(NaviampPlaybackSettingsSection.Debug)
    }

@Composable
private fun SettingsSubsectionHeader(
    title: String,
    subtitle: String,
    colors: NaviampColors,
    onBack: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(NaviampIcons.Back, contentDescription = "Back", tint = colors.primaryText)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = colors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = colors.secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ReplayGainSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    Text(
        if (supportsReplayGain) "ReplayGain" else "ReplayGain unavailable with this playback engine",
        color = colors.secondaryText,
        fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ReplayGainMode.entries.forEach { mode ->
            FilterChip(
                selected = playbackSettings.replayGainMode == mode,
                enabled = supportsReplayGain || mode == ReplayGainMode.Off,
                onClick = { onPlaybackSettingsChanged(playbackSettings.copy(replayGainMode = mode)) },
                label = { Text(mode.displayName, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.replayGainInspectorEnabled,
        enabled = supportsReplayGain,
        label = "Show ReplayGain inspector in track details",
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(playbackSettings.copy(replayGainInspectorEnabled = enabled))
        },
    )
}

@Composable
private fun GaplessCrossfadeSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    Text(
        if (supportsGapless) "Gapless playback" else "Gapless playback unavailable with this playback engine",
        color = colors.secondaryText,
        fontSize = 12.sp,
    )
    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.gaplessEnabled && playbackSettings.crossfadeDurationSeconds == 0,
        enabled = supportsGapless,
        label = "Gapless",
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(
                playbackSettings.copy(
                    gaplessEnabled = enabled,
                    crossfadeDurationSeconds = if (enabled) 0 else playbackSettings.crossfadeDurationSeconds,
                ),
            )
        },
    )
    Text(
        if (supportsCrossfade) "Crossfade" else "Crossfade unavailable with this playback engine",
        color = colors.secondaryText,
        fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CrossfadeDurationOptions.forEach { seconds ->
            FilterChip(
                selected = playbackSettings.crossfadeDurationSeconds == seconds,
                enabled = supportsCrossfade || seconds == 0,
                onClick = {
                    onPlaybackSettingsChanged(
                        playbackSettings.copy(
                            crossfadeDurationSeconds = seconds,
                            gaplessEnabled = if (seconds > 0) false else playbackSettings.gaplessEnabled,
                        ),
                    )
                },
                label = { Text(if (seconds == 0) "Off" else "${seconds}s", fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
}

@Composable
private fun QueueRulesSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsSonicSimilarity: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var upNextHelpOpen by remember { mutableStateOf(false) }
    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.removePlayedTracksFromQueue,
        label = "Remove played tracks from Back To",
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(playbackSettings.copy(removePlayedTracksFromQueue = enabled))
        },
    )
    if (supportsSonicSimilarity) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.sonicAutoplayEnabled,
            label = "Start Sonic autoplay when queue ends",
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(sonicAutoplayEnabled = enabled))
            },
        )
    }
    Text("Previous button", color = colors.secondaryText, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PreviousButtonBehavior.entries.forEach { behavior ->
            FilterChip(
                selected = playbackSettings.previousButtonBehavior == behavior,
                onClick = {
                    onPlaybackSettingsChanged(playbackSettings.copy(previousButtonBehavior = behavior))
                },
                label = { Text(behavior.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Up Next selection", color = colors.secondaryText, fontSize = 12.sp)
        IconButton(
            onClick = { upNextHelpOpen = true },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = NaviampIcons.Info,
                contentDescription = "Up Next selection details",
                tint = colors.secondaryText,
                modifier = Modifier.size(15.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        UpNextSelectionBehavior.entries.forEach { behavior ->
            FilterChip(
                selected = playbackSettings.upNextSelectionBehavior == behavior,
                onClick = {
                    onPlaybackSettingsChanged(playbackSettings.copy(upNextSelectionBehavior = behavior))
                },
                label = { Text(behavior.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    if (upNextHelpOpen) {
        AlertDialog(
            onDismissRequest = { upNextHelpOpen = false },
            title = { Text("Up Next selection") },
            text = {
                Text(
                    "Move selected plays the clicked song now and keeps the songs before it in Up Next.\n\n" +
                        "Skip to selected advances through the queue, so skipped songs move into Back To.",
                )
            },
            confirmButton = {
                TextButton(onClick = { upNextHelpOpen = false }) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun RadioTuningControls(
    colors: NaviampColors,
    tuning: RadioTuningSettings,
    onTuningChanged: (RadioTuningSettings) -> Unit,
) {
    Text("Familiarity", color = colors.secondaryText, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RadioFamiliarity.entries.forEach { familiarity ->
            FilterChip(
                selected = tuning.familiarity == familiarity,
                onClick = { onTuningChanged(tuning.copy(familiarity = familiarity)) },
                label = { Text(familiarity.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    Text("Artist spread", color = colors.secondaryText, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RadioArtistSpread.entries.forEach { spread ->
            FilterChip(
                selected = tuning.artistSpread == spread,
                onClick = { onTuningChanged(tuning.copy(artistSpread = spread)) },
                label = { Text(spread.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    SettingsCheckboxRow(
        colors = colors,
        checked = tuning.sameDecadeOnly,
        label = "Stay in seed track decade",
        onCheckedChange = { enabled ->
            onTuningChanged(tuning.copy(sameDecadeOnly = enabled))
        },
    )
    Text("Artist runs", color = colors.secondaryText, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RadioArtistRunMode.entries.forEach { mode ->
            FilterChip(
                selected = tuning.artistRunMode == mode,
                onClick = { onTuningChanged(tuning.copy(artistRunMode = mode)) },
                label = { Text(mode.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    if (tuning.artistRunMode == RadioArtistRunMode.ArtistBlocks) {
        SettingsNumberSlider(
            colors = colors,
            label = "Same artist run",
            value = tuning.sameArtistRunLength,
            onValueChanged = { value -> onTuningChanged(tuning.copy(sameArtistRunLength = value)) },
        )
        SettingsNumberSlider(
            colors = colors,
            label = "Other artists run",
            value = tuning.otherArtistRunLength,
            onValueChanged = { value -> onTuningChanged(tuning.copy(otherArtistRunLength = value)) },
        )
    }
}

@Composable
private fun SettingsNumberSlider(
    colors: NaviampColors,
    label: String,
    value: Int,
    onValueChanged: (Int) -> Unit,
) {
    val normalized = value.coerceIn(MinArtistRunLength, MaxArtistRunLength)
    Text("$label: $normalized", color = colors.secondaryText, fontSize = 12.sp)
    Slider(
        value = normalized.toFloat(),
        onValueChange = { onValueChanged(it.toInt().coerceIn(MinArtistRunLength, MaxArtistRunLength)) },
        valueRange = MinArtistRunLength.toFloat()..MaxArtistRunLength.toFloat(),
        steps = MaxArtistRunLength - MinArtistRunLength - 1,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RadioDjSettingsSection(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftTuning by remember { mutableStateOf(playbackSettings.radioTuning) }
    val editingPreset = editingId?.let { id -> playbackSettings.radioDjs.firstOrNull { it.id == id } }

    if (playbackSettings.radioDjs.isEmpty()) {
        Text("No DJs saved yet.", color = colors.secondaryText, fontSize = 12.sp)
    } else {
        playbackSettings.radioDjs.forEach { preset ->
            SettingsRow(preset.name, preset.tuning.summaryLabel(), colors) {
                editingId = preset.id
                draftName = preset.name
                draftTuning = preset.tuning
            }
        }
    }
    PrimarySettingsButton("New DJ", colors, enabled = true) {
        editingId = NewRadioDjId
        draftName = ""
        draftTuning = playbackSettings.radioTuning
    }

    if (editingId != null) {
        SettingsSectionTitle(if (editingPreset == null) "New DJ" else "Edit DJ", colors)
        OutlinedTextField(
            value = draftName,
            onValueChange = { draftName = it },
            singleLine = true,
            label = { Text("DJ name") },
            modifier = Modifier.fillMaxWidth(),
        )
        RadioTuningControls(
            colors = colors,
            tuning = draftTuning,
            onTuningChanged = { draftTuning = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = draftName.isNotBlank(),
                onClick = {
                    val preset = RadioDjPreset(
                        id = editingPreset?.id ?: radioDjIdFor(draftName, playbackSettings.radioDjs),
                        name = draftName,
                        tuning = draftTuning,
                    ).normalized()
                    val updated = if (editingPreset == null) {
                        playbackSettings.radioDjs + preset
                    } else {
                        playbackSettings.radioDjs.map { if (it.id == preset.id) preset else it }
                    }
                    onPlaybackSettingsChanged(
                        playbackSettings.copy(
                            radioDjs = updated,
                            activeRadioDjId = playbackSettings.activeRadioDjId?.takeIf { id ->
                                updated.any { it.id == id }
                            },
                        ),
                    )
                    editingId = null
                },
            ) {
                Text("Save", color = if (draftName.isNotBlank()) colors.primaryText else colors.mutedText)
            }
            if (editingPreset != null) {
                TextButton(
                    onClick = {
                        val updated = playbackSettings.radioDjs.filterNot { it.id == editingPreset.id }
                        onPlaybackSettingsChanged(
                            playbackSettings.copy(
                                radioDjs = updated,
                                activeRadioDjId = playbackSettings.activeRadioDjId?.takeIf { it != editingPreset.id },
                            ),
                        )
                        editingId = null
                    },
                ) {
                    Text("Delete", color = colors.primaryText)
                }
            }
            TextButton(onClick = { editingId = null }) {
                Text("Cancel", color = colors.secondaryText)
            }
        }
    }
}

@Composable
private fun LyricsRelatedSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsSonicSimilarity: Boolean,
    showLrclibLyrics: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    if (showLrclibLyrics) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.lrclibLyricsEnabled,
            label = "Download lyrics for tracks",
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(lrclibLyricsEnabled = enabled))
            },
        )
    }
    if (supportsSonicSimilarity) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.sonicSimilarityEnabled,
            label = "Use Navidrome sonic similarity for Related tracks",
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(sonicSimilarityEnabled = enabled))
            },
        )
    }
}

@Composable
private fun DebugSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    SettingsCheckboxRow(
        colors = colors,
        checked = playbackSettings.debugLoggingEnabled,
        label = "Debug logging",
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(playbackSettings.copy(debugLoggingEnabled = enabled))
        },
    )
}

@Composable
private fun EqualizerSettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    supportsEqualizer: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    val equalizer = playbackSettings.equalizer.normalized()
    val activeProfile = equalizer.savedProfiles.firstOrNull { it.id == equalizer.profileId }
    var profileDialogOpen by remember { mutableStateOf(false) }
    var profileName by remember(equalizer.profileId, activeProfile?.name) {
        mutableStateOf(activeProfile?.name.orEmpty())
    }
    Text(
        if (supportsEqualizer) "10-band equalizer" else "Equalizer unavailable with this playback engine",
        color = colors.secondaryText,
        fontSize = 12.sp,
    )
    SettingsCheckboxRow(
        colors = colors,
        checked = equalizer.enabled,
        enabled = supportsEqualizer,
        label = "Enabled",
        onCheckedChange = { enabled ->
            onPlaybackSettingsChanged(
                playbackSettings.copy(equalizer = equalizer.copy(enabled = enabled).normalized()),
            )
        },
    )
    if (equalizer.savedProfiles.isNotEmpty()) {
        SettingsDropdown(
            colors = colors,
            label = activeProfile?.name ?: "Saved profile",
            options = equalizer.savedProfiles,
            optionLabel = { it.name },
            enabled = supportsEqualizer,
            onOptionSelected = { profile ->
                onPlaybackSettingsChanged(playbackSettings.copy(equalizer = equalizer.withProfile(profile)))
            },
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsDropdown(
            colors = colors,
            label = equalizer.preset.displayName,
            options = EqualizerPreset.entries,
            optionLabel = { it.displayName },
            enabled = supportsEqualizer,
            onOptionSelected = { preset ->
                onPlaybackSettingsChanged(playbackSettings.copy(equalizer = equalizer.withPreset(preset)))
            },
        )
        TextButton(
            enabled = supportsEqualizer,
            onClick = {
                onPlaybackSettingsChanged(playbackSettings.copy(equalizer = equalizer.withPreset(EqualizerPreset.Flat)))
            },
        ) {
            Text("Reset", color = if (supportsEqualizer) colors.primaryText else colors.mutedText, fontSize = 12.sp)
        }
        TextButton(
            enabled = supportsEqualizer,
            onClick = {
                profileName = activeProfile?.name.orEmpty()
                profileDialogOpen = true
            },
        ) {
            Text(
                if (activeProfile == null) "Save profile" else "Rename profile",
                color = if (supportsEqualizer) colors.primaryText else colors.mutedText,
                fontSize = 12.sp,
            )
        }
    }
    EqualizerBandFrequencies.forEachIndexed { index, frequency ->
        val gain = equalizer.bandsDb.getOrNull(index) ?: 0f
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(frequency.equalizerFrequencyLabel(), color = colors.secondaryText, fontSize = 11.sp)
                Text(gain.equalizerGainLabel(), color = colors.secondaryText, fontSize = 11.sp)
            }
            Slider(
                enabled = supportsEqualizer,
                value = gain,
                onValueChange = { value ->
                    onPlaybackSettingsChanged(
                        playbackSettings.copy(equalizer = equalizer.withBandGain(index, value)),
                    )
                },
                valueRange = MinEqualizerGainDb..MaxEqualizerGainDb,
                steps = 47,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (profileDialogOpen) {
        AlertDialog(
            onDismissRequest = { profileDialogOpen = false },
            title = { Text(if (activeProfile == null) "Save EQ profile" else "Rename EQ profile") },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    singleLine = true,
                    label = { Text("Profile name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = profileName.isNotBlank(),
                    onClick = {
                        profileDialogOpen = false
                        onPlaybackSettingsChanged(
                            playbackSettings.copy(equalizer = equalizer.savedAsProfile(profileName)),
                        )
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StreamingQualitySettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    showMobileNetworkQuality: Boolean,
    downloadBytes: Long,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
) {
    var pendingDownloadQualitySettings by remember { mutableStateOf<PlaybackSettings?>(null) }

    SettingsSectionTitle("Streaming quality", colors)
    StreamQualityPreferenceRow(
        colors = colors,
        label = "Wi-Fi / wired",
        preference = playbackSettings.wifiStreamingQuality,
        onPreferenceChanged = { preference ->
            onPlaybackSettingsChanged(playbackSettings.copy(wifiStreamingQuality = preference))
        },
    )
    if (showMobileNetworkQuality) {
        StreamQualityPreferenceRow(
            colors = colors,
            label = "Mobile data",
            preference = playbackSettings.mobileStreamingQuality,
            onPreferenceChanged = { preference ->
                onPlaybackSettingsChanged(playbackSettings.copy(mobileStreamingQuality = preference))
            },
        )
    }
    SettingsSectionTitle("Downloads", colors)
    StreamQualityPreferenceRow(
        colors = colors,
        label = "Saved files",
        preference = playbackSettings.downloadQuality,
        onPreferenceChanged = { preference ->
            val updated = playbackSettings.copy(downloadQuality = preference)
            if (downloadBytes > 0L && preference != playbackSettings.downloadQuality) {
                pendingDownloadQualitySettings = updated
            } else {
                onPlaybackSettingsChanged(updated)
            }
        },
    )
    if (showMobileNetworkQuality) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.allowMobileDownloads,
            label = "Allow downloads on mobile data",
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(allowMobileDownloads = enabled))
            },
        )
    }
    pendingDownloadQualitySettings?.let { pendingSettings ->
        AlertDialog(
            onDismissRequest = { pendingDownloadQualitySettings = null },
            title = { Text("Change saved file quality?") },
            text = {
                Text("Existing downloads will stay in their current quality. New downloads will use the updated setting.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDownloadQualitySettings = null
                        onPlaybackSettingsChanged(pendingSettings)
                    },
                ) {
                    Text("Keep existing")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            pendingDownloadQualitySettings = null
                            onPlaybackSettingsChangedAndRedownload(pendingSettings)
                        },
                    ) {
                        Text("Re-download")
                    }
                    TextButton(onClick = { pendingDownloadQualitySettings = null }) {
                        Text("Cancel")
                    }
                }
            },
        )
    }
}

@Composable
private fun StreamQualityPreferenceRow(
    colors: NaviampColors,
    label: String,
    preference: StreamQualityPreference,
    onPreferenceChanged: (StreamQualityPreference) -> Unit,
) {
    val normalized = preference.normalized()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = colors.secondaryText, fontSize = 12.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsDropdown(
                colors = colors,
                label = normalized.mode.label,
                options = StreamQualityMode.entries,
                optionLabel = { it.label },
                onOptionSelected = { mode -> onPreferenceChanged(normalized.copy(mode = mode)) },
            )
            if (normalized.mode == StreamQualityMode.Transcode) {
                SettingsDropdown(
                    colors = colors,
                    label = normalized.codec.label,
                    options = StreamingCodec.entries,
                    optionLabel = { it.label },
                    onOptionSelected = { codec -> onPreferenceChanged(normalized.copy(codec = codec)) },
                )
                SettingsDropdown(
                    colors = colors,
                    label = "${normalized.bitrateKbps} kbps",
                    options = StreamBitrateKbpsOptions,
                    optionLabel = { "$it kbps" },
                    onOptionSelected = { bitrate -> onPreferenceChanged(normalized.copy(bitrateKbps = bitrate)) },
                )
            }
        }
    }
}

@Composable
private fun <T> SettingsDropdown(
    colors: NaviampColors,
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    enabled: Boolean = true,
    onOptionSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(enabled = enabled, onClick = { expanded = true }) {
        Text(label, color = if (enabled) colors.primaryText else colors.mutedText, fontSize = 12.sp)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsCheckboxRow(
    colors: NaviampColors,
    checked: Boolean,
    enabled: Boolean = true,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Text(label, color = colors.secondaryText, fontSize = 12.sp)
    }
}

@Composable
fun SettingsSectionTitle(title: String, colors: NaviampColors) {
    Text(title, color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

@Composable
fun SettingsRow(title: String, subtitle: String, colors: NaviampColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.primaryText, fontSize = 15.sp)
            Text(subtitle, color = colors.mutedText, fontSize = 12.sp)
        }
        Icon(NaviampIcons.ChevronRight, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(18.dp))
    }
}

private val PreviousButtonBehavior.label: String
    get() = when (this) {
        PreviousButtonBehavior.RestartThenPrevious -> "Restart first"
        PreviousButtonBehavior.AlwaysPrevious -> "Always previous"
    }

private val UpNextSelectionBehavior.label: String
    get() = when (this) {
        UpNextSelectionBehavior.MoveSelectedToCurrent -> "Move selected"
        UpNextSelectionBehavior.SkipToSelected -> "Skip to selected"
    }

private val StreamQualityMode.label: String
    get() = when (this) {
        StreamQualityMode.Original -> "Full quality"
        StreamQualityMode.Transcode -> "Encode"
    }

private val StreamingCodec.label: String
    get() = when (this) {
        StreamingCodec.Mp3 -> "MP3"
        StreamingCodec.Aac -> "AAC"
        StreamingCodec.Opus -> "Opus"
    }

private fun RadioTuningSettings.summaryLabel(): String =
    listOf(
        familiarity.label,
        artistSpread.label,
        if (sameDecadeOnly) "same decade" else "any decade",
        when (artistRunMode) {
            RadioArtistRunMode.Mixed -> "mixed artists"
            RadioArtistRunMode.SingleArtist -> "single artist"
            RadioArtistRunMode.ArtistBlocks -> "${sameArtistRunLength} same / ${otherArtistRunLength} other"
        },
    ).joinToString(" / ")

private fun radioDjIdFor(name: String, existing: List<RadioDjPreset>): String {
    val base = name.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "dj" }
    if (existing.none { it.id == base }) return base
    var index = 2
    while (existing.any { it.id == "$base-$index" }) {
        index += 1
    }
    return "$base-$index"
}

private fun Int.equalizerFrequencyLabel(): String =
    if (this >= 1_000) "${this / 1_000} kHz" else "$this Hz"

private fun Float.equalizerGainLabel(): String =
    if (this == 0f) "0 dB" else "%+.1f dB".format(this)

private val CrossfadeDurationOptions = listOf(0, 3, 5, 8, 12)
private const val NewRadioDjId = "__new_radio_dj__"
private val PrefetchDepthOptions = listOf(0, 3, 5, 10, 15, 25)
private val WaveformBucketCountOptions = listOf(
    MinWaveformBucketCount,
    DefaultWaveformBucketCount,
    250,
    320,
    400,
    MaxWaveformBucketCount,
)
private val AudioCacheBudgetOptions = listOf(
    256L * 1024L * 1024L,
    512L * 1024L * 1024L,
    1L * 1024L * 1024L * 1024L,
    2L * 1024L * 1024L * 1024L,
    5L * 1024L * 1024L * 1024L,
    10L * 1024L * 1024L * 1024L,
    20L * 1024L * 1024L * 1024L,
)
private val DownloadBudgetOptions = listOf(
    512L * 1024L * 1024L,
    1L * 1024L * 1024L * 1024L,
    2L * 1024L * 1024L * 1024L,
    5L * 1024L * 1024L * 1024L,
    10L * 1024L * 1024L * 1024L,
    25L * 1024L * 1024L * 1024L,
    50L * 1024L * 1024L * 1024L,
    100L * 1024L * 1024L * 1024L,
)

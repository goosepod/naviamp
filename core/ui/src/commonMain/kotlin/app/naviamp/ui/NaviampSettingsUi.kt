package app.naviamp.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.playback.ReplayGainMode
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
}

@Composable
fun NaviampSharedSettingsContent(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    diagnostics: NaviampDiagnosticsUi = NaviampDiagnosticsUi(),
    onEditConnection: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onClearCache: (() -> Unit)? = null,
    onClearLibrary: (() -> Unit)? = null,
    onResetDatabase: (() -> Unit)? = null,
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
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
                    SettingsSectionTitle("Connections", colors)
                    SettingsRow("Navidrome server", "Edit server, credentials, and TLS options", colors, onEditConnection)
                }
                NaviampSettingsCategory.Playback -> NaviampPlaybackSettingsSection(
                    colors = colors,
                    playbackSettings = playbackSettings,
                    supportsReplayGain = supportsReplayGain,
                    supportsGapless = supportsGapless,
                    supportsCrossfade = supportsCrossfade,
                    showReplayGain = true,
                    showCrossfade = true,
                    showQueueBehavior = showQueueBehavior,
                    showDebugLogging = showDebugLogging,
                    showMobileNetworkQuality = showMobileNetworkQuality,
                    showLrclibLyrics = true,
                    onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                )
                NaviampSettingsCategory.Cache -> NaviampDiagnosticsSettingsSection(
                    colors = colors,
                    title = "Cache",
                    diagnostics = NaviampDiagnosticsUi(
                        diagnostics.sections.filter { section ->
                            section.title == "Storage" || section.title == "Library sync"
                        },
                    ),
                    emptyText = "Cache sizes will appear after the app initializes storage.",
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
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    showReplayGain: Boolean = true,
    showCrossfade: Boolean = true,
    showQueueBehavior: Boolean = true,
    showDebugLogging: Boolean = true,
    showLrclibLyrics: Boolean = true,
    showMobileNetworkQuality: Boolean = false,
) {
    var upNextHelpOpen by remember { mutableStateOf(false) }

    SettingsSectionTitle("Playback", colors)
    StreamingQualitySettings(
        colors = colors,
        playbackSettings = playbackSettings,
        showMobileNetworkQuality = showMobileNetworkQuality,
        onPlaybackSettingsChanged = onPlaybackSettingsChanged,
    )
    if (showReplayGain) {
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
    }
    if (showCrossfade) {
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
    if (showQueueBehavior) {
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
    }
    if (showDebugLogging) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.debugLoggingEnabled,
            label = "Debug logging",
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(debugLoggingEnabled = enabled))
            },
        )
    }
    if (showLrclibLyrics) {
        SettingsCheckboxRow(
            colors = colors,
            checked = playbackSettings.lrclibLyricsEnabled,
            label = "Use LRCLIB when lyrics are missing or unsynced",
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(lrclibLyricsEnabled = enabled))
            },
        )
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
private fun StreamingQualitySettings(
    colors: NaviampColors,
    playbackSettings: PlaybackSettings,
    showMobileNetworkQuality: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
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
            onPlaybackSettingsChanged(playbackSettings.copy(downloadQuality = preference))
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
    onOptionSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) {
        Text(label, color = colors.primaryText, fontSize = 12.sp)
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

private val CrossfadeDurationOptions = listOf(0, 3, 5, 8, 12)

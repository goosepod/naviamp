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
import app.naviamp.domain.settings.UpNextSelectionBehavior

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
    onEditConnection: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    supportsReplayGain: Boolean = false,
    supportsGapless: Boolean = true,
    supportsCrossfade: Boolean = false,
    showQueueBehavior: Boolean = true,
    showDebugLogging: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        SettingsRow("Connection", "Edit Navidrome server and TLS options", colors, onEditConnection)
        NaviampPlaybackSettingsSection(
            colors = colors,
            playbackSettings = playbackSettings,
            supportsReplayGain = supportsReplayGain,
            supportsGapless = supportsGapless,
            supportsCrossfade = supportsCrossfade,
            showReplayGain = true,
            showCrossfade = true,
            showQueueBehavior = showQueueBehavior,
            showDebugLogging = showDebugLogging,
            showLrclibLyrics = true,
            onPlaybackSettingsChanged = onPlaybackSettingsChanged,
        )
    }
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
) {
    var upNextHelpOpen by remember { mutableStateOf(false) }

    SettingsSectionTitle("Playback", colors)
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

private val CrossfadeDurationOptions = listOf(0, 3, 5, 8, 12)

package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.desktop.settings.PlaybackSettings

@Composable
fun PlaybackSettingsPanel(
    appColors: AppColors,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Playback Settings", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
        Text(
            if (supportsReplayGain) {
                "ReplayGain"
            } else {
                "ReplayGain unavailable with this playback engine"
            },
            color = appColors.secondaryText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReplayGainMode.entries.forEach { mode ->
                FilterChip(
                    selected = playbackSettings.replayGainMode == mode,
                    enabled = supportsReplayGain || mode == ReplayGainMode.Off,
                    onClick = {
                        onPlaybackSettingsChanged(playbackSettings.copy(replayGainMode = mode))
                    },
                    label = { Text(mode.displayName) },
                )
            }
        }
    }
}

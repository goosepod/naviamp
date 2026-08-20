package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.playback.DefaultPlaybackProfileCrossfadeSeconds
import app.naviamp.domain.playback.MaxPlaybackProfileCrossfadeSeconds
import app.naviamp.domain.playback.MinPlaybackProfileCrossfadeSeconds
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackReplayGainMode
import app.naviamp.domain.playback.PlaybackTransitionMode

@Composable
fun PlaybackProfileDialog(
    title: String,
    initialProfile: PlaybackProfile,
    colors: NaviampColors,
    onDismissRequest: () -> Unit,
    onSave: (PlaybackProfile) -> Unit,
) {
    val normalized = initialProfile.normalized()
    var transition by remember(title, normalized) { mutableStateOf(normalized.transitionMode) }
    var replayGain by remember(title, normalized) { mutableStateOf(normalized.replayGainMode) }
    var crossfadeSeconds by remember(title, normalized) {
        mutableStateOf(normalized.crossfadeDurationSeconds ?: DefaultPlaybackProfileCrossfadeSeconds)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = colors.controlSurface,
        title = { Text(title, color = colors.primaryText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "Choose only the values this sequence should override. Inherited values continue to follow global playback settings.",
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                )
                ProfileChoiceSection(
                    label = "Transitions",
                    choices = PlaybackTransitionMode.entries.map { mode ->
                        mode to when (mode) {
                            PlaybackTransitionMode.Inherit -> "Inherit"
                            PlaybackTransitionMode.Gapless -> "Gapless"
                            PlaybackTransitionMode.Crossfade -> "Crossfade"
                            PlaybackTransitionMode.Pause -> "Pause"
                        }
                    },
                    selected = transition,
                    colors = colors,
                    onSelected = { transition = it },
                )
                if (transition == PlaybackTransitionMode.Crossfade) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Crossfade duration", color = colors.secondaryText, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                enabled = crossfadeSeconds > MinPlaybackProfileCrossfadeSeconds,
                                onClick = { crossfadeSeconds-- },
                            ) { Text("−") }
                            Text("$crossfadeSeconds seconds", color = colors.primaryText, fontSize = 13.sp)
                            TextButton(
                                enabled = crossfadeSeconds < MaxPlaybackProfileCrossfadeSeconds,
                                onClick = { crossfadeSeconds++ },
                            ) { Text("+") }
                        }
                    }
                }
                ProfileChoiceSection(
                    label = "ReplayGain",
                    choices = PlaybackReplayGainMode.entries.map { mode ->
                        mode to when (mode) {
                            PlaybackReplayGainMode.Inherit -> "Inherit"
                            PlaybackReplayGainMode.Off -> "Off"
                            PlaybackReplayGainMode.Track -> "Track"
                            PlaybackReplayGainMode.Album -> "Album"
                        }
                    },
                    selected = replayGain,
                    colors = colors,
                    onSelected = { replayGain = it },
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onSave(PlaybackProfile())
                        onDismissRequest()
                    },
                ) { Text("Use global settings") }
                TextButton(onClick = onDismissRequest) { Text("Cancel") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PlaybackProfile(
                            transitionMode = transition,
                            crossfadeDurationSeconds = crossfadeSeconds.takeIf {
                                transition == PlaybackTransitionMode.Crossfade
                            },
                            replayGainMode = replayGain,
                        ),
                    )
                    onDismissRequest()
                },
            ) { Text("Save") }
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun <T> ProfileChoiceSection(
    label: String,
    choices: List<Pair<T, String>>,
    selected: T,
    colors: NaviampColors,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { (value, choiceLabel) ->
                val isSelected = value == selected
                Text(
                    text = choiceLabel,
                    color = if (isSelected) colors.primaryText else colors.secondaryText,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .background(
                            color = if (isSelected) colors.accent.copy(alpha = 0.34f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable { onSelected(value) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
}

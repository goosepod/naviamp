package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.desktop.settings.PlaybackSettings

@Composable
fun SettingsPanel(
    appColors: AppColors,
    serverUrl: String,
    username: String,
    password: String,
    hasSavedConnection: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = appColors.primaryText,
        unfocusedTextColor = appColors.primaryText,
        focusedLabelColor = appColors.primaryText,
        unfocusedLabelColor = appColors.secondaryText,
        cursorColor = appColors.primaryText,
        focusedBorderColor = appColors.accent,
        unfocusedBorderColor = appColors.border,
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", color = appColors.primaryText, style = MaterialTheme.typography.titleMedium)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Server", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
            if (hasSavedConnection) {
                Text("Saved connection loaded. Leave password blank to reuse it.", color = appColors.mutedText)
            }

            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChanged,
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = { onConnect() },
                ),
                colors = textFieldColors,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        onDone = { onConnect() },
                    ),
                    colors = textFieldColors,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    label = { Text(if (hasSavedConnection) "Password (optional)" else "Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConnect() }),
                    colors = textFieldColors,
                )
            }

            Button(
                enabled = !isConnecting,
                onClick = onConnect,
            ) {
                Text(if (isConnecting) "Connecting" else "Connect")
            }

            connectionStatus?.let {
                Text(it, color = appColors.secondaryText)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Playback", color = appColors.primaryText, fontWeight = FontWeight.SemiBold)
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
}

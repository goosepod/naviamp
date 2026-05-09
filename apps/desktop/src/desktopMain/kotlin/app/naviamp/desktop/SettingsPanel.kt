package app.naviamp.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
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

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Settings", color = appColors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Server", color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (hasSavedConnection) {
                Text(
                    "Saved connection loaded. Leave password blank to reuse it.",
                    color = appColors.mutedText,
                    fontSize = 12.sp,
                )
            }

            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChanged,
                label = { Text("Server URL") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = { onConnect() },
                ),
                colors = textFieldColors,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    label = { Text("Username") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
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
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConnect() }),
                    colors = textFieldColors,
                )
            }

            Button(
                enabled = !isConnecting,
                onClick = onConnect,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Text(if (isConnecting) "Connecting" else "Connect")
            }

            connectionStatus?.let {
                Text(it, color = appColors.secondaryText, fontSize = 12.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Playback", color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(
                if (supportsReplayGain) {
                    "ReplayGain"
                } else {
                    "ReplayGain unavailable with this playback engine"
                },
                color = appColors.secondaryText,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ReplayGainMode.entries.forEach { mode ->
                    FilterChip(
                        selected = playbackSettings.replayGainMode == mode,
                        enabled = supportsReplayGain || mode == ReplayGainMode.Off,
                        onClick = {
                            onPlaybackSettingsChanged(playbackSettings.copy(replayGainMode = mode))
                        },
                        label = { Text(mode.displayName, fontSize = 12.sp) },
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
        }
    }
}

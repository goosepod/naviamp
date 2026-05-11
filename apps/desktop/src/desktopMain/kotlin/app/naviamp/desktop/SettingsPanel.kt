package app.naviamp.desktop

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.pointer.pointerInput
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
    supportsCrossfade: Boolean,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onOpenStatsForNerds: () -> Unit,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var statusClickCount by remember { mutableIntStateOf(0) }
    var lastStatusClickMillis by remember { mutableStateOf(0L) }
    var clearCacheDialogOpen by remember { mutableStateOf(false) }
    var clearLibraryDialogOpen by remember { mutableStateOf(false) }
    var resetDialogOpen by remember { mutableStateOf(false) }
    var resetIncludesServers by remember { mutableStateOf(false) }
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
                Text(
                    it,
                    color = appColors.secondaryText,
                    fontSize = 12.sp,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            val now = System.currentTimeMillis()
                            statusClickCount = if (now - lastStatusClickMillis <= 650L) {
                                statusClickCount + 1
                            } else {
                                1
                            }
                            lastStatusClickMillis = now
                            if (statusClickCount >= 3) {
                                onOpenStatsForNerds()
                                statusClickCount = 0
                            }
                        }
                    },
                )
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
            Text(
                if (supportsCrossfade) {
                    "Crossfade"
                } else {
                    "Crossfade unavailable with this playback engine"
                },
                color = appColors.secondaryText,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0, 3, 5, 8, 12).forEach { seconds ->
                    FilterChip(
                        selected = playbackSettings.crossfadeDurationSeconds == seconds,
                        enabled = supportsCrossfade || seconds == 0,
                        onClick = {
                            onPlaybackSettingsChanged(
                                playbackSettings.copy(crossfadeDurationSeconds = seconds),
                            )
                        },
                        label = {
                            Text(
                                if (seconds == 0) "Off" else "${seconds}s",
                                fontSize = 12.sp,
                            )
                        },
                        modifier = Modifier.height(28.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = playbackSettings.debugLoggingEnabled,
                    onCheckedChange = { enabled ->
                        onPlaybackSettingsChanged(playbackSettings.copy(debugLoggingEnabled = enabled))
                    },
                )
                Text("Debug logging", color = appColors.secondaryText, fontSize = 12.sp)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Local Data", color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { clearCacheDialogOpen = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Clear cache", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { clearLibraryDialogOpen = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Clear library index", fontSize = 12.sp)
                }
                TextButton(
                    onClick = onRefreshLibrary,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Refresh library", fontSize = 12.sp)
                }
                TextButton(
                    onClick = {
                        resetIncludesServers = false
                        resetDialogOpen = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Reset database", fontSize = 12.sp)
                }
            }
        }
    }

    if (clearCacheDialogOpen) {
        AlertDialog(
            onDismissRequest = { clearCacheDialogOpen = false },
            title = { Text("Clear Cache") },
            text = { Text("This removes cached images, provider responses, and prefetched audio. Saved servers and the local library index stay intact.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearCacheDialogOpen = false
                        onClearCache()
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearCacheDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (clearLibraryDialogOpen) {
        AlertDialog(
            onDismissRequest = { clearLibraryDialogOpen = false },
            title = { Text("Clear Library Index") },
            text = { Text("This removes locally indexed artists, albums, and tracks. Saved servers and cached images stay intact.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearLibraryDialogOpen = false
                        onClearLibrary()
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearLibraryDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (resetDialogOpen) {
        AlertDialog(
            onDismissRequest = { resetDialogOpen = false },
            title = { Text("Reset Database") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This removes cache, library data, saved servers, and playback session data.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Checkbox(
                            checked = resetIncludesServers,
                            onCheckedChange = { resetIncludesServers = it },
                        )
                        Text("I understand this removes saved servers.", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = resetIncludesServers,
                    onClick = {
                        resetDialogOpen = false
                        onResetDatabase()
                    },
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

package app.naviamp.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings

@Composable
fun SettingsPanel(
    appColors: AppColors,
    serverUrl: String,
    username: String,
    password: String,
    savedConnections: List<SavedMediaSource>,
    currentSourceId: String?,
    hasSavedConnection: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings,
    cacheStats: CacheStats,
    supportsReplayGain: Boolean,
    supportsCrossfade: Boolean,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (SavedMediaSource) -> Unit,
    onConnectSavedConnection: (SavedMediaSource) -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
    onOpenStatsForNerds: () -> Unit,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableStateOf(SettingsCategory.Connections) }
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", color = appColors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(150.dp),
            ) {
                SettingsCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.label, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                when (selectedCategory) {
                    SettingsCategory.Connections -> ConnectionsSettings(
                        appColors = appColors,
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        savedConnections = savedConnections,
                        currentSourceId = currentSourceId,
                        hasSavedConnection = hasSavedConnection,
                        isConnecting = isConnecting,
                        connectionStatus = connectionStatus,
                        textFieldColors = textFieldColors,
                        focusManager = focusManager,
                        onServerUrlChanged = onServerUrlChanged,
                        onUsernameChanged = onUsernameChanged,
                        onPasswordChanged = onPasswordChanged,
                        onConnect = onConnect,
                        onNewConnection = onNewConnection,
                        onEditConnection = onEditConnection,
                        onConnectSavedConnection = onConnectSavedConnection,
                    )
                    SettingsCategory.Playback -> PlaybackSettingsSection(
                        appColors = appColors,
                        playbackSettings = playbackSettings,
                        supportsReplayGain = supportsReplayGain,
                        supportsCrossfade = supportsCrossfade,
                        onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                    )
                    SettingsCategory.Cache -> CacheSettingsSection(
                        appColors = appColors,
                        cacheSettings = cacheSettings,
                        cacheStats = cacheStats,
                        onCacheSettingsChanged = onCacheSettingsChanged,
                    )
                    SettingsCategory.LocalData -> LocalDataSettings(
                        appColors = appColors,
                        onClearCache = { clearCacheDialogOpen = true },
                        onClearLibrary = { clearLibraryDialogOpen = true },
                        onRefreshLibrary = onRefreshLibrary,
                        onResetDatabase = {
                            resetIncludesServers = false
                            resetDialogOpen = true
                        },
                    )
                    SettingsCategory.Diagnostics -> DiagnosticsSettings(
                        appColors = appColors,
                        connectionStatus = connectionStatus,
                        statusClickCount = statusClickCount,
                        lastStatusClickMillis = lastStatusClickMillis,
                        onStatusClickStateChanged = { count, millis ->
                            statusClickCount = count
                            lastStatusClickMillis = millis
                        },
                        onOpenStatsForNerds = onOpenStatsForNerds,
                    )
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

@Composable
private fun ConnectionsSettings(
    appColors: AppColors,
    serverUrl: String,
    username: String,
    password: String,
    savedConnections: List<SavedMediaSource>,
    currentSourceId: String?,
    hasSavedConnection: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    textFieldColors: androidx.compose.material3.TextFieldColors,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (SavedMediaSource) -> Unit,
    onConnectSavedConnection: (SavedMediaSource) -> Unit,
) {
    SettingsSectionTitle("Connections", appColors)
    if (savedConnections.isEmpty()) {
        Text("No saved connections yet.", color = appColors.secondaryText, fontSize = 12.sp)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            savedConnections.forEach { connection ->
                SavedConnectionRow(
                    appColors = appColors,
                    connection = connection,
                    selected = connection.id == currentSourceId,
                    enabled = !isConnecting,
                    onEdit = { onEditConnection(connection) },
                    onConnect = { onConnectSavedConnection(connection) },
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onNewConnection,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(30.dp),
        ) {
            Text("New connection", fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = appColors.border)
    SettingsSectionTitle("Connection Details", appColors)
    if (hasSavedConnection) {
        Text(
            "Saved credentials loaded. Leave password blank to reuse them.",
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            enabled = !isConnecting,
            onClick = onConnect,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(30.dp),
        ) {
            Text(if (isConnecting) "Connecting" else "Save and connect", fontSize = 12.sp)
        }
        connectionStatus?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SavedConnectionRow(
    appColors: AppColors,
    connection: SavedMediaSource,
    selected: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onConnect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) appColors.accent else appColors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    connection.displayName,
                    color = appColors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Text("Current", color = appColors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                "${connection.username} • ${connection.baseUrl}",
                color = appColors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            enabled = enabled,
            onClick = onEdit,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(28.dp),
        ) {
            Text("Edit", fontSize = 12.sp)
        }
        Button(
            enabled = enabled,
            onClick = onConnect,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = Modifier.height(28.dp),
        ) {
            Text(if (selected) "Reconnect" else "Connect", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PlaybackSettingsSection(
    appColors: AppColors,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean,
    supportsCrossfade: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    SettingsSectionTitle("Playback", appColors)
    Text(
        if (supportsReplayGain) "ReplayGain" else "ReplayGain unavailable with this playback engine",
        color = appColors.secondaryText,
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
    Text(
        if (supportsCrossfade) "Crossfade" else "Crossfade unavailable with this playback engine",
        color = appColors.secondaryText,
        fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0, 3, 5, 8, 12).forEach { seconds ->
            FilterChip(
                selected = playbackSettings.crossfadeDurationSeconds == seconds,
                enabled = supportsCrossfade || seconds == 0,
                onClick = {
                    onPlaybackSettingsChanged(playbackSettings.copy(crossfadeDurationSeconds = seconds))
                },
                label = { Text(if (seconds == 0) "Off" else "${seconds}s", fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = playbackSettings.debugLoggingEnabled,
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(debugLoggingEnabled = enabled))
            },
        )
        Text("Debug logging", color = appColors.secondaryText, fontSize = 12.sp)
    }
}

@Composable
private fun LocalDataSettings(
    appColors: AppColors,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    SettingsSectionTitle("Local Data", appColors)
    Text(
        "Manage local cache, indexed library data, and database reset actions.",
        color = appColors.secondaryText,
        fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onClearCache,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier.height(30.dp),
        ) { Text("Clear cache", fontSize = 12.sp) }
        TextButton(
            onClick = onClearLibrary,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier.height(30.dp),
        ) { Text("Clear library index", fontSize = 12.sp) }
        TextButton(
            onClick = onRefreshLibrary,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier.height(30.dp),
        ) { Text("Refresh library", fontSize = 12.sp) }
        TextButton(
            onClick = onResetDatabase,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier.height(30.dp),
        ) { Text("Reset database", fontSize = 12.sp) }
    }
}

@Composable
private fun CacheSettingsSection(
    appColors: AppColors,
    cacheSettings: CacheSettings,
    cacheStats: CacheStats,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
) {
    SettingsSectionTitle("Cache", appColors)
    Text(
        "Audio cache: ${cacheStats.audioCount} files, ${cacheStats.audioBytes.settingsBytesLabel()} used of " +
            cacheSettings.maxAudioCacheBytes.settingsBytesLabel(),
        color = appColors.secondaryText,
        fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = cacheSettings.audioCachingEnabled,
            onCheckedChange = { enabled ->
                onCacheSettingsChanged(cacheSettings.copy(audioCachingEnabled = enabled).normalized())
            },
        )
        Text("Enable audio cache and prefetch", color = appColors.secondaryText, fontSize = 12.sp)
    }
    Text("Prefetch depth", color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0, 3, 5, 10, 15, 25).forEach { depth ->
            FilterChip(
                selected = cacheSettings.audioPrefetchDepth == depth,
                enabled = cacheSettings.audioCachingEnabled,
                onClick = {
                    onCacheSettingsChanged(cacheSettings.copy(audioPrefetchDepth = depth).normalized())
                },
                label = { Text(if (depth == 0) "Off" else depth.toString(), fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
    Text("Audio cache budget", color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AudioCacheBudgetOptions.forEach { option ->
            FilterChip(
                selected = cacheSettings.maxAudioCacheBytes == option.bytes,
                onClick = {
                    onCacheSettingsChanged(cacheSettings.copy(maxAudioCacheBytes = option.bytes).normalized())
                },
                label = { Text(option.label, fontSize = 12.sp) },
                modifier = Modifier.height(28.dp),
            )
        }
    }
}

@Composable
private fun DiagnosticsSettings(
    appColors: AppColors,
    connectionStatus: String?,
    statusClickCount: Int,
    lastStatusClickMillis: Long,
    onStatusClickStateChanged: (Int, Long) -> Unit,
    onOpenStatsForNerds: () -> Unit,
) {
    SettingsSectionTitle("Diagnostics", appColors)
    connectionStatus?.let {
        Text(
            it,
            color = appColors.secondaryText,
            fontSize = 12.sp,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures {
                    val now = System.currentTimeMillis()
                    val nextCount = if (now - lastStatusClickMillis <= 650L) statusClickCount + 1 else 1
                    if (nextCount >= 3) {
                        onOpenStatsForNerds()
                        onStatusClickStateChanged(0, now)
                    } else {
                        onStatusClickStateChanged(nextCount, now)
                    }
                }
            },
        )
    }
    TextButton(
        onClick = onOpenStatsForNerds,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        modifier = Modifier.height(24.dp),
    ) {
        Text("Stats for nerds", fontSize = 12.sp)
    }
}

@Composable
private fun SettingsSectionTitle(title: String, appColors: AppColors) {
    Text(title, color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

private fun Long.settingsBytesLabel(): String {
    val mib = 1024.0 * 1024.0
    val gib = mib * 1024.0
    return when {
        this >= gib -> "%.1f GB".format(this / gib)
        this >= mib -> "%.0f MB".format(this / mib)
        else -> "$this B"
    }
}

private data class AudioCacheBudgetOption(
    val label: String,
    val bytes: Long,
)

private val AudioCacheBudgetOptions = listOf(
    AudioCacheBudgetOption("512 MB", 512L * 1024L * 1024L),
    AudioCacheBudgetOption("1 GB", 1L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("2 GB", 2L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("5 GB", 5L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("10 GB", 10L * 1024L * 1024L * 1024L),
)

private enum class SettingsCategory(val label: String) {
    Connections("Connections"),
    Playback("Playback"),
    Cache("Cache"),
    LocalData("Local data"),
    Diagnostics("Diagnostics"),
}

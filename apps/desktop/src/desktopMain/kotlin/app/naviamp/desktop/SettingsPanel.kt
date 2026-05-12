package app.naviamp.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.PreviousButtonBehavior
import app.naviamp.desktop.settings.UpNextSelectionBehavior
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsPanel(
    appColors: AppColors,
    serverUrl: String,
    connectionName: String,
    username: String,
    password: String,
    insecureSkipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificateKeyStorePath: String,
    clientCertificateKeyStorePassword: String,
    savedConnections: List<SavedMediaSource>,
    currentSourceId: String?,
    hasSavedConnection: Boolean,
    isConnectionFormOpen: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings,
    cacheStats: CacheStats,
    supportsReplayGain: Boolean,
    supportsCrossfade: Boolean,
    onServerUrlChanged: (String) -> Unit,
    onConnectionNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onInsecureSkipTlsVerificationChanged: (Boolean) -> Unit,
    onCustomCertificatePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (SavedMediaSource) -> Unit,
    onDeleteConnection: (SavedMediaSource) -> Unit,
    onConnectSavedConnection: (SavedMediaSource) -> Unit,
    onCancelConnectionForm: () -> Unit,
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

    @Composable
    fun CategoryContent(category: SettingsCategory) {
        when (category) {
            SettingsCategory.Connections -> ConnectionsSettings(
                appColors = appColors,
                serverUrl = serverUrl,
                connectionName = connectionName,
                username = username,
                password = password,
                insecureSkipTlsVerification = insecureSkipTlsVerification,
                customCertificatePath = customCertificatePath,
                clientCertificateKeyStorePath = clientCertificateKeyStorePath,
                clientCertificateKeyStorePassword = clientCertificateKeyStorePassword,
                savedConnections = savedConnections,
                currentSourceId = currentSourceId,
                hasSavedConnection = hasSavedConnection,
                isConnectionFormOpen = isConnectionFormOpen,
                isConnecting = isConnecting,
                connectionStatus = connectionStatus,
                textFieldColors = textFieldColors,
                focusManager = focusManager,
                onServerUrlChanged = onServerUrlChanged,
                onConnectionNameChanged = onConnectionNameChanged,
                onUsernameChanged = onUsernameChanged,
                onPasswordChanged = onPasswordChanged,
                onInsecureSkipTlsVerificationChanged = onInsecureSkipTlsVerificationChanged,
                onCustomCertificatePathChanged = onCustomCertificatePathChanged,
                onClientCertificateKeyStorePathChanged = onClientCertificateKeyStorePathChanged,
                onClientCertificateKeyStorePasswordChanged = onClientCertificateKeyStorePasswordChanged,
                onConnect = onConnect,
                onNewConnection = onNewConnection,
                onEditConnection = onEditConnection,
                onDeleteConnection = onDeleteConnection,
                onConnectSavedConnection = onConnectSavedConnection,
                onCancelConnectionForm = onCancelConnectionForm,
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

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val narrowSettings = maxWidth < 560.dp
        var narrowCategory by remember { mutableStateOf<SettingsCategory?>(null) }
        val connectionSubtitle = savedConnections
            .firstOrNull { it.id == currentSourceId }
            ?.displayName
            ?: connectionStatus
            ?: "Not connected"

        if (narrowSettings) {
            val activeCategory = narrowCategory
            if (activeCategory == null) {
                SettingsCategoryList(
                    appColors = appColors,
                    connectionSubtitle = connectionSubtitle,
                    onCategorySelected = { narrowCategory = it },
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 10.dp),
                ) {
                    SettingsDetailHeader(
                        appColors = appColors,
                        title = activeCategory.label,
                        onBack = { narrowCategory = null },
                    )
                    CategoryContent(activeCategory)
                }
            }
        } else {
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
                        CategoryContent(selectedCategory)
                    }
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
private fun SettingsCategoryList(
    appColors: AppColors,
    connectionSubtitle: String,
    onCategorySelected: (SettingsCategory) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
    ) {
        Text("Settings", color = appColors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCategory.entries.forEach { category ->
            SettingsCategoryRow(
                appColors = appColors,
                category = category,
                subtitle = if (category == SettingsCategory.Connections) connectionSubtitle else category.subtitle,
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    appColors: AppColors,
    category: SettingsCategory,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = appColors.secondaryText,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.label,
                color = appColors.primaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = appColors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = NavigationIcons.ChevronDown,
            contentDescription = null,
            tint = appColors.secondaryText,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = -90f },
        )
    }
}

@Composable
private fun SettingsDetailHeader(
    appColors: AppColors,
    title: String,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = NavigationIcons.Back,
                contentDescription = "Back to settings",
                tint = appColors.primaryText,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(title, color = appColors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectionsSettings(
    appColors: AppColors,
    serverUrl: String,
    connectionName: String,
    username: String,
    password: String,
    insecureSkipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificateKeyStorePath: String,
    clientCertificateKeyStorePassword: String,
    savedConnections: List<SavedMediaSource>,
    currentSourceId: String?,
    hasSavedConnection: Boolean,
    isConnectionFormOpen: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    textFieldColors: androidx.compose.material3.TextFieldColors,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onServerUrlChanged: (String) -> Unit,
    onConnectionNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onInsecureSkipTlsVerificationChanged: (Boolean) -> Unit,
    onCustomCertificatePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (SavedMediaSource) -> Unit,
    onDeleteConnection: (SavedMediaSource) -> Unit,
    onConnectSavedConnection: (SavedMediaSource) -> Unit,
    onCancelConnectionForm: () -> Unit,
) {
    var connectionPendingDelete by remember { mutableStateOf<SavedMediaSource?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
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
                        onDelete = { connectionPendingDelete = connection },
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
        connectionStatus?.takeUnless { isConnectionFormOpen }?.let {
            Text(it, color = appColors.secondaryText, fontSize = 12.sp)
        }
        if (isConnectionFormOpen) {
            ConnectionForm(
                appColors = appColors,
                serverUrl = serverUrl,
                connectionName = connectionName,
                username = username,
                password = password,
                insecureSkipTlsVerification = insecureSkipTlsVerification,
                customCertificatePath = customCertificatePath,
                clientCertificateKeyStorePath = clientCertificateKeyStorePath,
                clientCertificateKeyStorePassword = clientCertificateKeyStorePassword,
                hasSavedConnection = hasSavedConnection,
                isConnecting = isConnecting,
                connectionStatus = connectionStatus,
                textFieldColors = textFieldColors,
                focusManager = focusManager,
                onServerUrlChanged = onServerUrlChanged,
                onConnectionNameChanged = onConnectionNameChanged,
                onUsernameChanged = onUsernameChanged,
                onPasswordChanged = onPasswordChanged,
                onInsecureSkipTlsVerificationChanged = onInsecureSkipTlsVerificationChanged,
                onCustomCertificatePathChanged = onCustomCertificatePathChanged,
                onClientCertificateKeyStorePathChanged = onClientCertificateKeyStorePathChanged,
                onClientCertificateKeyStorePasswordChanged = onClientCertificateKeyStorePasswordChanged,
                onConnect = onConnect,
                onCancelConnectionForm = onCancelConnectionForm,
            )
        }
        connectionPendingDelete?.let { connection ->
            DeleteConnectionDialog(
                connection = connection,
                onDismiss = { connectionPendingDelete = null },
                onConfirm = {
                    connectionPendingDelete = null
                    onDeleteConnection(connection)
                },
            )
        }
    }
}

@Composable
private fun ConnectionForm(
    appColors: AppColors,
    serverUrl: String,
    connectionName: String,
    username: String,
    password: String,
    insecureSkipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificateKeyStorePath: String,
    clientCertificateKeyStorePassword: String,
    hasSavedConnection: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    textFieldColors: androidx.compose.material3.TextFieldColors,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onServerUrlChanged: (String) -> Unit,
    onConnectionNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onInsecureSkipTlsVerificationChanged: (Boolean) -> Unit,
    onCustomCertificatePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePasswordChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onCancelConnectionForm: () -> Unit,
) {
    HorizontalDivider(color = appColors.border)
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingsSectionTitle("Connection Details", appColors)
        if (hasSavedConnection) {
            Text(
                "Saved credentials loaded. Leave password blank to reuse them.",
                color = appColors.mutedText,
                fontSize = 11.sp,
            )
        }
        CompactConnectionTextField(
            value = connectionName,
            onValueChange = onConnectionNameChanged,
            label = "Connection name (optional)",
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            appColors = appColors,
        )
        CompactConnectionTextField(
            value = serverUrl,
            onValueChange = onServerUrlChanged,
            label = "Server URL",
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Next) },
                onDone = { onConnect() },
            ),
            appColors = appColors,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            CompactConnectionTextField(
                value = username,
                onValueChange = onUsernameChanged,
                label = "Username",
                modifier = Modifier.weight(1f),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    onDone = { onConnect() },
                ),
                appColors = appColors,
            )
            CompactConnectionTextField(
                value = password,
                onValueChange = onPasswordChanged,
                label = if (hasSavedConnection) "Password (optional)" else "Password",
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConnect() }),
                appColors = appColors,
            )
        }
        SettingsSectionTitle("TLS", appColors)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = insecureSkipTlsVerification,
                onCheckedChange = onInsecureSkipTlsVerificationChanged,
            )
            Text("Skip TLS certificate verification", color = appColors.primaryText, fontSize = 11.sp)
        }
        CompactConnectionTextField(
            value = customCertificatePath,
            onValueChange = onCustomCertificatePathChanged,
            label = "Trusted certificate or CA file",
            enabled = !insecureSkipTlsVerification,
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            appColors = appColors,
        )
        SettingsSectionTitle("mTLS", appColors)
        CompactConnectionTextField(
            value = clientCertificateKeyStorePath,
            onValueChange = onClientCertificateKeyStorePathChanged,
            label = "Client certificate PKCS12 file",
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
            appColors = appColors,
        )
        CompactConnectionTextField(
            value = clientCertificateKeyStorePassword,
            onValueChange = onClientCertificateKeyStorePasswordChanged,
            label = "Client certificate password",
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConnect() }),
            appColors = appColors,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                enabled = !isConnecting,
                onClick = onConnect,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(if (isConnecting) "Connecting" else "Save and connect", fontSize = 11.sp)
            }
            TextButton(
                enabled = !isConnecting,
                onClick = onCancelConnectionForm,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text("Cancel", fontSize = 11.sp)
            }
            connectionStatus?.let {
                Text(it, color = appColors.secondaryText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun CompactConnectionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    appColors: AppColors,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    keyboardActions: KeyboardActions = KeyboardActions(),
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        Text(
            label,
            color = if (enabled) appColors.secondaryText else appColors.mutedText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            visualTransformation = visualTransformation,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = if (enabled) appColors.primaryText else appColors.mutedText,
                fontSize = 12.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .border(
                            width = 1.dp,
                            color = if (enabled) appColors.border else appColors.border.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 9.dp),
                ) {
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun SavedConnectionRow(
    appColors: AppColors,
    connection: SavedMediaSource,
    selected: Boolean,
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
        ConnectionActionIconButton(
            enabled = enabled,
            onClick = onEdit,
            icon = NavigationIcons.Edit,
            contentDescription = "Edit connection",
            appColors = appColors,
        )
        ConnectionActionIconButton(
            enabled = enabled,
            onClick = onDelete,
            icon = NavigationIcons.Trash,
            contentDescription = "Delete connection",
            appColors = appColors,
        )
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
private fun ConnectionActionIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    appColors: AppColors,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.height(28.dp).width(28.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) appColors.primaryText else appColors.mutedText,
            modifier = Modifier.height(17.dp).width(17.dp),
        )
    }
}

@Composable
private fun DeleteConnectionDialog(
    connection: SavedMediaSource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Connection") },
        text = { Text("Delete ${connection.displayName}? This removes the saved server entry.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PlaybackSettingsSection(
    appColors: AppColors,
    playbackSettings: PlaybackSettings,
    supportsReplayGain: Boolean,
    supportsCrossfade: Boolean,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
) {
    var upNextHelpOpen by remember { mutableStateOf(false) }

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
    Text("Previous button", color = appColors.secondaryText, fontSize = 12.sp)
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
        Text("Up Next selection", color = appColors.secondaryText, fontSize = 12.sp)
        IconButton(
            onClick = { upNextHelpOpen = true },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = NavigationIcons.Info,
                contentDescription = "Up Next selection details",
                tint = appColors.secondaryText,
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
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = playbackSettings.debugLoggingEnabled,
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(debugLoggingEnabled = enabled))
            },
        )
        Text("Debug logging", color = appColors.secondaryText, fontSize = 12.sp)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = playbackSettings.lrclibLyricsEnabled,
            onCheckedChange = { enabled ->
                onPlaybackSettingsChanged(playbackSettings.copy(lrclibLyricsEnabled = enabled))
            },
        )
        Text("Use LRCLIB when lyrics are missing or unsynced", color = appColors.secondaryText, fontSize = 12.sp)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onClearCache,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier.height(30.dp).fillMaxWidth(),
        ) { Text("Clear cache", fontSize = 12.sp) }
        TextButton(
            onClick = onClearLibrary,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier.height(30.dp).fillMaxWidth(),
        ) { Text("Clear library index", fontSize = 12.sp) }
        TextButton(
            onClick = onRefreshLibrary,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier.height(30.dp).fillMaxWidth(),
        ) { Text("Refresh library", fontSize = 12.sp) }
        TextButton(
            onClick = onResetDatabase,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            modifier = Modifier.height(30.dp).fillMaxWidth(),
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
    DetentIntSettingsSlider(
        title = "Prefetch depth",
        value = cacheSettings.audioPrefetchDepth,
        detents = PrefetchDepthOptions,
        snapDistance = 0.12f,
        enabled = cacheSettings.audioCachingEnabled,
        valueLabel = { depth -> if (depth == 0) "Off" else depth.toString() },
        appColors = appColors,
        onValueChanged = { depth ->
            onCacheSettingsChanged(cacheSettings.copy(audioPrefetchDepth = depth).normalized())
        },
    )
    DetentByteSettingsSlider(
        title = "Audio cache budget",
        valueBytes = cacheSettings.maxAudioCacheBytes,
        detents = AudioCacheBudgetOptions,
        snapDistance = 0.12f,
        appColors = appColors,
        onValueChanged = { bytes ->
            onCacheSettingsChanged(cacheSettings.copy(maxAudioCacheBytes = bytes).normalized())
        },
    )
    Text(
        "Downloads: ${cacheStats.downloadCount} files, ${cacheStats.downloadBytes.settingsBytesLabel()} used of " +
            cacheSettings.maxDownloadBytes.settingsBytesLabel(),
        color = appColors.secondaryText,
        fontSize = 12.sp,
    )
    DetentByteSettingsSlider(
        title = "Download storage budget",
        valueBytes = cacheSettings.maxDownloadBytes,
        detents = DownloadBudgetOptions,
        snapDistance = 0.12f,
        appColors = appColors,
        onValueChanged = { bytes ->
            onCacheSettingsChanged(cacheSettings.copy(maxDownloadBytes = bytes).normalized())
        },
    )
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

@Composable
private fun DetentIntSettingsSlider(
    title: String,
    value: Int,
    detents: List<Int>,
    snapDistance: Float,
    valueLabel: (Int) -> String,
    appColors: AppColors,
    onValueChanged: (Int) -> Unit,
    enabled: Boolean = true,
) {
    if (detents.isEmpty()) return

    val detentValues = detents.map { it.toFloat() }
    val sliderRange = 0f..detents.lastIndex.toFloat()
    DetentSettingsSliderScaffold(
        title = title,
        valueLabel = valueLabel(value),
        detentLabels = detents.map(valueLabel),
        enabled = enabled,
        appColors = appColors,
    ) {
        Slider(
            value = value.toFloat().toDetentPosition(detentValues).coerceIn(sliderRange.start, sliderRange.endInclusive),
            onValueChange = { rawPosition ->
                val snappedPosition = rawPosition.snapToDetentPosition(detents.lastIndex, snapDistance)
                val nextValue = snappedPosition.fromDetentPosition(detentValues)
                    .roundToInt()
                    .coerceIn(detents.first(), detents.last())
                if (nextValue != value) {
                    onValueChanged(nextValue)
                }
            },
            enabled = enabled,
            valueRange = sliderRange,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
    }
}

@Composable
private fun DetentByteSettingsSlider(
    title: String,
    valueBytes: Long,
    detents: List<AudioCacheBudgetOption>,
    snapDistance: Float,
    appColors: AppColors,
    onValueChanged: (Long) -> Unit,
    enabled: Boolean = true,
) {
    if (detents.isEmpty()) return

    val detentGb = detents.map { it.bytes.toGbFloat() }
    val sliderRange = 0f..detents.lastIndex.toFloat()
    val valuePosition = valueBytes.toGbFloat().toDetentPosition(detentGb)
    DetentSettingsSliderScaffold(
        title = title,
        valueLabel = valueBytes.settingsBytesLabel(),
        detentLabels = detents.map { it.label },
        enabled = enabled,
        appColors = appColors,
    ) {
        Slider(
            value = valuePosition.coerceIn(sliderRange.start, sliderRange.endInclusive),
            onValueChange = { rawPosition ->
                val snappedPosition = rawPosition.snapToDetentPosition(detents.lastIndex, snapDistance)
                val nextGb = snappedPosition.fromDetentPosition(detentGb)
                val nextBytes = nextGb.gbToBytes()
                if (nextBytes != valueBytes) {
                    onValueChanged(nextBytes)
                }
            },
            enabled = enabled,
            valueRange = sliderRange,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
    }
}

@Composable
private fun DetentSettingsSliderScaffold(
    title: String,
    valueLabel: String,
    detentLabels: List<String>,
    enabled: Boolean,
    appColors: AppColors,
    slider: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(title, color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(valueLabel, color = appColors.secondaryText, fontSize = 12.sp)
        }
        slider()
        DetentLabelRow(
            labels = detentLabels,
            enabled = enabled,
            appColors = appColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DetentLabelRow(
    labels: List<String>,
    enabled: Boolean,
    appColors: AppColors,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            labels.forEach { label ->
                Text(
                    label,
                    color = if (enabled) appColors.mutedText else appColors.mutedText.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                )
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = constraints.maxWidth
        layout(width, height) {
            val lastIndex = placeables.lastIndex.coerceAtLeast(1)
            placeables.forEachIndexed { index, placeable ->
                val centerX = (width * (index.toFloat() / lastIndex)).roundToInt()
                val x = (centerX - (placeable.width / 2)).coerceIn(0, width - placeable.width)
                placeable.placeRelative(x, 0)
            }
        }
    }
}

private fun Float.toDetentPosition(detents: List<Float>): Float {
    if (detents.isEmpty()) return 0f
    if (this <= detents.first()) return 0f
    if (this >= detents.last()) return detents.lastIndex.toFloat()

    val upperIndex = detents.indexOfFirst { detent -> this <= detent }
    val lowerIndex = (upperIndex - 1).coerceAtLeast(0)
    val lower = detents[lowerIndex]
    val upper = detents[upperIndex]
    val segmentProgress = if (upper == lower) 0f else (this - lower) / (upper - lower)
    return lowerIndex + segmentProgress
}

private fun Float.fromDetentPosition(detents: List<Float>): Float {
    if (detents.isEmpty()) return 0f
    val position = coerceIn(0f, detents.lastIndex.toFloat())
    val lowerIndex = position.toInt().coerceIn(0, detents.lastIndex)
    val upperIndex = (lowerIndex + 1).coerceAtMost(detents.lastIndex)
    val segmentProgress = position - lowerIndex
    return detents[lowerIndex] + ((detents[upperIndex] - detents[lowerIndex]) * segmentProgress)
}

private fun Float.snapToDetentPosition(lastIndex: Int, snapDistance: Float): Float {
    val nearest = roundToInt().coerceIn(0, lastIndex).toFloat()
    return if (abs(this - nearest) <= snapDistance) nearest else this
}

private fun Long.toGbFloat(): Float =
    this / (1024f * 1024f * 1024f)

private fun Float.gbToBytes(): Long =
    (this * 1024f * 1024f * 1024f).toLong()

private data class AudioCacheBudgetOption(
    val label: String,
    val bytes: Long,
)

private val PrefetchDepthOptions = listOf(0, 3, 5, 10, 15, 25)

private val AudioCacheBudgetOptions = listOf(
    AudioCacheBudgetOption("512 MB", 512L * 1024L * 1024L),
    AudioCacheBudgetOption("1 GB", 1L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("2 GB", 2L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("5 GB", 5L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("10 GB", 10L * 1024L * 1024L * 1024L),
)

private val DownloadBudgetOptions = listOf(
    AudioCacheBudgetOption("1 GB", 1L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("5 GB", 5L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("10 GB", 10L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("25 GB", 25L * 1024L * 1024L * 1024L),
    AudioCacheBudgetOption("50 GB", 50L * 1024L * 1024L * 1024L),
)

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

private enum class SettingsCategory(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Connections("Connections", "Servers and credentials", NavigationIcons.Settings),
    Playback("Playback", "Player behavior and lyrics", TransportIcons.Play),
    Cache("Cache", "Audio cache and downloads", NavigationIcons.Downloads),
    LocalData("Local data", "Cache, library, and database", NavigationIcons.Library),
    Diagnostics("Diagnostics", "Stats and debugging", NavigationIcons.Settings),
}

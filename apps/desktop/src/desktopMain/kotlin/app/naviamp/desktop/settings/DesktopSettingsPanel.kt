package app.naviamp.desktop

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.MaxWaveformBucketCount
import app.naviamp.domain.settings.MinWaveformBucketCount
import app.naviamp.domain.settings.SettingsSyncFileName
import app.naviamp.domain.settings.selectedMusicFolderSummary
import app.naviamp.desktop.settings.CacheSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.ui.NaviampAboutSettingsSection
import app.naviamp.ui.NaviampAboutUi
import app.naviamp.ui.NaviampAudioCacheSettingsSection
import app.naviamp.ui.NaviampConnectionForm
import app.naviamp.ui.NaviampDebugPlaybackSettingsSection
import app.naviamp.ui.NaviampDownloadsSettingsSection
import app.naviamp.ui.NaviampExperienceSettingsSection
import app.naviamp.ui.NaviampLanguageSettingsSection
import app.naviamp.ui.NaviampPlaybackSettingsSection
import app.naviamp.ui.NaviampSettingsCategory
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.categoryLabel
import app.naviamp.ui.categorySubtitle
import app.naviamp.ui.languageTitle
import app.naviamp.ui.naviampLanguagePack
import app.naviamp.ui.settingsTitle
import app.naviamp.ui.storageBytesLabel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DesktopSettingsPanel(
    modifier: Modifier = Modifier,
    appColors: DesktopAppColors,
    serverUrl: String,
    connectionName: String,
    username: String,
    password: String,
    insecureSkipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificateKeyStorePath: String,
    clientCertificateKeyStorePassword: String,
    secondaryUrls: List<ConnectionFormSecondaryUrl>,
    customHeaders: List<ConnectionFormHeader>,
    selectedMusicFolderIds: List<String>,
    availableMusicFolders: List<ConnectionFormMusicFolder>,
    musicFoldersStatus: String?,
    savedConnections: List<SavedMediaSource>,
    currentSourceId: String?,
    hasSavedConnection: Boolean,
    isConnectionFormOpen: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    interfaceSettings: InterfaceSettings,
    playbackSettings: PlaybackSettings,
    cacheSettings: CacheSettings,
    cacheStats: StorageCacheStats,
    settingsSyncDirectoryPath: String?,
    settingsSyncAutoExportEnabled: Boolean,
    settingsSyncStatus: String?,
    about: NaviampAboutUi,
    supportsReplayGain: Boolean,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
    supportsEqualizer: Boolean,
    supportsAudioOutputDeviceSelection: Boolean,
    audioOutputDevices: List<AudioOutputDevice>,
    supportsSonicSimilarity: Boolean,
    onServerUrlChanged: (String) -> Unit,
    onConnectionNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onInsecureSkipTlsVerificationChanged: (Boolean) -> Unit,
    onCustomCertificatePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePasswordChanged: (String) -> Unit,
    onSecondaryUrlsChanged: (List<ConnectionFormSecondaryUrl>) -> Unit,
    onCustomHeadersChanged: (List<ConnectionFormHeader>) -> Unit,
    onSelectedMusicFolderIdsChanged: (List<String>) -> Unit,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (SavedMediaSource) -> Unit,
    onDeleteConnection: (SavedMediaSource) -> Unit,
    onConnectSavedConnection: (SavedMediaSource) -> Unit,
    onCancelConnectionForm: () -> Unit,
    onSettingsSyncDirectoryChanged: (String?) -> Unit,
    onSettingsSyncDirectorySelectedForImport: (String) -> Unit,
    onSettingsSyncAutoExportChanged: (Boolean) -> Unit,
    onSettingsSyncExport: () -> Unit,
    onSettingsSyncImport: () -> Unit,
    onInterfaceSettingsChanged: (InterfaceSettings) -> Unit,
    onPlaybackSettingsChanged: (PlaybackSettings) -> Unit,
    onPlaybackSettingsChangedAndRedownload: (PlaybackSettings) -> Unit,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
    onOpenStatsForNerds: () -> Unit,
    onClearCache: () -> Unit,
    onClearLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onResetDatabase: () -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(NaviampSettingsCategory.Source) }
    var statusClickCount by remember { mutableIntStateOf(0) }
    var lastStatusClickMillis by remember { mutableStateOf(0L) }
    var clearCacheDialogOpen by remember { mutableStateOf(false) }
    var clearLibraryDialogOpen by remember { mutableStateOf(false) }
    var resetDialogOpen by remember { mutableStateOf(false) }
    var resetIncludesServers by remember { mutableStateOf(false) }
    val languagePack = remember(interfaceSettings.language) {
        naviampLanguagePack(interfaceSettings.language)
    }

    @Composable
    fun CategoryContent(category: NaviampSettingsCategory) {
        when (category) {
            NaviampSettingsCategory.Source -> ConnectionsSettings(
                appColors = appColors,
                serverUrl = serverUrl,
                connectionName = connectionName,
                username = username,
                password = password,
                insecureSkipTlsVerification = insecureSkipTlsVerification,
                customCertificatePath = customCertificatePath,
                clientCertificateKeyStorePath = clientCertificateKeyStorePath,
                clientCertificateKeyStorePassword = clientCertificateKeyStorePassword,
                secondaryUrls = secondaryUrls,
                customHeaders = customHeaders,
                selectedMusicFolderIds = selectedMusicFolderIds,
                availableMusicFolders = availableMusicFolders,
                musicFoldersStatus = musicFoldersStatus,
                savedConnections = savedConnections,
                currentSourceId = currentSourceId,
                hasSavedConnection = hasSavedConnection,
                isConnectionFormOpen = isConnectionFormOpen,
                isConnecting = isConnecting,
                connectionStatus = connectionStatus,
                settingsSyncDirectoryPath = settingsSyncDirectoryPath,
                settingsSyncAutoExportEnabled = settingsSyncAutoExportEnabled,
                settingsSyncStatus = settingsSyncStatus,
                onServerUrlChanged = onServerUrlChanged,
                onConnectionNameChanged = onConnectionNameChanged,
                onUsernameChanged = onUsernameChanged,
                onPasswordChanged = onPasswordChanged,
                onInsecureSkipTlsVerificationChanged = onInsecureSkipTlsVerificationChanged,
                onCustomCertificatePathChanged = onCustomCertificatePathChanged,
                onClientCertificateKeyStorePathChanged = onClientCertificateKeyStorePathChanged,
                onClientCertificateKeyStorePasswordChanged = onClientCertificateKeyStorePasswordChanged,
                onSecondaryUrlsChanged = onSecondaryUrlsChanged,
                onCustomHeadersChanged = onCustomHeadersChanged,
                onSelectedMusicFolderIdsChanged = onSelectedMusicFolderIdsChanged,
                onConnect = onConnect,
                onNewConnection = onNewConnection,
                onEditConnection = onEditConnection,
                onDeleteConnection = onDeleteConnection,
                onConnectSavedConnection = onConnectSavedConnection,
                onCancelConnectionForm = onCancelConnectionForm,
                onSettingsSyncDirectoryChanged = onSettingsSyncDirectoryChanged,
                onSettingsSyncDirectorySelectedForImport = onSettingsSyncDirectorySelectedForImport,
                onSettingsSyncAutoExportChanged = onSettingsSyncAutoExportChanged,
                onSettingsSyncExport = onSettingsSyncExport,
                onSettingsSyncImport = onSettingsSyncImport,
            )
            NaviampSettingsCategory.Language -> NaviampLanguageSettingsSection(
                colors = appColors,
                interfaceSettings = interfaceSettings,
                languagePack = languagePack,
                onInterfaceSettingsChanged = onInterfaceSettingsChanged,
            )
            NaviampSettingsCategory.Experience -> NaviampExperienceSettingsSection(
                colors = appColors,
                interfaceSettings = interfaceSettings,
                playbackSettings = playbackSettings,
                cacheSettings = cacheSettings,
                showQueueBehavior = true,
                showLrclibLyrics = true,
                supportsSonicSimilarity = supportsSonicSimilarity,
                onInterfaceSettingsChanged = onInterfaceSettingsChanged,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                onCacheSettingsChanged = onCacheSettingsChanged,
            )
            NaviampSettingsCategory.Playback -> NaviampPlaybackSettingsSection(
                colors = appColors,
                playbackSettings = playbackSettings,
                supportsReplayGain = supportsReplayGain,
                supportsGapless = supportsGapless,
                supportsCrossfade = supportsCrossfade,
                supportsEqualizer = supportsEqualizer,
                supportsAudioOutputDeviceSelection = supportsAudioOutputDeviceSelection,
                audioOutputDevices = audioOutputDevices,
                supportsSonicSimilarity = supportsSonicSimilarity,
                downloadBytes = cacheStats.downloadBytes,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
            )
            NaviampSettingsCategory.Downloads -> NaviampDownloadsSettingsSection(
                colors = appColors,
                playbackSettings = playbackSettings,
                cacheSettings = cacheSettings,
                diagnostics = NaviampDiagnosticsUi(
                    listOf(
                        "Audio cache" to cacheStats.audioBytes.storageBytesLabel(),
                        "Downloads" to cacheStats.downloadBytes.storageBytesLabel(),
                        "Images" to cacheStats.imageBytes.storageBytesLabel(),
                    ).let { rows -> listOf(NaviampDiagnosticsSectionUi("Storage", rows)) },
                ),
                showMobileNetworkQuality = false,
                downloadBytes = cacheStats.downloadBytes,
                onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                onPlaybackSettingsChangedAndRedownload = onPlaybackSettingsChangedAndRedownload,
                onCacheSettingsChanged = onCacheSettingsChanged,
            )
            NaviampSettingsCategory.AudioCache -> NaviampAudioCacheSettingsSection(
                colors = appColors,
                cacheSettings = cacheSettings,
                diagnostics = NaviampDiagnosticsUi(
                    listOf(
                        "Audio cache" to cacheStats.audioBytes.storageBytesLabel(),
                    ).let { rows -> listOf(NaviampDiagnosticsSectionUi("Storage", rows)) },
                ),
                onCacheSettingsChanged = onCacheSettingsChanged,
            )
            NaviampSettingsCategory.Debugging -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesktopSettingsRowHorizontalPadding),
                ) {
                    NaviampDebugPlaybackSettingsSection(
                        colors = appColors,
                        playbackSettings = playbackSettings,
                        onPlaybackSettingsChanged = onPlaybackSettingsChanged,
                    )
                    DiagnosticsSettings(
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
                    LocalDataSettings(
                        appColors = appColors,
                        onClearCache = { clearCacheDialogOpen = true },
                        onClearLibrary = { clearLibraryDialogOpen = true },
                        onRefreshLibrary = onRefreshLibrary,
                        onResetDatabase = {
                            resetIncludesServers = false
                            resetDialogOpen = true
                        },
                    )
                }
            }
            NaviampSettingsCategory.About -> NaviampAboutSettingsSection(
                colors = appColors,
                about = about,
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val narrowSettings = maxWidth < 560.dp
        var narrowCategory by remember { mutableStateOf<NaviampSettingsCategory?>(null) }
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
                    languagePack = languagePack,
                    connectionSubtitle = connectionSubtitle,
                    languageSubtitle = languagePack.languageTitle(interfaceSettings.language),
                    onCategorySelected = { narrowCategory = it },
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 10.dp),
                ) {
                    SettingsDetailHeader(
                        appColors = appColors,
                        title = languagePack.categoryLabel(activeCategory),
                        onBack = { narrowCategory = null },
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        CategoryContent(activeCategory)
                    }
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(languagePack.settingsTitle(), color = appColors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.width(150.dp),
                    ) {
                        NaviampSettingsCategory.entries.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(languagePack.categoryLabel(category), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
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
    appColors: DesktopAppColors,
    languagePack: app.naviamp.ui.NaviampLanguagePack,
    connectionSubtitle: String,
    languageSubtitle: String,
    onCategorySelected: (NaviampSettingsCategory) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(end = 10.dp),
    ) {
        Text(languagePack.settingsTitle(), color = appColors.primaryText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        NaviampSettingsCategory.entries.forEach { category ->
            SettingsCategoryRow(
                appColors = appColors,
                languagePack = languagePack,
                category = category,
                subtitle = when (category) {
                    NaviampSettingsCategory.Source -> connectionSubtitle
                    NaviampSettingsCategory.Language -> languageSubtitle
                    else -> languagePack.categorySubtitle(category)
                },
                onClick = { onCategorySelected(category) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    appColors: DesktopAppColors,
    languagePack: app.naviamp.ui.NaviampLanguagePack,
    category: NaviampSettingsCategory,
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
                languagePack.categoryLabel(category),
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
            imageVector = DesktopNavigationIcons.ChevronDown,
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
    appColors: DesktopAppColors,
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
                imageVector = DesktopNavigationIcons.Back,
                contentDescription = "Back to settings",
                tint = appColors.primaryText,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(title, color = appColors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FirstRunConnectionChoice(
    appColors: DesktopAppColors,
    onNewConnection: () -> Unit,
    onSettingsSyncDirectorySelectedForImport: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Start with a new server or import an existing settings sync folder.",
            color = appColors.secondaryText,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onNewConnection,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Text("Set up server", fontSize = 12.sp)
            }
            TextButton(
                onClick = {
                    chooseDirectory(System.getProperty("user.home"), "Choose settings sync folder")?.let { selected ->
                        onSettingsSyncDirectorySelectedForImport(selected.absolutePath)
                    }
                },
                modifier = Modifier.height(30.dp),
            ) {
                Text("Use sync folder", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ConnectionsSettings(
    appColors: DesktopAppColors,
    serverUrl: String,
    connectionName: String,
    username: String,
    password: String,
    insecureSkipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificateKeyStorePath: String,
    clientCertificateKeyStorePassword: String,
    secondaryUrls: List<ConnectionFormSecondaryUrl>,
    customHeaders: List<ConnectionFormHeader>,
    selectedMusicFolderIds: List<String>,
    availableMusicFolders: List<ConnectionFormMusicFolder>,
    musicFoldersStatus: String?,
    savedConnections: List<SavedMediaSource>,
    currentSourceId: String?,
    hasSavedConnection: Boolean,
    isConnectionFormOpen: Boolean,
    isConnecting: Boolean,
    connectionStatus: String?,
    settingsSyncDirectoryPath: String?,
    settingsSyncAutoExportEnabled: Boolean,
    settingsSyncStatus: String?,
    onServerUrlChanged: (String) -> Unit,
    onConnectionNameChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onInsecureSkipTlsVerificationChanged: (Boolean) -> Unit,
    onCustomCertificatePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePathChanged: (String) -> Unit,
    onClientCertificateKeyStorePasswordChanged: (String) -> Unit,
    onSecondaryUrlsChanged: (List<ConnectionFormSecondaryUrl>) -> Unit,
    onCustomHeadersChanged: (List<ConnectionFormHeader>) -> Unit,
    onSelectedMusicFolderIdsChanged: (List<String>) -> Unit,
    onConnect: () -> Unit,
    onNewConnection: () -> Unit,
    onEditConnection: (SavedMediaSource) -> Unit,
    onDeleteConnection: (SavedMediaSource) -> Unit,
    onConnectSavedConnection: (SavedMediaSource) -> Unit,
    onCancelConnectionForm: () -> Unit,
    onSettingsSyncDirectoryChanged: (String?) -> Unit,
    onSettingsSyncDirectorySelectedForImport: (String) -> Unit,
    onSettingsSyncAutoExportChanged: (Boolean) -> Unit,
    onSettingsSyncExport: () -> Unit,
    onSettingsSyncImport: () -> Unit,
) {
    var connectionPendingDelete by remember { mutableStateOf<SavedMediaSource?>(null) }
    var sourcePage by remember { mutableStateOf<DesktopSourceSettingsPage?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesktopSettingsRowHorizontalPadding),
    ) {
        val showingFirstRunChoice = savedConnections.isEmpty() && !isConnectionFormOpen
        if (isConnectionFormOpen) {
            ConnectionFormSubScreenHeader(
                appColors = appColors,
                title = if (hasSavedConnection) "Edit Connection" else "New Connection",
                onBack = onCancelConnectionForm,
                enabled = !isConnecting,
            )
            NaviampConnectionForm(
                form = ConnectionFormState(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    displayName = connectionName,
                    skipTlsVerification = insecureSkipTlsVerification,
                    customCertificatePath = customCertificatePath,
                    clientCertificatePath = clientCertificateKeyStorePath,
                    clientCertificatePassword = clientCertificateKeyStorePassword,
                    secondaryUrls = secondaryUrls,
                    customHeaders = customHeaders,
                    selectedMusicFolderIds = selectedMusicFolderIds,
                ),
                colors = appColors,
                isReconnect = hasSavedConnection,
                isConnecting = isConnecting,
                connectionStatus = connectionStatus,
                availableMusicFolders = availableMusicFolders,
                musicFoldersStatus = musicFoldersStatus,
                onFormChanged = { form ->
                    onServerUrlChanged(form.serverUrl)
                    onUsernameChanged(form.username)
                    onPasswordChanged(form.password)
                    onConnectionNameChanged(form.displayName)
                    onInsecureSkipTlsVerificationChanged(form.skipTlsVerification)
                    onCustomCertificatePathChanged(form.customCertificatePath)
                    onClientCertificateKeyStorePathChanged(form.clientCertificatePath)
                    onClientCertificateKeyStorePasswordChanged(form.clientCertificatePassword)
                    onSecondaryUrlsChanged(form.secondaryUrls)
                    onCustomHeadersChanged(form.customHeaders)
                    onSelectedMusicFolderIdsChanged(form.selectedMusicFolderIds)
                },
                onConnect = onConnect,
                onCancel = onCancelConnectionForm,
            )
            return@Column
        }
        when (sourcePage) {
            DesktopSourceSettingsPage.Connections -> {
                ConnectionFormSubScreenHeader(
                    appColors = appColors,
                    title = "Connections",
                    onBack = { sourcePage = null },
                    enabled = !isConnecting,
                )
                if (savedConnections.isEmpty()) {
                    Text("No saved connections yet.", color = appColors.secondaryText, fontSize = 12.sp)
                    if (showingFirstRunChoice) {
                        FirstRunConnectionChoice(
                            appColors = appColors,
                            onNewConnection = onNewConnection,
                            onSettingsSyncDirectorySelectedForImport = onSettingsSyncDirectorySelectedForImport,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedConnections.forEach { connection ->
                            SavedConnectionRow(
                                appColors = appColors,
                                connection = connection,
                                selectedLibrarySummary = selectedMusicFolderSummary(
                                    selectedIds = connection.selectedMusicFolderIds,
                                    availableFolders = availableMusicFolders,
                                ),
                                selected = connection.id == currentSourceId,
                                enabled = !isConnecting,
                                onEdit = { onEditConnection(connection) },
                                onDelete = { connectionPendingDelete = connection },
                                onConnect = { onConnectSavedConnection(connection) },
                            )
                        }
                    }
                }
                if (!showingFirstRunChoice) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onNewConnection,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("New connection", fontSize = 12.sp)
                    }
                }
                connectionStatus?.let {
                    Text(it, color = appColors.secondaryText, fontSize = 12.sp)
                }
            }
            DesktopSourceSettingsPage.SettingsSync -> {
                ConnectionFormSubScreenHeader(
                    appColors = appColors,
                    title = "Settings Sync",
                    onBack = { sourcePage = null },
                    enabled = !isConnecting,
                )
                Text(
                    "Use a folder managed by your own sync service. Naviamp writes $SettingsSyncFileName there.",
                    color = appColors.secondaryText,
                    fontSize = 12.sp,
                )
                DesktopInlineToggleRow(
                    appColors = appColors,
                    checked = settingsSyncAutoExportEnabled,
                    enabled = settingsSyncDirectoryPath != null,
                    label = "Auto-sync changes",
                    onCheckedChange = onSettingsSyncAutoExportChanged,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        settingsSyncDirectoryPath ?: "No sync folder selected",
                        color = appColors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val start = settingsSyncDirectoryPath ?: System.getProperty("user.home")
                            chooseDirectory(start, "Choose settings sync folder")?.let { selected ->
                                onSettingsSyncDirectoryChanged(selected.absolutePath)
                            }
                        },
                    ) {
                        Text("Choose")
                    }
                    TextButton(
                        enabled = settingsSyncDirectoryPath != null,
                        onClick = { onSettingsSyncDirectoryChanged(null) },
                    ) {
                        Text("Reset")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = settingsSyncDirectoryPath != null,
                        onClick = onSettingsSyncExport,
                    ) {
                        Text("Export")
                    }
                    TextButton(
                        enabled = settingsSyncDirectoryPath != null,
                        onClick = onSettingsSyncImport,
                    ) {
                        Text("Import")
                    }
                }
                settingsSyncStatus?.let {
                    Text(it, color = appColors.secondaryText, fontSize = 12.sp)
                }
            }
            null -> {
                DesktopSourceSubpageRow(
                    title = "Connections",
                    subtitle = "Saved Navidrome servers.",
                    value = savedConnections.firstOrNull { it.id == currentSourceId }?.displayName ?: "${savedConnections.size} saved",
                    appColors = appColors,
                    enabled = !isConnecting,
                ) {
                    sourcePage = DesktopSourceSettingsPage.Connections
                }
                DesktopSourceSubpageRow(
                    title = "Settings Sync",
                    subtitle = "Folder-based settings sync.",
                    value = settingsSyncDirectoryPath?.let { "Configured" },
                    appColors = appColors,
                    enabled = !isConnecting,
                ) {
                    sourcePage = DesktopSourceSettingsPage.SettingsSync
                }
                connectionStatus?.let {
                    Text(it, color = appColors.secondaryText, fontSize = 12.sp)
                }
            }
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

private enum class DesktopSourceSettingsPage {
    Connections,
    SettingsSync,
}

@Composable
private fun DesktopSourceSubpageRow(
    title: String,
    subtitle: String,
    value: String?,
    appColors: DesktopAppColors,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) appColors.primaryText else appColors.mutedText, fontSize = 14.sp)
            Text(subtitle, color = appColors.secondaryText, fontSize = 11.sp)
        }
        value?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                color = if (enabled) appColors.secondaryText else appColors.mutedText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(0.45f, fill = false)
                    .padding(start = 10.dp),
            )
        }
        Icon(
            imageVector = DesktopNavigationIcons.ChevronRight,
            contentDescription = null,
            tint = if (enabled) appColors.secondaryText else appColors.mutedText,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DesktopInlineToggleRow(
    appColors: DesktopAppColors,
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (enabled) appColors.primaryText else appColors.mutedText,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        CompactSettingsSwitch(
            checked = checked,
            enabled = enabled,
            appColors = appColors,
            onClick = { onCheckedChange(!checked) },
        )
    }
}

@Composable
private fun CompactSettingsSwitch(
    checked: Boolean,
    enabled: Boolean,
    appColors: DesktopAppColors,
    onClick: () -> Unit,
) {
    val trackColor = when {
        !enabled -> appColors.border.copy(alpha = 0.28f)
        checked -> appColors.accent
        else -> appColors.background.copy(alpha = 0.92f)
    }
    val thumbColor = when {
        !enabled -> appColors.mutedText.copy(alpha = 0.62f)
        checked -> appColors.primaryText
        else -> appColors.secondaryText
    }
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(18.dp)
            .background(trackColor, RoundedCornerShape(999.dp))
            .border(1.dp, appColors.border.copy(alpha = if (checked) 0.22f else 0.72f), RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .background(thumbColor, RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun ConnectionFormSubScreenHeader(
    appColors: DesktopAppColors,
    title: String,
    onBack: () -> Unit,
    enabled: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            enabled = enabled,
            onClick = onBack,
            modifier = Modifier.height(30.dp).width(30.dp),
        ) {
            Icon(
                imageVector = DesktopNavigationIcons.Back,
                contentDescription = "Back to connections",
                tint = if (enabled) appColors.primaryText else appColors.mutedText,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            title,
            color = appColors.primaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    connectionSubScreenDivider(appColors)
}

@Composable
private fun connectionSubScreenDivider(appColors: DesktopAppColors) {
    HorizontalDivider(color = appColors.border)
}

@Composable
private fun SavedConnectionRow(
    appColors: DesktopAppColors,
    connection: SavedMediaSource,
    selectedLibrarySummary: String,
    selected: Boolean,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) appColors.accent else appColors.border, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    connection.displayName,
                    color = appColors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text("Current", color = appColors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            ConnectionActionIconButton(
                enabled = enabled,
                onClick = onEdit,
                icon = DesktopNavigationIcons.Edit,
                contentDescription = "Edit connection",
                appColors = appColors,
            )
            ConnectionActionIconButton(
                enabled = enabled,
                onClick = onDelete,
                icon = DesktopNavigationIcons.Trash,
                contentDescription = "Delete connection",
                appColors = appColors,
            )
            ConnectionActionIconButton(
                enabled = enabled,
                onClick = onConnect,
                icon = DesktopNavigationIcons.Refresh,
                contentDescription = if (selected) "Reconnect" else "Connect",
                appColors = appColors,
            )
        }
        Text(
            connection.username,
            color = appColors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            connection.baseUrl,
            color = appColors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (selectedLibrarySummary.isNotBlank()) {
            Text(
                "Libraries: $selectedLibrarySummary",
                color = appColors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConnectionActionIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    appColors: DesktopAppColors,
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
private fun LocalDataSettings(
    appColors: DesktopAppColors,
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
        LocalDataActionRow("Clear cache", appColors, onClearCache)
        LocalDataActionRow("Clear library index", appColors, onClearLibrary)
        LocalDataActionRow("Refresh library", appColors, onRefreshLibrary)
        LocalDataActionRow("Reset database", appColors, onResetDatabase)
    }
}

@Composable
private fun LocalDataActionRow(
    label: String,
    appColors: DesktopAppColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = appColors.primaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CacheSettingsSection(
    appColors: DesktopAppColors,
    cacheSettings: CacheSettings,
    cacheStats: StorageCacheStats,
    onCacheSettingsChanged: (CacheSettings) -> Unit,
) {
    var downloadPathStatus by remember { mutableStateOf<String?>(null) }
    SettingsSectionTitle("Cache", appColors)
    Text(
        "Audio cache: ${cacheStats.audioCount} files, ${cacheStats.audioBytes.storageBytesLabel()} used of " +
            cacheSettings.maxAudioCacheBytes.storageBytesLabel(),
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
        Text("Enable audio prefetch", color = appColors.secondaryText, fontSize = 12.sp)
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
        "Waveforms: ${cacheStats.audioWaveformCount} files, ${cacheStats.audioWaveformBytes.storageBytesLabel()}",
        color = appColors.secondaryText,
        fontSize = 12.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = cacheSettings.waveformsEnabled,
            onCheckedChange = { enabled ->
                onCacheSettingsChanged(cacheSettings.copy(waveformsEnabled = enabled).normalized())
            },
        )
        Text("Generate track waveforms", color = appColors.secondaryText, fontSize = 12.sp)
    }
    DetentIntSettingsSlider(
        title = "Waveform detail",
        value = cacheSettings.waveformBucketCount,
        detents = WaveformBucketCountOptions,
        snapDistance = 0.12f,
        enabled = cacheSettings.waveformsEnabled,
        valueLabel = { count -> "$count steps" },
        appColors = appColors,
        onValueChanged = { count ->
            onCacheSettingsChanged(cacheSettings.copy(waveformBucketCount = count).normalized())
        },
    )
    Text(
        "Downloads: ${cacheStats.downloadCount} files, ${cacheStats.downloadBytes.storageBytesLabel()} used of " +
            cacheSettings.maxDownloadBytes.storageBytesLabel(),
        color = appColors.secondaryText,
        fontSize = 12.sp,
    )
    Text(
        "Download location",
        color = appColors.primaryText,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val effectiveDownloadDirectory = cacheSettings.customDownloadDirectory
            ?: DesktopDownloadDirectories.defaultDirectory().toString()
        Text(
            effectiveDownloadDirectory,
            color = appColors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                chooseDirectory(effectiveDownloadDirectory, "Choose download location")?.let { selected ->
                    runCatching {
                        DesktopDownloadDirectories.prepare(selected.toPath())
                    }.onSuccess { directory ->
                        downloadPathStatus = null
                        onCacheSettingsChanged(
                            cacheSettings.copy(customDownloadDirectory = directory.toString()).normalized(),
                        )
                    }.onFailure { error ->
                        downloadPathStatus = error.message ?: "Could not use that download location."
                    }
                }
            },
        ) {
            Text("Choose")
        }
        TextButton(
            enabled = cacheSettings.customDownloadDirectory != null,
            onClick = {
                downloadPathStatus = null
                onCacheSettingsChanged(cacheSettings.copy(customDownloadDirectory = null).normalized())
            },
        ) {
            Text("Reset")
        }
    }
    Text(
        "Existing downloads stay where they are. New downloads use this location.",
        color = appColors.secondaryText,
        fontSize = 12.sp,
    )
    downloadPathStatus?.let { status ->
        Text(status, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
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

private sealed interface DirectoryPickerResult {
    data class Selected(val file: File) : DirectoryPickerResult
    data object Cancelled : DirectoryPickerResult
    data object Unavailable : DirectoryPickerResult
}

private fun chooseDirectory(currentPath: String, title: String): File? =
    when (
        val result = when {
            isMacOs() -> chooseMacDirectory(currentPath, title)
            isWindows() -> chooseWindowsDirectory(currentPath, title)
            else -> chooseLinuxDirectory(currentPath, title)
        }
    ) {
        is DirectoryPickerResult.Selected -> result.file
        DirectoryPickerResult.Cancelled -> null
        DirectoryPickerResult.Unavailable -> chooseSwingDirectory(currentPath, title)
    }

private fun chooseMacDirectory(currentPath: String, title: String): DirectoryPickerResult {
    val previous = System.getProperty(MacDirectoryDialogProperty)
    System.setProperty(MacDirectoryDialogProperty, "true")
    return try {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.directory = currentPath
        dialog.isVisible = true
        val selected = dialog.file ?: return DirectoryPickerResult.Cancelled
        DirectoryPickerResult.Selected(File(dialog.directory, selected))
    } catch (_: Throwable) {
        DirectoryPickerResult.Unavailable
    } finally {
        if (previous == null) {
            System.clearProperty(MacDirectoryDialogProperty)
        } else {
            System.setProperty(MacDirectoryDialogProperty, previous)
        }
    }
}

private fun chooseWindowsDirectory(currentPath: String, title: String): DirectoryPickerResult {
    val selectedPath = runNativeDirectoryPicker(
        "powershell",
        "-NoProfile",
        "-STA",
        "-Command",
        """
        Add-Type -AssemblyName System.Windows.Forms;
        ${'$'}dialog = New-Object System.Windows.Forms.FolderBrowserDialog;
        ${'$'}dialog.Description = '${title.replace("'", "''")}';
        ${'$'}dialog.SelectedPath = '${currentPath.replace("'", "''")}';
        if (${'$'}dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) {
            [Console]::Out.Write(${'$'}dialog.SelectedPath)
        }
        """.trimIndent(),
    )
    return when (selectedPath) {
        null -> DirectoryPickerResult.Unavailable
        "" -> DirectoryPickerResult.Cancelled
        else -> DirectoryPickerResult.Selected(File(selectedPath))
    }
}

private fun chooseLinuxDirectory(currentPath: String, title: String): DirectoryPickerResult {
    runNativeDirectoryPicker(
        "zenity",
        "--file-selection",
        "--directory",
        "--title=$title",
        "--filename=$currentPath/",
    ).let { result ->
        if (result != null) {
            return if (result.isBlank()) DirectoryPickerResult.Cancelled else DirectoryPickerResult.Selected(File(result))
        }
    }
    runNativeDirectoryPicker(
        "kdialog",
        "--getexistingdirectory",
        currentPath,
        "--title",
        title,
    ).let { result ->
        if (result != null) {
            return if (result.isBlank()) DirectoryPickerResult.Cancelled else DirectoryPickerResult.Selected(File(result))
        }
    }
    return DirectoryPickerResult.Unavailable
}

private fun runNativeDirectoryPicker(vararg command: String): String? =
    runCatching {
        val process = ProcessBuilder(*command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        when {
            exitCode == 0 -> output
            exitCode == 1 -> ""
            else -> null
        }
    }.getOrNull()

private fun chooseSwingDirectory(currentPath: String, title: String): File? {
    val chooser = JFileChooser(File(currentPath))
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = title
    chooser.isAcceptAllFileFilterUsed = false
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").lowercase().contains("mac")

private fun isWindows(): Boolean =
    System.getProperty("os.name").lowercase().contains("win")

private const val MacDirectoryDialogProperty = "apple.awt.fileDialogForDirectories"

@Composable
private fun DiagnosticsSettings(
    appColors: DesktopAppColors,
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
private fun SettingsSectionTitle(title: String, appColors: DesktopAppColors) {
    Text(title, color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

@Composable
private fun SettingsPlaceholderSection(
    title: String,
    body: String,
    appColors: DesktopAppColors,
    value: String? = null,
) {
    SettingsSectionTitle(title, appColors)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(body, color = appColors.secondaryText, fontSize = 12.sp)
        value?.let {
            Text(it, color = appColors.primaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetentIntSettingsSlider(
    title: String,
    value: Int,
    detents: List<Int>,
    snapDistance: Float,
    valueLabel: (Int) -> String,
    appColors: DesktopAppColors,
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
    appColors: DesktopAppColors,
    onValueChanged: (Long) -> Unit,
    enabled: Boolean = true,
) {
    if (detents.isEmpty()) return

    val detentGb = detents.map { it.bytes.toGbFloat() }
    val sliderRange = 0f..detents.lastIndex.toFloat()
    val valuePosition = valueBytes.toGbFloat().toDetentPosition(detentGb)
    DetentSettingsSliderScaffold(
        title = title,
        valueLabel = valueBytes.storageBytesLabel(),
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
    appColors: DesktopAppColors,
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
    appColors: DesktopAppColors,
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

private val DesktopSettingsRowHorizontalPadding = 14.dp
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

package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampCacheSettingsUi
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampConnectionSettingsUi
import app.naviamp.ui.NaviampGeneralSettingsUi
import app.naviamp.ui.NaviampPlaybackSettingsUi
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.NaviampSharedSettingsContent
import app.naviamp.ui.NaviampStorageLocationUi
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

@Composable
fun DesktopSettingsPanel(
    modifier: Modifier = Modifier,
    appColors: DesktopAppColors,
    connectionSettings: NaviampConnectionSettingsUi,
    general: NaviampGeneralSettingsUi,
    playback: NaviampPlaybackSettingsUi,
    cache: NaviampCacheSettingsUi,
    settingsSync: NaviampSettingsSyncUi,
    connectionActions: NaviampConnectionSettingsActions,
    syncActions: NaviampSettingsSyncActions,
    valueActions: NaviampSettingsValueActions,
    maintenanceActions: NaviampSettingsMaintenanceActions,
) {
    val connection = connectionSettings.connection
    val cacheSettings = cache.settings
    val downloadLocations = buildList {
        add(NaviampStorageLocationUi("default", "Default folder", DesktopDownloadDirectories.defaultDirectory().toString()))
        cacheSettings.customDownloadDirectory?.let { path ->
            add(NaviampStorageLocationUi("custom", "Current custom folder", path))
        }
        if (cache.fileSelectionAvailable) {
            add(NaviampStorageLocationUi("choose", "Choose another folder…", "Select any writable folder"))
        }
    }
    val audioCacheLocations = buildList {
        add(NaviampStorageLocationUi("default", "Default folder", defaultAudioCacheDirectory().toString()))
        cacheSettings.customAudioCacheDirectory?.let { path ->
            add(NaviampStorageLocationUi("custom", "Current custom folder", path))
        }
        if (cache.fileSelectionAvailable) {
            add(NaviampStorageLocationUi("choose", "Choose another folder…", "Select any writable folder"))
        }
    }
    val chooseSettingsSyncFolder: () -> Unit = {
        val start = settingsSync.directoryPath ?: System.getProperty("user.home")
        chooseDirectory(start, "Choose settings sync folder")?.let { selected ->
            syncActions.onDirectoryChanged(selected.absolutePath)
        }
    }
    val importSettingsSyncDirectory: () -> Unit = {
        val start = settingsSync.directoryPath ?: System.getProperty("user.home")
        chooseDirectory(start, "Import Naviamp settings")?.let { selected ->
            syncActions.onDirectorySelectedForImport(selected.absolutePath)
        }
    }

    NaviampSharedSettingsContent(
        colors = appColors,
        modifier = modifier,
        interfaceSettings = general.interfaceSettings,
        playbackSettings = playback.settings,
        cacheSettings = cacheSettings,
        diagnostics = cache.diagnostics,
        downloadsDiagnostics = cache.downloadsDiagnostics,
        audioCacheDiagnostics = cache.audioCacheDiagnostics,
        about = general.about,
        savedConnections = connection.savedConnections,
        isConnectionFormOpen = connection.editingConnection,
        isConnecting = connection.isConnecting,
        connectionStatus = connection.status,
        settingsSyncStatus = settingsSync.status,
        availableMusicFolders = connection.availableMusicFolders,
        musicFoldersStatus = connection.musicFoldersStatus,
        connectionForm = connection.form,
        hasSavedConnection = connection.hasSavedConnection,
        onEditConnection = connectionActions.onEditCurrentConnection,
        onNewConnection = connectionActions.onNewConnection,
        onEditSavedConnection = connectionActions.onEditConnection,
        onConnectSavedConnection = connectionActions.onConnectSavedConnection,
        onDeleteSavedConnection = connectionActions.onDeleteConnection,
        onImportSettingsSyncFile = (syncActions.onImportFile ?: importSettingsSyncDirectory)
            .takeIf { settingsSync.available },
        onChooseSettingsSyncFolder = (syncActions.onChooseFolder ?: chooseSettingsSyncFolder)
            .takeIf { settingsSync.available },
        onImportSettingsSyncFolder = (syncActions.onImportFolder ?: syncActions.onImport)
            .takeIf { settingsSync.available },
        onExportSettingsSyncFolder = (syncActions.onExportFolder ?: syncActions.onExport)
            .takeIf { settingsSync.available },
        settingsSyncAutoExportEnabled = settingsSync.autoExportEnabled,
        onSettingsSyncAutoExportChanged = syncActions.onAutoExportChanged.takeIf { settingsSync.available },
        onConnectionFormChanged = connectionActions.onFormChanged,
        onConnect = connectionActions.onConnect,
        onCancelConnectionForm = connectionActions.onCancelConnectionForm,
        onInterfaceSettingsChanged = valueActions.onInterfaceSettingsChanged,
        onPlaybackSettingsChanged = valueActions.onPlaybackSettingsChanged,
        onPlaybackSettingsChangedAndRedownload = valueActions.onPlaybackSettingsChangedAndRedownload,
        onCacheSettingsChanged = valueActions.onCacheSettingsChanged,
        onClearCache = maintenanceActions.onClearCache,
        onClearLibrary = maintenanceActions.onClearLibrary,
        onRefreshLibrary = maintenanceActions.onRefreshLibrary,
        onResetDatabase = maintenanceActions.onResetDatabase,
        onOpenStatsForNerds = maintenanceActions.onOpenStatsForNerds,
        supportsReplayGain = playback.replayGainAvailable,
        supportsGapless = playback.gaplessAvailable,
        supportsCrossfade = playback.crossfadeAvailable,
        supportsEqualizer = playback.equalizerAvailable,
        supportsAudioOutputDeviceSelection = playback.audioOutputDeviceSelectionAvailable,
        audioOutputDevices = playback.audioOutputDevices,
        supportsSonicSimilarity = playback.sonicSimilarityAvailable,
        downloadBytes = playback.downloadBytes,
        showMobileNetworkQuality = playback.showMobileNetworkQuality,
        showTooltipPreference = true,
        connectionCapabilities = connectionSettings.capabilities,
        downloadLocations = downloadLocations,
        audioCacheLocations = audioCacheLocations,
        selectedDownloadLocationId = if (cacheSettings.customDownloadDirectory == null) "default" else "custom",
        selectedAudioCacheLocationId = if (cacheSettings.customAudioCacheDirectory == null) "default" else "custom",
        onDownloadLocationChanged = { location ->
            when (location.id) {
                "default" -> valueActions.onCacheSettingsChanged(
                    cacheSettings.copy(customDownloadDirectory = null).normalized(),
                )
                "choose" -> {
                    val start = cacheSettings.customDownloadDirectory
                        ?: DesktopDownloadDirectories.defaultDirectory().toString()
                    chooseDirectory(start, "Choose download location")?.let { selected ->
                        runCatching { DesktopDownloadDirectories.prepare(selected.toPath()) }
                            .onSuccess { directory ->
                                valueActions.onCacheSettingsChanged(
                                    cacheSettings.copy(customDownloadDirectory = directory.toString()).normalized(),
                                )
                            }
                    }
                }
                else -> valueActions.onDownloadLocationChanged(location)
            }
        },
        onAudioCacheLocationChanged = { location ->
            when (location.id) {
                "default" -> valueActions.onCacheSettingsChanged(
                    cacheSettings.copy(customAudioCacheDirectory = null).normalized(),
                )
                "choose" -> {
                    val start = cacheSettings.customAudioCacheDirectory ?: defaultAudioCacheDirectory().toString()
                    chooseDirectory(start, "Choose cache location")?.let { selected ->
                        runCatching {
                            selected.toPath()
                                .also(java.nio.file.Files::createDirectories)
                                .toAbsolutePath()
                                .normalize()
                        }.onSuccess { directory ->
                            valueActions.onCacheSettingsChanged(
                                cacheSettings.copy(customAudioCacheDirectory = directory.toString()).normalized(),
                            )
                        }
                    }
                }
                else -> valueActions.onAudioCacheLocationChanged(location)
            }
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
            return if (result.isBlank()) DirectoryPickerResult.Cancelled
            else DirectoryPickerResult.Selected(File(result))
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
            return if (result.isBlank()) DirectoryPickerResult.Cancelled
            else DirectoryPickerResult.Selected(File(result))
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

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
import app.naviamp.ui.NaviampSettingsContent
import app.naviamp.ui.NaviampStorageLocationUi
import app.naviamp.desktop.settings.DesktopNativeDirectoryPicker
import java.io.File

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

    val adaptedCache = cache.copy(
        downloadLocations = downloadLocations,
        audioCacheLocations = audioCacheLocations,
        selectedDownloadLocationId = if (cacheSettings.customDownloadDirectory == null) "default" else "custom",
        selectedAudioCacheLocationId = if (cacheSettings.customAudioCacheDirectory == null) "default" else "custom",
    )
    val adaptedSyncActions = syncActions.copy(
        onImportFile = (syncActions.onImportFile ?: importSettingsSyncDirectory).takeIf { settingsSync.available },
        onChooseFolder = (syncActions.onChooseFolder ?: chooseSettingsSyncFolder).takeIf { settingsSync.available },
        onImportFolder = (syncActions.onImportFolder ?: syncActions.onImport).takeIf { settingsSync.available },
        onExportFolder = (syncActions.onExportFolder ?: syncActions.onExport).takeIf { settingsSync.available },
    )
    val adaptedValueActions = valueActions.copy(
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

    NaviampSettingsContent(
        colors = appColors,
        modifier = modifier,
        connectionSettings = connectionSettings,
        general = general,
        playback = playback,
        cache = adaptedCache,
        settingsSync = settingsSync,
        connectionActions = connectionActions,
        syncActions = adaptedSyncActions,
        valueActions = adaptedValueActions,
        maintenanceActions = maintenanceActions,
    )
}

private fun chooseDirectory(currentPath: String, title: String): File? =
    DesktopSettingsDirectoryPicker.chooseDirectory(currentPath, title)?.let(::File)

private val DesktopSettingsDirectoryPicker = DesktopNativeDirectoryPicker()

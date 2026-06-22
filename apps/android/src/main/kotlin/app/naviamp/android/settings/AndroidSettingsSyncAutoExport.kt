package app.naviamp.android

import android.content.Context
import android.net.Uri
import app.naviamp.domain.settings.SettingsSyncCoordinator
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.buildSettingsSyncDocument

fun markAndroidSettingsSyncChangedAndAutoExport(
    context: Context,
    settingsStore: AndroidSettingsStore,
    storage: AndroidStorageDependencies,
) {
    val coordinator = SettingsSyncCoordinator(
        deviceId = AndroidSettingsSyncDeviceId,
        state = {
            settingsStore.loadSettingsSync().let { settings ->
                SettingsSyncRuntimeState(
                    autoExportEnabled = settings.autoExportEnabled,
                    lastLocalUpdateEpochMillis = settings.lastLocalUpdateEpochMillis,
                    lastAppliedSyncUpdateEpochMillis = settings.lastAppliedSyncUpdateEpochMillis,
                )
            }
        },
        saveState = { runtimeState ->
            val current = settingsStore.loadSettingsSync()
            settingsStore.saveSettingsSync(
                current.copy(
                    autoExportEnabled = runtimeState.autoExportEnabled,
                    lastLocalUpdateEpochMillis = runtimeState.lastLocalUpdateEpochMillis,
                    lastAppliedSyncUpdateEpochMillis = runtimeState.lastAppliedSyncUpdateEpochMillis,
                ),
            )
        },
        nowEpochMillis = { System.currentTimeMillis() },
        buildLocalDocument = { updatedAtEpochMillis ->
            val storedPlayback = settingsStore.loadPlaybackSettings()
            val radioDjs = storage.radioDjPresets().ifEmpty { storedPlayback.radioDjs }
            buildSettingsSyncDocument(
                snapshot = SettingsSyncLocalSnapshot(
                    serverProfiles = storage.mediaSources(),
                    playback = storedPlayback.copy(radioDjs = radioDjs),
                    visualizer = settingsStore.loadVisualizerSettings(),
                    recentRadioStreams = settingsStore.loadRecentRadioStreams(),
                    recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations(),
                ),
                nowEpochMillis = updatedAtEpochMillis,
                deviceId = AndroidSettingsSyncDeviceId,
            )
        },
        applyDocument = {},
    )
    coordinator.markLocalChanged()
    coordinator.exportCurrent().documentToWrite?.let { document ->
        runCatching {
            AndroidSettingsSyncMirrorStore(context).write(document)
        }.onSuccess {
            coordinator.documentWritten(document)
            settingsStore.saveSettingsSync(
                settingsStore.loadSettingsSync().copy(
                    lastMirrorUpdateEpochMillis = document.updatedAtEpochMillis,
                    lastProviderError = null,
                ),
            )
        }.onFailure { error ->
            settingsStore.saveSettingsSync(
                settingsStore.loadSettingsSync().copy(
                    lastProviderError = error.message ?: "Could not save local settings mirror.",
                ),
            )
            return
        }
        if (!settingsStore.loadSettingsSync().autoExportEnabled) return
        val treeUri = settingsStore.loadSettingsSync().treeUri?.let(Uri::parse) ?: return
        runCatching {
            AndroidSettingsSyncFile.write(context, treeUri, document)
        }.onSuccess {
            settingsStore.saveSettingsSync(
                settingsStore.loadSettingsSync().copy(
                    lastProviderPushEpochMillis = System.currentTimeMillis(),
                    lastProviderError = null,
                ),
            )
        }.onFailure { error ->
            settingsStore.saveSettingsSync(
                settingsStore.loadSettingsSync().copy(
                    lastProviderError = error.message ?: "Could not sync settings with provider.",
                ),
            )
        }
    }
}

private const val AndroidSettingsSyncDeviceId = "android"

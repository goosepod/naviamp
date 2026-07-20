package app.naviamp.android

import android.content.Context
import app.naviamp.app.NaviampApplicationStatusLevel
import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState

fun markAndroidSettingsSyncChangedAndAutoExport(
    context: Context,
    settingsStore: AndroidSettingsStore,
    storage: AndroidStorageDependencies,
) {
    val controller = NaviampSettingsSyncController(
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
        nowEpochMillis = AndroidSystemClock::nowEpochMillis,
        snapshot = {
            val storedPlayback = settingsStore.loadPlaybackSettings()
            val radioDjs = storage.radioDjPresets().ifEmpty { storedPlayback.radioDjs }
            SettingsSyncLocalSnapshot(
                serverProfiles = storage.mediaSources(),
                interfaceSettings = settingsStore.loadInterfaceSettings(),
                playback = storedPlayback.copy(radioDjs = radioDjs),
                visualizer = settingsStore.loadVisualizerSettings(),
                recentRadioStreams = settingsStore.loadRecentRadioStreams(),
                recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations(),
            )
        },
        applyDocument = {},
    )
    AndroidSettingsSyncHost(
        context = context,
        controller = controller,
        mirrorStore = AndroidSettingsSyncMirrorStore(context),
        settings = settingsStore::loadSettingsSync,
        saveSettings = settingsStore::saveSettingsSync,
        setStatus = {},
        publishStatus = { _: String, _: NaviampApplicationStatusLevel -> },
    ).markChangedAndAutoExport()
}

private const val AndroidSettingsSyncDeviceId = "android"

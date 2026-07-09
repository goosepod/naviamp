package app.naviamp.android

import android.content.Context
import android.net.Uri
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncJson
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.importSettingsSyncServerProfiles
import app.naviamp.ui.naviampVisualizerFromName

fun importAndroidSettingsSyncDocument(
    context: Context,
    uri: Uri,
    state: AndroidAppState,
    settingsStore: AndroidSettingsStore,
    storage: AndroidStorageDependencies,
    playbackEngine: AndroidPlaybackEngine,
): String {
    val text = context.contentResolver.openInputStream(uri)
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: error("Could not read the selected settings file.")
    return importAndroidSettingsSyncDocumentText(
        text = text,
        state = state,
        settingsStore = settingsStore,
        storage = storage,
        playbackEngine = playbackEngine,
    )
}

fun importAndroidSettingsSyncDocumentText(
    text: String,
    state: AndroidAppState,
    settingsStore: AndroidSettingsStore,
    storage: AndroidStorageDependencies,
    playbackEngine: AndroidPlaybackEngine,
): String {
    val document = SettingsSyncJson.decode(text)
    return applyAndroidSettingsSyncDocument(
        document = document,
        state = state,
        settingsStore = settingsStore,
        storage = storage,
        playbackEngine = playbackEngine,
    )
}

fun applyAndroidSettingsSyncDocument(
    document: SettingsSyncDocument,
    state: AndroidAppState,
    settingsStore: AndroidSettingsStore,
    storage: AndroidStorageDependencies,
    playbackEngine: AndroidPlaybackEngine,
): String {
    val preferences = document.preferences
    val importedPlayback = preferences.playback.effectiveForEngine(playbackEngine)

    state.interfaceSettings = preferences.interfaceSettings.normalized()
    settingsStore.saveInterfaceSettings(state.interfaceSettings)

    storage.replaceRadioDjPresets(importedPlayback.radioDjs)
    state.playbackSettings = importedPlayback.copy(radioDjs = storage.radioDjPresets())
    settingsStore.savePlaybackSettings(state.playbackSettings)

    state.selectedVisualizer = naviampVisualizerFromName(preferences.visualizer.selectedVisualizer)
    settingsStore.saveVisualizerSettings(preferences.visualizer)

    settingsStore.saveRecentRadioStreams(preferences.recentRadioStreams)
    settingsStore.saveRecentInternetRadioStations(preferences.recentInternetRadioStations)
    state.homeState = state.homeState.copy(
        recentRadioStreams = preferences.recentRadioStreams,
        recentInternetRadioStations = preferences.recentInternetRadioStations.map { it.toStation() },
    )

    val importedProfiles = importSettingsSyncServerProfiles(
        serverProfiles = document.serverProfiles,
        repository = storage,
    )
    state.savedMediaSources = storage.mediaSources()
    val importedConnection = importedProfiles.firstConnectionForm
    if (importedConnection != null) {
        settingsStore.saveConnection(importedConnection)
        state.applyConnectionForm(importedConnection)
        state.savedConnectionForLogin = null
        state.restoringConnection = false
        state.editingConnection = true
        state.navigationState = state.navigationState.copy(route = NaviampRoute.Settings)
        state.status = if (importedProfiles.importedCount > 1) {
            "Settings imported ${importedProfiles.importedCount} server profiles. Enter the Navidrome password to finish connecting."
        } else {
            "Settings imported. Enter the Navidrome password to finish connecting."
        }
        return state.status
    }

    state.status = "Settings imported. Add a Navidrome server to finish setup."
    return state.status
}

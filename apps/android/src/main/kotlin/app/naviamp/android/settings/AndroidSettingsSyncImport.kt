package app.naviamp.android

import android.content.Context
import android.net.Uri
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.settings.SettingsSyncJson
import app.naviamp.domain.settings.SettingsSyncFileName
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.toConnectionFormState
import app.naviamp.ui.naviampVisualizerFromName
import java.io.File

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

fun androidSettingsSyncFile(context: Context): File {
    val directory = context.externalMediaDirs.firstOrNull()
        ?: context.getExternalFilesDir(null)
        ?: context.filesDir
    directory.mkdirs()
    return File(directory, SettingsSyncFileName)
}

fun importAndroidSettingsSyncLocalFile(
    context: Context,
    state: AndroidAppState,
    settingsStore: AndroidSettingsStore,
    storage: AndroidStorageDependencies,
    playbackEngine: AndroidPlaybackEngine,
): String {
    val file = androidSettingsSyncFile(context)
    if (!file.exists()) {
        error("No settings file found at ${file.absolutePath}.")
    }
    return importAndroidSettingsSyncDocumentText(
        text = file.readText(),
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
    val preferences = document.preferences
    val importedPlayback = preferences.playback.effectiveForEngine(playbackEngine)

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

    val importedConnection = document.serverProfiles.firstOrNull()?.toConnectionFormState(password = "")
    if (importedConnection != null) {
        settingsStore.saveConnection(importedConnection)
        state.applyConnectionForm(importedConnection)
        state.savedConnectionForLogin = null
        state.restoringConnection = false
        state.editingConnection = true
        state.navigationState = state.navigationState.copy(route = NaviampRoute.Settings)
        state.status = "Settings imported. Enter the Navidrome password to finish connecting."
        return state.status
    }

    state.status = "Settings imported. Add a Navidrome server to finish setup."
    return state.status
}

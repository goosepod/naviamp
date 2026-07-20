package app.naviamp.desktop

import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.DesktopSettingsSyncSettings
import app.naviamp.desktop.settings.NavigationSettings
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.desktop.settings.SearchSettings
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.toNavidromeConnection

internal data class DesktopRestoredAppState(
    val mediaSource: SavedMediaSource?,
    val connection: NavidromeConnection?,
    val playbackSession: PlaybackSessionSettings?,
    val visualizer: VisualizerSettings,
    val navigation: NavigationSettings,
    val search: SearchSettings,
    val recentRadioStreams: List<RecentRadioStream>,
    val recentPlaylistIds: List<String>,
    val recentInternetRadioStations: List<SavedInternetRadioStation>,
    val settingsSync: DesktopSettingsSyncSettings,
    val playbackSettings: PlaybackSettings,
)

internal fun loadDesktopRestoredAppState(
    storage: DesktopStorageDependencies,
    settingsStore: DesktopSettingsStore,
    playbackSessions: NaviampPlaybackSessionController,
): DesktopRestoredAppState {
    val mediaSource = storage.latestMediaSource()
    val settingsConnection = settingsStore.loadConnection()?.toConnection()
    val playbackSettings = settingsStore.loadPlaybackSettings()
    val storedDjs = storage.radioDjPresets()
    val restoredPlaybackSettings = if (storedDjs.isEmpty() && playbackSettings.radioDjs.isNotEmpty()) {
        storage.replaceRadioDjPresets(playbackSettings.radioDjs)
        playbackSettings.copy(radioDjs = storage.radioDjPresets())
    } else {
        playbackSettings.copy(radioDjs = storedDjs)
    }
    return DesktopRestoredAppState(
        mediaSource = mediaSource,
        connection = mediaSource?.toNavidromeConnection()
            ?.withNativeTokenFrom(settingsConnection)
            ?: settingsConnection,
        playbackSession = playbackSessions.load(),
        visualizer = settingsStore.loadVisualizerSettings(),
        navigation = settingsStore.loadNavigationSettings(),
        search = settingsStore.loadSearchSettings(),
        recentRadioStreams = settingsStore.loadRecentRadioStreams(),
        recentPlaylistIds = settingsStore.loadRecentPlaylistIds(),
        recentInternetRadioStations = settingsStore.loadRecentInternetRadioStations(),
        settingsSync = settingsStore.loadSettingsSync(),
        playbackSettings = restoredPlaybackSettings,
    )
}

internal fun NavidromeConnection.withNativeTokenFrom(
    fallback: NavidromeConnection?,
): NavidromeConnection {
    if (nativeToken?.isNotBlank() == true) return this
    val fallbackToken = fallback?.nativeToken?.takeIf { it.isNotBlank() } ?: return this
    val matchesSavedConnection = fallback.baseUrl == baseUrl && fallback.username == username
    return if (matchesSavedConnection) copy(nativeToken = fallbackToken) else this
}

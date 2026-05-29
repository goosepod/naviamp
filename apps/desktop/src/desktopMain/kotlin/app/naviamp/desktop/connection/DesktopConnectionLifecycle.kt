package app.naviamp.desktop

import app.naviamp.domain.Track
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.deletedMediaSourceUpdate
import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider

data class DesktopActiveConnectionClearState(
    val connectedProvider: NavidromeProvider? = null,
    val connectedSourceId: String? = null,
    val librarySnapshot: LibrarySnapshot = LibrarySnapshot(),
    val libraryStatus: String? = null,
    val homeContent: HomeContent = HomeContent(),
    val homeStatus: String? = null,
    val nowPlayingTrack: Track? = null,
    val nowPlayingCoverArtUrl: String? = null,
    val nowPlayingLyricsStatus: String? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val playbackProgress: PlaybackProgress = PlaybackProgress.Unknown,
    val playbackQueue: PlaybackQueue = PlaybackQueue(),
)

class DesktopConnectionLifecycleController(
    private val sessionCache: DesktopCache,
    private val settingsStore: DesktopSettingsStore,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: PlaylistEngine,
    private val stopRadioContinuation: () -> Unit,
    private val applyClearedConnectionState: (DesktopActiveConnectionClearState) -> Unit,
    private val connectedSourceId: () -> String?,
    private val savedConnectionForLogin: () -> NavidromeConnection?,
    private val setSavedConnectionForLogin: (NavidromeConnection?) -> Unit,
    private val incrementMediaSourcesRevision: () -> Unit,
    private val setConnectionFormOpen: (Boolean) -> Unit,
    private val setConnectionStatus: (String) -> Unit,
    private val setAppRoute: (AppRoute) -> Unit,
) {
    fun clearActiveConnectionState() {
        stopRadioContinuation()
        playlistEngine.clear()
        playbackEngine.stop()
        settingsStore.savePlaybackSession(null)
        applyClearedConnectionState(DesktopActiveConnectionClearState())
    }

    fun resetDatabase() {
        sessionCache.clearAll()
        settingsStore.clearConnection()
        setSavedConnectionForLogin(null)
        incrementMediaSourcesRevision()
        clearActiveConnectionState()
        setConnectionStatus("Database reset. Saved servers were removed.")
        setAppRoute(AppRoute.Settings)
    }

    fun deleteConnection(source: SavedMediaSource) {
        sessionCache.deleteMediaSource(source.id)
        incrementMediaSourcesRevision()
        val savedConnection = savedConnectionForLogin()
        val update = deletedMediaSourceUpdate(
            source = source,
            connectedSourceId = connectedSourceId(),
            savedConnectionBaseUrl = savedConnection?.baseUrl,
            savedConnectionUsername = savedConnection?.username,
        )

        if (update.clearConnectedSource) {
            clearActiveConnectionState()
        }

        if (update.clearSavedConnectionForLogin) {
            setSavedConnectionForLogin(null)
        }
        setConnectionFormOpen(false)
        setConnectionStatus(update.status)
    }
}

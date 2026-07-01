package app.naviamp.desktop

import app.naviamp.domain.Track
import app.naviamp.domain.app.databaseResetStatus
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.connectionFormError
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.connectionFailureStatus
import app.naviamp.domain.source.deletedMediaSourceUpdate
import app.naviamp.desktop.playback.PlaylistCallbacks
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Lyrics
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.provider.navidrome.navidromeConnectionSuccessStatus
import app.naviamp.provider.navidrome.navidromeTlsSettingsFromForm
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    private val scope: CoroutineScope,
    private val cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    private val mediaSourceRepository: MediaSourceRepository,
    private val providerMediaSourceRepository: ProviderMediaSourceRepository,
    private val settingsStore: DesktopSettingsStore,
    private val playbackSessionRepository: PlaybackSessionRepository,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: DesktopPlaylistEngine,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val applyClearedConnectionState: (DesktopActiveConnectionClearState) -> Unit,
    private val serverUrl: () -> String,
    private val username: () -> String,
    private val password: () -> String,
    private val clearPassword: () -> Unit,
    private val connectionName: () -> String,
    private val insecureSkipTlsVerification: () -> Boolean,
    private val customCertificatePath: () -> String,
    private val clientCertificateKeyStorePath: () -> String,
    private val clientCertificateKeyStorePassword: () -> String,
    private val secondaryUrls: () -> List<ConnectionSecondaryUrl>,
    private val customHeaders: () -> List<ConnectionHeaderDefinition>,
    private val selectedMusicFolderIds: () -> List<String>,
    private val isConnecting: () -> Boolean,
    private val setConnecting: (Boolean) -> Unit,
    private val savedPlaybackSession: () -> PlaybackSessionSettings?,
    private val playlistCallbacks: () -> PlaylistCallbacks,
    private val streamQuality: () -> StreamQuality,
    private val replayGainMode: () -> ReplayGainMode,
    private val setConnectedProvider: (NavidromeProvider?) -> Unit,
    private val setConnectedSourceId: (String?) -> Unit,
    private val setHomeContent: (HomeContent) -> Unit,
    private val setHomeStatus: (String?) -> Unit,
    private val setNowPlayingInternetRadioStation: (app.naviamp.domain.InternetRadioStation?) -> Unit,
    private val setNowPlayingStreamMetadata: (PlaybackStreamMetadata) -> Unit,
    private val setNowPlayingTrack: (Track?) -> Unit,
    private val setNowPlayingCoverArtUrl: (String?) -> Unit,
    private val setNowPlayingWaveform: (AudioWaveform?) -> Unit,
    private val setNowPlayingWaveformStatus: (String) -> Unit,
    private val setNowPlayingAudioTags: (List<AudioTag>?) -> Unit,
    private val setNowPlayingLyrics: (Lyrics?) -> Unit,
    private val setNowPlayingLyricsStatus: (String?) -> Unit,
    private val setPlaybackState: (PlaybackState) -> Unit,
    private val setPlaybackProgress: (PlaybackProgress) -> Unit,
    private val setPlaybackQueue: (PlaybackQueue) -> Unit,
    private val refreshLibrarySnapshot: () -> Unit,
    private val loadHomeContent: (NavidromeProvider) -> Unit,
    private val refreshPlaylists: () -> Unit,
    private val refreshInternetRadioStations: () -> Unit,
    private val startLibrarySync: (Boolean) -> Unit,
    private val checkLibraryFreshness: () -> Unit,
    private val connectedSourceId: () -> String?,
    private val savedConnectionForLogin: () -> NavidromeConnection?,
    private val setSavedConnectionForLogin: (NavidromeConnection?) -> Unit,
    private val incrementMediaSourcesRevision: () -> Unit,
    private val applyConnectionFormState: (DesktopConnectionFormState) -> Unit,
    private val setConnectionFormOpen: (Boolean) -> Unit,
    private val setConnectionStatus: (String?) -> Unit,
    private val setAppRoute: (DesktopAppRoute) -> Unit,
    private val appRoute: () -> DesktopAppRoute,
    private val onSyncedSettingsChanged: () -> Unit = {},
) {
    fun openNewConnectionForm() {
        applyConnectionFormState(newDesktopConnectionFormState())
        setConnectionFormOpen(true)
        setConnectionStatus(null)
    }

    fun openSavedConnectionForm(source: SavedMediaSource) {
        applyConnectionFormState(savedDesktopConnectionFormState(source))
        setConnectionFormOpen(true)
        setConnectionStatus("Editing saved connection. Leave password blank to reuse it.")
    }

    fun connectSavedConnection(source: SavedMediaSource) {
        applyConnectionFormState(savedDesktopConnectionFormState(source))
        setConnectionFormOpen(false)
        connectToServer()
    }

    fun closeConnectionForm() {
        setConnectionFormOpen(false)
    }

    fun connectToServer(restoreSavedSession: Boolean = false) {
        if (isConnecting()) return
        val formError = connectionFormError(
            serverUrl = serverUrl(),
            username = username(),
            password = password(),
            hasSavedConnectionForLogin = savedConnectionForLogin() != null,
        )
        if (formError != null) {
            setConnectionStatus(formError)
            setAppRoute(DesktopAppRoute.Settings)
            return
        }

        setConnecting(true)
        setConnectionStatus("Connecting to Navidrome...")
        if (!restoreSavedSession) {
            setHomeContent(HomeContent())
            setHomeStatus(null)
            stopRadioContinuation()
            clearShuffleSnapshot()
            playlistEngine.clear()
            playbackEngine.stop()
            setNowPlayingTrack(null)
            setNowPlayingCoverArtUrl(null)
            setNowPlayingLyricsStatus(null)
            setPlaybackState(PlaybackState.Idle)
            setPlaybackProgress(PlaybackProgress.Unknown)
            setPlaybackQueue(PlaybackQueue())
            playbackSessionRepository.savePlaybackSession(null)
        }

        scope.launch {
            try {
                val session = openDesktopConnectionSession(
                    serverUrl = serverUrl(),
                    username = username(),
                    password = password(),
                    displayName = desktopConnectionDisplayName(
                        connectionName = connectionName(),
                        serverUrl = serverUrl(),
                    ),
                    tlsSettings = navidromeTlsSettingsFromForm(
                        insecureSkipTlsVerification = insecureSkipTlsVerification(),
                        customCertificatePath = customCertificatePath(),
                        clientCertificateKeyStorePath = clientCertificateKeyStorePath(),
                        clientCertificateKeyStorePassword = clientCertificateKeyStorePassword(),
                    ),
                    secondaryUrls = secondaryUrls(),
                    customHeaders = customHeaders(),
                    selectedMusicFolderIds = selectedMusicFolderIds(),
                    savedConnectionForLogin = savedConnectionForLogin(),
                    cacheMaintenanceRepository = cacheMaintenanceRepository,
                    providerMediaSourceRepository = providerMediaSourceRepository,
                    clearProviderData = !restoreSavedSession,
                )
                val connection = session.connection
                val provider = session.provider
                setConnectedProvider(provider)
                setConnectedSourceId(session.sourceId)
                incrementMediaSourcesRevision()
                if (restoreSavedSession) {
                    restorePlaybackSession(provider)
                }
                settingsStore.saveConnection(connection)
                setSavedConnectionForLogin(connection)
                onSyncedSettingsChanged()
                if (connection.nativeToken?.isNotBlank() == true) {
                    clearPassword()
                }
                setConnectionFormOpen(false)
                if (appRoute() == DesktopAppRoute.Settings) {
                    setAppRoute(DesktopAppRoute.Home)
                }
                setConnectionStatus(
                    navidromeConnectionSuccessStatus(
                        validation = session.validation,
                        activeUrl = session.connection.baseUrl,
                        primaryUrl = serverUrl(),
                    ),
                )
                refreshLibrarySnapshot()
                loadHomeContent(provider)
                refreshPlaylists()
                refreshInternetRadioStations()
                startLibrarySync(!restoreSavedSession)
                checkLibraryFreshness()
            } catch (exception: Exception) {
                setConnectedProvider(null)
                setAppRoute(DesktopAppRoute.Settings)
                setConnectionStatus(connectionFailureStatus(exception, fallback = "Could not connect to Navidrome."))
            } finally {
                setConnecting(false)
            }
        }
    }

    fun clearActiveConnectionState() {
        stopRadioContinuation()
        playlistEngine.clear()
        playbackEngine.stop()
        playbackSessionRepository.savePlaybackSession(null)
        applyClearedConnectionState(DesktopActiveConnectionClearState())
    }

    fun resetDatabase() {
        cacheMaintenanceRepository.clearAll()
        settingsStore.clearConnection()
        setSavedConnectionForLogin(null)
        incrementMediaSourcesRevision()
        onSyncedSettingsChanged()
        clearActiveConnectionState()
        setConnectionStatus(databaseResetStatus(savedServersRemoved = true))
        setAppRoute(DesktopAppRoute.Settings)
    }

    fun deleteConnection(source: SavedMediaSource) {
        mediaSourceRepository.deleteMediaSource(source.id)
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
        onSyncedSettingsChanged()
        setConnectionFormOpen(false)
        setConnectionStatus(update.status)
    }

    private fun restorePlaybackSession(provider: NavidromeProvider) {
        when (val restoredSession = restoredDesktopPlaybackSession(savedPlaybackSession(), provider)) {
            is DesktopRestoredPlaybackSession.InternetRadio -> {
                setNowPlayingInternetRadioStation(restoredSession.station)
                setNowPlayingStreamMetadata(PlaybackStreamMetadata())
                setNowPlayingTrack(restoredSession.track)
                setNowPlayingCoverArtUrl(null)
                setNowPlayingWaveform(null)
                setNowPlayingWaveformStatus("Internet radio")
                setNowPlayingAudioTags(null)
                setNowPlayingLyrics(null)
                setNowPlayingLyricsStatus(null)
                setPlaybackProgress(PlaybackProgress.Unknown)
                setPlaybackQueue(PlaybackQueue())
                setPlaybackState(PlaybackState.Idle)
            }
            is DesktopRestoredPlaybackSession.TrackQueue -> {
                setPlaybackProgress(restoredSession.session.playbackProgress)
                playlistEngine.restore(
                    provider = provider,
                    tracks = restoredSession.session.tracks,
                    index = restoredSession.session.currentIndex,
                    quality = streamQuality(),
                    replayGainMode = replayGainMode(),
                    callbacks = playlistCallbacks(),
                    initialProgress = restoredSession.session.playbackProgress,
                )
                setNowPlayingInternetRadioStation(null)
                setNowPlayingStreamMetadata(PlaybackStreamMetadata())
                setNowPlayingTrack(restoredSession.session.currentTrack)
                setNowPlayingCoverArtUrl(restoredSession.coverArtUrl)
                setPlaybackState(PlaybackState.Idle)
            }
            null -> Unit
        }
    }
}

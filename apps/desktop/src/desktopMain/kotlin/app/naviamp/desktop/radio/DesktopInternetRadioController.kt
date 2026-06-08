package app.naviamp.desktop

import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.planPlaybackProgressUpdate
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.internetRadioTrack
import app.naviamp.domain.radio.InternetRadioMetadataUpdateApplier
import app.naviamp.domain.radio.InternetRadioStartApplier
import app.naviamp.domain.radio.InternetRadioStationManager
import app.naviamp.domain.radio.applyInternetRadioMetadataUpdate
import app.naviamp.domain.radio.applyInternetRadioStart
import app.naviamp.domain.radio.internetRadioDeleteErrorStatus
import app.naviamp.domain.radio.internetRadioDeleteLoadingStatus
import app.naviamp.domain.radio.internetRadioRefreshErrorStatus
import app.naviamp.domain.radio.internetRadioRefreshLoadingStatus
import app.naviamp.domain.radio.internetRadioSaveErrorStatus
import app.naviamp.domain.radio.internetRadioSaveLoadingStatus
import app.naviamp.domain.radio.planInternetRadioMetadataUpdate
import app.naviamp.domain.radio.planInternetRadioPlaybackRequest
import app.naviamp.domain.radio.planInternetRadioStart
import app.naviamp.domain.waveform.AudioWaveform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopInternetRadioController(
    private val scope: CoroutineScope,
    private val settingsStore: DesktopSettingsStore,
    private val playbackSessionRepository: PlaybackSessionRepository,
    private val playbackEngine: PlaybackEngine,
    private val playlistEngine: DesktopPlaylistEngine,
    private val provider: () -> MediaProvider?,
    private val stationManager: InternetRadioStationManager,
    private val homeContent: () -> HomeContent,
    private val setHomeContent: (HomeContent) -> Unit,
    private val recentStations: () -> List<InternetRadioStation>,
    private val setRecentStations: (List<InternetRadioStation>) -> Unit,
    private val setStations: (List<InternetRadioStation>) -> Unit,
    private val setStatus: (String?) -> Unit,
    private val stopRadioContinuation: () -> Unit,
    private val clearShuffleSnapshot: () -> Unit,
    private val setNowPlayingTrack: (Track?) -> Unit,
    private val nowPlayingTrack: () -> Track?,
    private val setNowPlayingCoverArtUrl: (String?) -> Unit,
    private val setNowPlayingWaveform: (AudioWaveform?) -> Unit,
    private val setNowPlayingWaveformStatus: (String) -> Unit,
    private val setNowPlayingAudioTags: (List<AudioTag>?) -> Unit,
    private val setNowPlayingLyrics: (Lyrics?) -> Unit,
    private val setNowPlayingLyricsStatus: (String?) -> Unit,
    private val nowPlayingStation: () -> InternetRadioStation?,
    private val setNowPlayingStation: (InternetRadioStation?) -> Unit,
    private val setNowPlayingStreamMetadata: (PlaybackStreamMetadata) -> Unit,
    private val playbackProgress: () -> PlaybackProgress,
    private val setPlaybackProgress: (PlaybackProgress) -> Unit,
    private val setPlaybackQueue: (PlaybackQueue) -> Unit,
    private val setPlaybackState: (PlaybackState) -> Unit,
    private val lastProgressUiUpdateMillis: () -> Long,
    private val setLastProgressUiUpdateMillis: (Long) -> Unit,
    private val restoredPlaybackPositionSeconds: () -> Double?,
    private val setRestoredPlaybackPositionSeconds: (Double?) -> Unit,
    private val setAppRoute: (DesktopAppRoute) -> Unit,
) {
    fun refreshStations() {
        val activeProvider = provider() ?: return
        setStatus(internetRadioRefreshLoadingStatus())
        scope.launch {
            try {
                setStations(
                    withContext(Dispatchers.IO) {
                        stationManager.refreshStations(activeProvider)
                    },
                )
                setStatus(null)
            } catch (exception: Exception) {
                setStatus(exception.message ?: internetRadioRefreshErrorStatus())
            }
        }
    }

    fun rememberStation(station: InternetRadioStation) {
        val plan = planInternetRadioStart(
            station = station,
            recentStations = recentStations(),
            recentSavedStations = settingsStore.loadRecentInternetRadioStations(),
        )
        settingsStore.saveRecentInternetRadioStations(plan.recentSavedStations)
        setRecentStations(plan.recentStations)
        setHomeContent(homeContent().copy(recentInternetRadioStations = plan.recentStations))
    }

    fun playStation(station: InternetRadioStation) {
        val plan = planInternetRadioStart(
            station = station,
            recentStations = recentStations(),
            recentSavedStations = settingsStore.loadRecentInternetRadioStations(),
        )
        val radioTrack = internetRadioTrack(station)
        applyInternetRadioStart(
            plan = plan,
            nowPlayingTrack = radioTrack,
            applier = InternetRadioStartApplier(
                saveRecentStations = settingsStore::saveRecentInternetRadioStations,
                setRecentStations = { updatedRecentStations ->
                    setRecentStations(updatedRecentStations)
                    setHomeContent(homeContent().copy(recentInternetRadioStations = updatedRecentStations))
                },
                clearRadioContinuation = stopRadioContinuation,
                clearShuffleSnapshot = clearShuffleSnapshot,
                clearPlaybackQueue = { playlistEngine.clear() },
                setNowPlayingTrack = setNowPlayingTrack,
                setNowPlayingCoverArtUrl = setNowPlayingCoverArtUrl,
                resetNowPlayingSidecars = {
                    setNowPlayingWaveform(null)
                    setNowPlayingWaveformStatus("Internet radio")
                    setNowPlayingAudioTags(null)
                    setNowPlayingLyrics(null)
                    setNowPlayingLyricsStatus(null)
                },
                setNowPlayingStation = setNowPlayingStation,
                setStreamMetadata = setNowPlayingStreamMetadata,
                setPlaybackProgress = setPlaybackProgress,
                setPlaybackQueue = setPlaybackQueue,
                setStatus = setStatus,
                savePlaybackSession = {
                    playbackSessionRepository.savePlaybackSession(PlaybackSessionSettings.fromInternetRadioStation(station))
                },
                openNowPlaying = { setAppRoute(DesktopAppRoute.Player) },
            ),
        )
        playbackEngine.play(
            scope = scope,
            request = planInternetRadioPlaybackRequest(
                startPlan = plan,
                streamUrl = station.streamUrl,
                replayGainMode = ReplayGainMode.Off,
            ).request,
            onStateChanged = { state ->
                setPlaybackState(state)
            },
            onProgressChanged = { progress ->
                val now = System.currentTimeMillis()
                val liveProgress = progress.copy(durationSeconds = null)
                val progressPlan = planPlaybackProgressUpdate(
                    sessionToken = 1,
                    activeSessionToken = 1,
                    incomingProgress = liveProgress,
                    currentProgress = playbackProgress(),
                    pendingSeekPositionSeconds = null,
                    pendingSeekIssuedAtMillis = null,
                    pendingRestoreStartPositionSeconds = null,
                    nowMillis = now,
                    lastExternalProgressPublishAtMillis = 0,
                    externalProgressPublishIntervalMillis = Long.MAX_VALUE,
                    resetUnknownProgress = false,
                    mergeMissingProgressFields = false,
                    reportPlayed = false,
                    prepareNext = false,
                    lastUiUpdateMillis = lastProgressUiUpdateMillis(),
                    positionThresholdSeconds = PlaybackProgressUiUpdateThresholdSeconds,
                    uiUpdateIntervalMillis = PlaybackProgressUiUpdateIntervalMillis,
                )
                if (progressPlan.shouldUpdateUi) {
                    setPlaybackProgress(progressPlan.progress ?: liveProgress)
                    setLastProgressUiUpdateMillis(now)
                }
            },
            onMetadataChanged = { metadata ->
                applyInternetRadioMetadataUpdate(
                    plan = planInternetRadioMetadataUpdate(
                        station = station,
                        metadata = metadata,
                        fallbackTrack = radioTrack,
                        updateNotificationMetadata = false,
                    ),
                    applier = InternetRadioMetadataUpdateApplier(
                        setStreamMetadata = setNowPlayingStreamMetadata,
                        setNowPlayingTrack = setNowPlayingTrack,
                    ),
                )
            },
        )
    }

    fun playCurrentSelection() {
        val station = nowPlayingStation()
        if (station != null || nowPlayingTrack()?.isInternetRadioTrack() == true) {
            station?.let(::playStation)
            return
        }
        val restoredPosition = restoredPlaybackPositionSeconds()
        setRestoredPlaybackPositionSeconds(null)
        playlistEngine.playCurrent(scope, restoredPosition)
    }

    fun saveStation(station: InternetRadioStation) {
        val activeProvider = provider() ?: return
        setStatus(internetRadioSaveLoadingStatus(station))
        scope.launch {
            try {
                val stations = withContext(Dispatchers.IO) { stationManager.saveStation(activeProvider, station) }
                setStations(stations)
                setStatus(null)
            } catch (exception: Exception) {
                setStatus(exception.message ?: internetRadioSaveErrorStatus())
            }
        }
    }

    fun deleteStation(station: InternetRadioStation) {
        val activeProvider = provider() ?: return
        setStatus(internetRadioDeleteLoadingStatus(station))
        scope.launch {
            try {
                val stations = withContext(Dispatchers.IO) { stationManager.deleteStation(activeProvider, station) }
                setStations(stations)
                setStatus(null)
            } catch (exception: Exception) {
                setStatus(exception.message ?: internetRadioDeleteErrorStatus())
            }
        }
    }
}

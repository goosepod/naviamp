package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import app.naviamp.domain.radio.InternetRadioRecentStationApplier
import app.naviamp.domain.radio.InternetRadioStartApplier
import app.naviamp.domain.radio.InternetRadioStationManager
import app.naviamp.domain.radio.applyInternetRadioMetadataUpdate
import app.naviamp.domain.radio.applyRememberInternetRadioStation
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
import app.naviamp.domain.radio.planRememberInternetRadioStation
import app.naviamp.domain.settings.SavedInternetRadioStation
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
    initialRecentStations: List<InternetRadioStation>,
    private val saveRecentInternetRadioStations: (List<SavedInternetRadioStation>) -> Unit,
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
    var stations by mutableStateOf<List<InternetRadioStation>>(emptyList())
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var recentStations by mutableStateOf(initialRecentStations)
        private set

    private fun updateRecentStations(stations: List<InternetRadioStation>) {
        recentStations = stations
        setHomeContent(homeContent().copy(recentInternetRadioStations = stations))
    }

    fun refreshStations() {
        val activeProvider = provider() ?: return
        status = internetRadioRefreshLoadingStatus()
        scope.launch {
            try {
                stations = withContext(Dispatchers.IO) {
                    stationManager.refreshStations(activeProvider)
                }
                status = null
            } catch (exception: Exception) {
                status = exception.message ?: internetRadioRefreshErrorStatus()
            }
        }
    }

    fun rememberStation(station: InternetRadioStation) {
        applyRememberInternetRadioStation(
            plan = planRememberInternetRadioStation(
                station = station,
                recentStations = recentStations,
                recentSavedStations = settingsStore.loadRecentInternetRadioStations(),
            ),
            applier = InternetRadioRecentStationApplier(
                saveRecentStations = saveRecentInternetRadioStations,
                setRecentStations = ::updateRecentStations,
            ),
        )
    }

    fun playStation(station: InternetRadioStation) {
        val plan = planInternetRadioStart(
            station = station,
            recentStations = recentStations,
            recentSavedStations = settingsStore.loadRecentInternetRadioStations(),
        )
        val radioTrack = internetRadioTrack(station)
        applyInternetRadioStart(
            plan = plan,
            nowPlayingTrack = radioTrack,
            applier = InternetRadioStartApplier(
                saveRecentStations = saveRecentInternetRadioStations,
                setRecentStations = ::updateRecentStations,
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
                setStatus = { playbackStatus -> status = playbackStatus },
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
            ?: playbackProgress().positionSeconds?.takeIf { it > 0.0 }
        setRestoredPlaybackPositionSeconds(null)
        playlistEngine.playCurrent(scope, restoredPosition)
    }

    fun saveStation(station: InternetRadioStation) {
        val activeProvider = provider() ?: return
        status = internetRadioSaveLoadingStatus(station)
        scope.launch {
            try {
                stations = withContext(Dispatchers.IO) { stationManager.saveStation(activeProvider, station) }
                status = null
            } catch (exception: Exception) {
                status = exception.message ?: internetRadioSaveErrorStatus()
            }
        }
    }

    fun deleteStation(station: InternetRadioStation) {
        val activeProvider = provider() ?: return
        status = internetRadioDeleteLoadingStatus(station)
        scope.launch {
            try {
                stations = withContext(Dispatchers.IO) { stationManager.deleteStation(activeProvider, station) }
                status = null
            } catch (exception: Exception) {
                status = exception.message ?: internetRadioDeleteErrorStatus()
            }
        }
    }
}

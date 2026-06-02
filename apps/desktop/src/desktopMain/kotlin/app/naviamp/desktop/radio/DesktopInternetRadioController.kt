package app.naviamp.desktop

import app.naviamp.desktop.playback.PlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.PlaybackSessionSettings
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.shouldUpdatePlaybackProgressUi
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.internetRadioTrack
import app.naviamp.domain.radio.internetRadioTrackWithMetadata
import app.naviamp.domain.radio.planInternetRadioStart
import app.naviamp.domain.radio.recentInternetRadioStationsWith
import app.naviamp.domain.radio.recentSavedInternetRadioStationsWith
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
    private val playlistEngine: PlaylistEngine,
    private val provider: () -> MediaProvider?,
    private val providerResponseService: ProviderResponseService,
    private val homeContent: () -> HomeContent,
    private val setHomeContent: (HomeContent) -> Unit,
    private val recentStations: () -> List<InternetRadioStation>,
    private val setRecentStations: (List<InternetRadioStation>) -> Unit,
    private val setStations: (List<InternetRadioStation>) -> Unit,
    private val setStatus: (String?) -> Unit,
    private val setNewStationDialogOpen: (Boolean) -> Unit,
    private val setPendingEdit: (InternetRadioStation?) -> Unit,
    private val setPendingDelete: (InternetRadioStation?) -> Unit,
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
    private val setAppRoute: (AppRoute) -> Unit,
) {
    fun refreshStations() {
        val activeProvider = provider() ?: return
        setStatus("Loading internet radio...")
        scope.launch {
            try {
                setStations(
                    withContext(Dispatchers.IO) {
                        providerResponseService.internetRadioStations(activeProvider)
                    },
                )
                setStatus(null)
            } catch (exception: Exception) {
                setStatus(exception.message ?: "Could not load internet radio stations.")
            }
        }
    }

    fun rememberStation(station: InternetRadioStation) {
        val recent = recentInternetRadioStationsWith(recentStations(), station)
        setRecentStations(recent)
        settingsStore.saveRecentInternetRadioStations(
            recentSavedInternetRadioStationsWith(
                settingsStore.loadRecentInternetRadioStations(),
                station,
            ),
        )
        setHomeContent(homeContent().copy(recentInternetRadioStations = recent))
    }

    fun playStation(station: InternetRadioStation) {
        val plan = planInternetRadioStart(
            station = station,
            recentStations = recentStations(),
            recentSavedStations = settingsStore.loadRecentInternetRadioStations(),
        )
        setRecentStations(plan.recentStations)
        settingsStore.saveRecentInternetRadioStations(plan.recentSavedStations)
        setHomeContent(homeContent().copy(recentInternetRadioStations = plan.recentStations))
        if (plan.clearRadioContinuation) stopRadioContinuation()
        if (plan.clearShuffleSnapshot) clearShuffleSnapshot()
        playlistEngine.clear()
        val radioTrack = internetRadioTrack(station)
        setNowPlayingTrack(radioTrack)
        setNowPlayingCoverArtUrl(null)
        setNowPlayingWaveform(null)
        setNowPlayingWaveformStatus("Internet radio")
        setNowPlayingAudioTags(null)
        setNowPlayingLyrics(null)
        setNowPlayingLyricsStatus(null)
        setNowPlayingStation(plan.station)
        setNowPlayingStreamMetadata(plan.streamMetadata)
        setPlaybackProgress(plan.playbackProgress)
        setPlaybackQueue(plan.playbackQueue)
        setStatus(plan.status)
        if (plan.savePlaybackSession) {
            playbackSessionRepository.savePlaybackSession(PlaybackSessionSettings.fromInternetRadioStation(station))
        }
        if (plan.openNowPlaying) setAppRoute(AppRoute.Player)
        playbackEngine.play(
            scope = scope,
            request = PlaybackRequest(
                url = station.streamUrl,
                mediaId = plan.engineMediaId,
                replayGainMode = ReplayGainMode.Off,
            ),
            onStateChanged = { state ->
                setPlaybackState(state)
            },
            onProgressChanged = { progress ->
                val now = System.currentTimeMillis()
                val liveProgress = progress.copy(durationSeconds = null)
                if (
                    shouldUpdatePlaybackProgressUi(
                        pendingSeekPositionSeconds = null,
                        currentProgress = playbackProgress(),
                        mergedProgress = liveProgress,
                        nowMillis = now,
                        lastUiUpdateMillis = lastProgressUiUpdateMillis(),
                        positionThresholdSeconds = PlaybackProgressUiUpdateThresholdSeconds,
                        updateIntervalMillis = PlaybackProgressUiUpdateIntervalMillis,
                    )
                ) {
                    setPlaybackProgress(liveProgress)
                    setLastProgressUiUpdateMillis(now)
                }
            },
            onMetadataChanged = { metadata ->
                setNowPlayingStreamMetadata(metadata)
                setNowPlayingTrack(internetRadioTrackWithMetadata(radioTrack, station, metadata))
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
        setStatus("Saving ${station.name}...")
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (station.id == station.streamUrl) {
                        activeProvider.createInternetRadioStation(
                            name = station.name,
                            streamUrl = station.streamUrl,
                            homePageUrl = station.homePageUrl,
                        )
                    } else {
                        activeProvider.updateInternetRadioStation(station)
                    }
                    providerResponseService.invalidateInternetRadioStations(activeProvider)
                }
                setNewStationDialogOpen(false)
                setPendingEdit(null)
                setStatus(null)
                refreshStations()
            } catch (exception: Exception) {
                setStatus(exception.message ?: "Could not save station.")
            }
        }
    }

    fun deleteStation(station: InternetRadioStation) {
        val activeProvider = provider() ?: return
        setStatus("Deleting ${station.name}...")
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activeProvider.deleteInternetRadioStation(station.id)
                    providerResponseService.invalidateInternetRadioStations(activeProvider)
                }
                setPendingDelete(null)
                setStatus(null)
                refreshStations()
            } catch (exception: Exception) {
                setStatus(exception.message ?: "Could not delete station.")
            }
        }
    }
}

package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.ui.NaviampVisualizer

data class NaviampCorePlaybackCapabilities(
    val engineName: String = "Playback engine",
    val supportsPause: Boolean = true,
    val supportsSeek: Boolean = true,
    val supportsSoftwareVolume: Boolean = true,
    val supportsVisualizer: Boolean = false,
)

/** Native audio boundary. Queue and navigation policy have already been resolved by Core. */
interface NaviampCorePlaybackEffectPort : NaviampPlaybackExecution {
    val capabilities: NaviampCorePlaybackCapabilities
    val playbackSource: PlaybackSource
    val playbackQuality: StreamQuality?
        get() = null

    /** Connects native engine observations to Core without giving the host product-state access. */
    fun attach(observer: NaviampCorePlaybackObserver) = Unit

    /** Enables expensive native FFT sampling only while shared UI has a visualizer consumer. */
    fun setVisualizerFramesEnabled(enabled: Boolean) = Unit

    fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean)
    fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) {
        applyQueue(queue, clearPreparedNext = true)
    }
    fun restoreInternetRadio(station: InternetRadioStation) = Unit
    fun applyNavigation(command: PlaybackQueueNavigationCommand)
    fun applyAutomaticNavigation(command: PlaybackQueueNavigationCommand) = applyNavigation(command)
    fun applyRepeatMode(mode: RepeatMode)
    fun playQueueSelection(queue: PlaybackQueue, index: Int)
    fun diagnostics(): List<Pair<String, String>> = emptyList()
}

interface NaviampCorePlaybackObserver {
    fun onStateChanged(state: PlaybackState)
    fun onProgressChanged(progress: PlaybackProgress)
    fun onMetadataChanged(metadata: PlaybackStreamMetadata)
    fun onVisualizerFrameChanged(frame: PlaybackVisualizerFrame?) = Unit
}

data class NaviampCoreNowPlayingSidecars(
    val waveform: AudioWaveform? = null,
    val audioTags: List<AudioTag>? = null,
    val lyrics: Lyrics? = null,
    val lyricsStatus: String? = null,
    val streamMetadata: PlaybackStreamMetadata = PlaybackStreamMetadata(),
    val visualizerFrame: PlaybackVisualizerFrame? = null,
    val relatedTracks: List<Track> = emptyList(),
    val relatedTracksSource: RelatedTracksSource = RelatedTracksSource.None,
    val relatedSimilarityByTrackId: Map<TrackId, Double> = emptyMap(),
    val internetRadioStations: List<InternetRadioStation> = emptyList(),
    val currentInternetRadioStationId: String? = null,
    val radioTrackArtworkByKey: Map<String, String?> = emptyMap(),
)

/** Cache/provider sidecar work; Core owns visibility and when these effects are requested. */
interface NaviampCoreNowPlayingSidecarPort {
    fun snapshot(): NaviampCoreNowPlayingSidecars
    suspend fun loadForTrack(track: Track)
    suspend fun loadLyrics(track: Track)
    suspend fun changeLyricsOffset(track: Track, offsetMillis: Int)
    fun updateStreamMetadata(metadata: PlaybackStreamMetadata) = Unit
    suspend fun loadInternetRadioArtwork(
        station: InternetRadioStation,
        metadata: PlaybackStreamMetadata,
    ) = Unit
    fun updateVisualizerFrame(frame: PlaybackVisualizerFrame?) = Unit
}

fun interface NaviampCorePlaybackSettingsPort {
    fun apply(settings: PlaybackSettings, redownload: Boolean): PlaybackSettings
}

interface NaviampCoreVisualizerSettingsPort {
    fun save(visualizer: NaviampVisualizer)
}

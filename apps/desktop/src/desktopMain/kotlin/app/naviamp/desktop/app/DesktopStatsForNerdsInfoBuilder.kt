package app.naviamp.desktop

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeProvider

internal fun desktopStatsForNerdsInfoOrNull(
    showStatsForNerds: Boolean,
    appRoute: DesktopAppRoute,
    connectionForm: DesktopConnectionFormStateHolder,
    connectedProvider: NavidromeProvider?,
    connectedSourceId: String?,
    storage: DesktopStorageDependencies,
    connectionStatus: String?,
    isLibrarySyncing: Boolean,
    libraryStatus: String?,
    libraryTab: DesktopLibraryTab,
    libraryQuery: String,
    librarySnapshot: LibrarySnapshot,
    playbackEngine: PlaybackEngine,
    playlistEngine: DesktopPlaylistEngine,
    playbackQueue: PlaybackQueue,
    nowPlayingTrack: Track?,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackSettings: PlaybackSettings,
    nowPlayingWaveform: AudioWaveform?,
    nowPlayingWaveformStatus: String,
    nowPlayingInternetRadioStation: InternetRadioStation?,
    nowPlayingStreamMetadata: PlaybackStreamMetadata,
    cacheStats: app.naviamp.domain.cache.StorageCacheStats,
): DesktopStatsForNerdsInfo? {
    if (!showStatsForNerds) return null
    val streamQuality = playbackSettings.streamQuality(playbackEngine)
    val currentAudioCacheMetadata = connectedSourceId
        ?.let { sourceId ->
            nowPlayingTrack?.let { track ->
                storage.cachedAudioMetadata(sourceId, track.id, streamQuality)
            }
        }
    return buildDesktopStatsForNerdsInfo(
        route = appRoute.name,
        serverUrl = connectionForm.serverUrl,
        username = connectionForm.username,
        connectedProvider = connectedProvider,
        mediaSource = connectedSourceId?.let { storage.mediaSource(it) } ?: storage.latestMediaSource(),
        connectionStatus = connectionStatus,
        isLibrarySyncing = isLibrarySyncing,
        libraryStatus = libraryStatus,
        libraryTabLabel = libraryTab.label,
        libraryQuery = libraryQuery,
        librarySnapshot = librarySnapshot,
        playbackEngine = playbackEngine,
        playlistEngine = playlistEngine,
        playbackQueue = playbackQueue,
        nowPlayingTrack = nowPlayingTrack,
        playbackState = playbackState,
        playbackProgress = playbackProgress,
        playbackSettings = playbackSettings,
        streamQuality = streamQuality,
        nowPlayingWaveform = nowPlayingWaveform,
        nowPlayingWaveformStatus = nowPlayingWaveformStatus,
        cachedAudio = currentAudioCacheMetadata,
        nowPlayingInternetRadioStation = nowPlayingInternetRadioStation,
        nowPlayingStreamMetadata = nowPlayingStreamMetadata,
        cacheStats = cacheStats,
    )
}

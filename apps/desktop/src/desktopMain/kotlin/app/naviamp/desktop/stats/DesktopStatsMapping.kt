package app.naviamp.desktop

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.label
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.ConnectionFormMusicFolder
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.desktop.playback.DesktopPlaybackEngineDiagnostics
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.ui.bytesLabel
import app.naviamp.ui.durationLabel
import app.naviamp.ui.label
import app.naviamp.ui.nowPlayingAlbumLine

fun buildDesktopStatsForNerdsInfo(
    route: String,
    serverUrl: String,
    username: String,
    connectedProvider: NavidromeProvider?,
    mediaSource: SavedMediaSource?,
    availableMusicFolders: List<ConnectionFormMusicFolder>,
    connectionStatus: String?,
    isLibrarySyncing: Boolean,
    libraryStatus: String?,
    libraryTabLabel: String,
    libraryQuery: String,
    librarySnapshot: app.naviamp.domain.cache.LibrarySnapshot,
    playbackEngine: PlaybackEngine,
    playlistEngine: DesktopPlaylistEngine,
    playbackQueue: PlaybackQueue,
    nowPlayingTrack: Track?,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackSettings: PlaybackSettings,
    streamQuality: StreamQuality,
    nowPlayingWaveform: AudioWaveform?,
    nowPlayingWaveformStatus: String,
    cachedAudio: CachedAudioMetadata?,
    nowPlayingInternetRadioStation: InternetRadioStation?,
    nowPlayingStreamMetadata: PlaybackStreamMetadata,
    cacheStats: StorageCacheStats,
): DesktopStatsForNerdsInfo =
    DesktopStatsForNerdsInfo(
        route = route,
        os = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
        javaVersion = System.getProperty("java.version"),
        workingDirectory = System.getProperty("user.dir"),
        serverUrl = serverUrl,
        username = username,
        providerName = connectedProvider?.displayName ?: "Not connected",
        providerCacheNamespace = connectedProvider?.cacheNamespace ?: "Not connected",
        mediaSource = mediaSource?.toStats(availableMusicFolders),
        connectionStatus = connectionStatus,
        librarySync = DesktopLibrarySyncStats(
            isSyncing = isLibrarySyncing,
            status = libraryStatus ?: "Idle",
            selectedTab = libraryTabLabel,
            query = libraryQuery,
            visibleArtists = librarySnapshot.artists.size,
            visibleAlbums = librarySnapshot.albums.size,
            visibleTracks = librarySnapshot.tracks.size,
        ),
        playbackEngineName = playbackEngine.name,
        playbackCapabilities = playbackEngine.capabilitiesLabel(),
        playbackEngineStats = (playbackEngine as? DesktopPlaybackEngineDiagnostics)?.statsRows().orEmpty(),
        queueSize = playbackQueue.tracks.size,
        currentQueueIndex = playbackQueue.currentIndex,
        cacheRuntime = playlistEngine.cacheRuntimeStats(),
        stream = nowPlayingTrack?.toStreamStats(
            playbackState = playbackState,
            playbackProgress = playbackProgress,
            playbackSettings = playbackSettings,
            streamQuality = streamQuality,
            waveform = nowPlayingWaveform,
            waveformStatus = nowPlayingWaveformStatus,
            cachedAudio = cachedAudio,
            internetRadioStation = nowPlayingInternetRadioStation,
            streamMetadata = nowPlayingStreamMetadata,
        ),
        cacheStats = cacheStats,
        providerCapabilities = connectedProvider?.capabilities?.asStatsMap().orEmpty(),
        apiCalls = recentDesktopApiCallStats(),
    )

private fun recentDesktopApiCallStats(): List<DesktopApiCallStats> =
    (
        NavidromeApiCallHistory.recent(50).map { call ->
            DesktopApiCallStats(
                source = "Navidrome",
                endpoint = "${call.method} ${call.endpoint}",
                sanitizedUrl = call.sanitizedUrl,
                startedAtEpochMillis = call.startedAtEpochMillis,
                durationMillis = call.durationMillis,
                success = call.success,
                errorMessage = call.errorMessage,
            )
        } + DesktopLrclibApiCallHistory.recent(50).map { call ->
            DesktopApiCallStats(
                source = "LRCLIB",
                endpoint = call.endpoint,
                sanitizedUrl = call.sanitizedUrl,
                startedAtEpochMillis = call.startedAtEpochMillis,
                durationMillis = call.durationMillis,
                success = call.success,
                errorMessage = call.errorMessage,
            )
        }
    ).sortedByDescending { it.startedAtEpochMillis }.take(50)

fun PlaybackEngine.capabilitiesLabel(): String =
    listOf(
        "pause" to supportsPause,
        "seek" to supportsSeek,
        "gapless" to supportsGapless,
        "crossfade" to supportsCrossfade,
        "ReplayGain" to supportsReplayGain,
        "volume" to supportsSoftwareVolume,
    ).joinToString(", ") { (label, supported) ->
        if (supported) label else "no $label"
    }

fun Track.toStreamStats(
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackSettings: PlaybackSettings,
    streamQuality: StreamQuality,
    waveform: AudioWaveform?,
    waveformStatus: String,
    cachedAudio: CachedAudioMetadata?,
    internetRadioStation: InternetRadioStation?,
    streamMetadata: PlaybackStreamMetadata,
): DesktopStreamStats {
    val effectiveDurationSeconds = durationSeconds?.toDouble() ?: playbackProgress.durationSeconds
    val audio = audioInfo
    return DesktopStreamStats(
        state = playbackState.label(),
        source = if (internetRadioStation != null || isInternetRadioTrack()) "Internet radio" else "Library track",
        trackId = id.value,
        stationId = internetRadioStation?.id ?: "None",
        stationName = internetRadioStation?.name ?: "None",
        stationStreamUrl = internetRadioStation?.streamUrl ?: "None",
        stationHomePageUrl = internetRadioStation?.homePageUrl ?: "None",
        title = title,
        artist = artistName,
        album = nowPlayingAlbumLine().ifBlank { "Unknown album" },
        duration = durationLabel(),
        progress = playbackProgress.label(effectiveDurationSeconds),
        streamQuality = streamQuality.label(),
        isTranscoded = if (streamQuality is StreamQuality.Transcoded) "Yes" else "No",
        replayGainMode = playbackSettings.replayGainMode.displayName,
        codec = audio?.codec ?: "Unknown",
        bitrate = audio?.bitrateKbps?.let { "$it kbps" } ?: "Unknown",
        contentType = audio?.contentType ?: "Unknown",
        coverArtId = coverArtId ?: "None",
        waveformStatus = waveformStatus,
        waveformBuckets = waveform?.amplitudes?.size?.toString() ?: "None",
        audioCacheStatus = cachedAudio?.let { if (it.exists) "Cached" else "Missing file" } ?: "Not cached",
        audioCacheSize = cachedAudio?.sizeBytes?.bytesLabel() ?: "None",
        audioCachePath = cachedAudio?.path?.toAbsolutePath()?.toString() ?: "None",
        streamMetadataTitle = streamMetadata.title ?: "None",
        streamMetadataProperties = streamMetadata.properties.entries
            .sortedBy { it.key.lowercase() }
            .joinToString(", ") { (key, value) -> "$key=$value" }
            .ifBlank { "None" },
    )
}

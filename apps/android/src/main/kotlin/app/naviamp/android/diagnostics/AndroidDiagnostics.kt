package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats

import app.naviamp.android.playback.AndroidBassLoadReport
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.label
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.provider.navidrome.NavidromeApiCall
import app.naviamp.provider.navidrome.NavidromeApiCallHistory
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.bytesLabel
import app.naviamp.ui.label as streamQualityLabel

fun rememberAndroidDiagnostics(
    selectedRoute: SharedRoute,
    storageStats: StorageCacheStats,
    provider: MediaProvider?,
    validation: ConnectionValidation?,
    activeSourceId: String?,
    bassLoadReport: AndroidBassLoadReport,
    playbackEngine: AndroidPlaybackEngine,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackQueue: PlaybackQueue,
    playbackSettings: PlaybackSettings,
    streamQuality: StreamQuality,
    nowPlaying: Track?,
    nowPlayingStation: InternetRadioStation?,
    nowPlayingStreamMetadata: PlaybackStreamMetadata,
    nowPlayingOpen: Boolean,
    visualizerVisible: Boolean,
    activeTlsSettings: NavidromeTlsSettings,
): NaviampDiagnosticsUi {
    if (selectedRoute != SharedRoute.Settings) return NaviampDiagnosticsUi()
    return androidDiagnostics(
        storageStats = storageStats,
        provider = provider,
        validation = validation,
        activeSourceId = activeSourceId,
        bassLoadReport = bassLoadReport,
        playbackEngine = playbackEngine,
        playbackState = playbackState,
        playbackProgress = playbackProgress,
        playbackQueue = playbackQueue,
        playbackSettings = playbackSettings,
        streamQuality = streamQuality,
        nowPlaying = nowPlaying,
        nowPlayingStation = nowPlayingStation,
        nowPlayingStreamMetadata = nowPlayingStreamMetadata,
        nowPlayingOpen = nowPlayingOpen,
        visualizerVisible = visualizerVisible,
        activeTlsSettings = activeTlsSettings,
        selectedRoute = selectedRoute,
    )
}

fun androidDiagnostics(
    storageStats: StorageCacheStats,
    provider: MediaProvider?,
    validation: ConnectionValidation?,
    activeSourceId: String?,
    bassLoadReport: AndroidBassLoadReport,
    playbackEngine: AndroidPlaybackEngine,
    playbackState: PlaybackState,
    playbackProgress: PlaybackProgress,
    playbackQueue: PlaybackQueue,
    playbackSettings: PlaybackSettings,
    streamQuality: StreamQuality,
    nowPlaying: Track?,
    nowPlayingStation: InternetRadioStation?,
    nowPlayingStreamMetadata: PlaybackStreamMetadata,
    nowPlayingOpen: Boolean,
    visualizerVisible: Boolean,
    activeTlsSettings: NavidromeTlsSettings,
    selectedRoute: SharedRoute,
): NaviampDiagnosticsUi =
    NaviampDiagnosticsUi(
        sections = listOf(
            NaviampDiagnosticsSectionUi(
                title = "Connection",
                rows = listOf(
                    "Provider" to (provider?.displayName ?: "Not connected"),
                    "Source ID" to (activeSourceId ?: "None"),
                    "Server" to (validation?.serverVersion ?: "Unknown"),
                    "API" to (validation?.apiVersion ?: "Unknown"),
                    "Route" to selectedRoute.label,
                    "Skip TLS verification" to activeTlsSettings.insecureSkipTlsVerification.toString(),
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "API calls",
                rows = androidApiCallRows(),
            ),
            NaviampDiagnosticsSectionUi(
                title = "BASS",
                rows = listOf(
                    "Available" to bassLoadReport.available.toString(),
                    "Loaded libraries" to "${bassLoadReport.loadedLibraries.size}: ${bassLoadReport.loadedLibraries.joinToString(", ")}",
                    "Failed libraries" to bassLoadReport.failedLibraries.ifEmpty { null }
                        ?.joinToString(", ") { "${it.name}: ${it.message}" }
                        .orEmpty()
                        .ifBlank { "None" },
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "Playback",
                rows = listOf(
                    "Engine" to playbackEngine.name,
                    "State" to playbackState.label(),
                    "Now playing" to (nowPlaying?.title ?: nowPlayingStation?.name ?: "None"),
                    "Stream title" to (nowPlayingStreamMetadata.title ?: "None"),
                    "Now Playing screen" to nowPlayingOpen.toString(),
                    "Queue" to "${playbackQueue.tracks.size} tracks, index ${playbackQueue.currentIndex}",
                    "Position" to playbackProgress.positionSeconds?.let { "%.1fs".format(it) }.orEmpty().ifBlank { "Unknown" },
                    "Duration" to playbackProgress.durationSeconds?.let { "%.1fs".format(it) }.orEmpty().ifBlank { "Unknown" },
                    "Transcoded" to if (streamQuality is StreamQuality.Transcoded) "Yes" else "No",
                    "Stream quality" to streamQuality.streamQualityLabel(),
                    "ReplayGain" to playbackSettings.replayGainMode.name,
                    "Gapless" to playbackSettings.gaplessEnabled.toString(),
                    "Crossfade" to "${playbackSettings.crossfadeDurationSeconds}s",
                    "Visualizer" to visualizerVisible.toString(),
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "Storage",
                rows = listOf(
                    "Database" to storageStats.databaseLabel,
                    "Saved sources" to storageStats.mediaSourceCount.toString(),
                    "Saved sessions" to storageStats.playbackSessionCount.toString(),
                    "Library index" to "${storageStats.libraryArtistCount} artists, ${storageStats.libraryAlbumCount} albums, ${storageStats.libraryTrackCount} tracks",
                    "Images" to "${storageStats.imageCount} (${storageStats.imageBytes.bytesLabel()})",
                    "Provider responses" to storageStats.responseCount.toString(),
                    "Audio cache" to "${storageStats.audioCount} (${storageStats.audioBytes.bytesLabel()})",
                    "Downloads" to "${storageStats.downloadCount} (${storageStats.downloadBytes.bytesLabel()})",
                    "Waveforms" to "${storageStats.audioWaveformCount} (${storageStats.audioWaveformBytes.bytesLabel()})",
                    "Lyrics" to storageStats.lyricsBytes.bytesLabel(),
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "Provider features",
                rows = provider?.capabilities?.let { capabilities ->
                    listOf(
                        "Streaming transcode" to capabilities.supportsStreamingTranscode.toString(),
                        "Download transcode" to capabilities.supportsDownloadTranscode.toString(),
                        "Artist radio" to capabilities.supportsArtistRadio.toString(),
                        "Album radio" to capabilities.supportsAlbumRadio.toString(),
                        "Track radio" to capabilities.supportsTrackRadio.toString(),
                        "Track favorites" to capabilities.supportsTrackFavorites.toString(),
                        "Artist favorites" to capabilities.supportsArtistFavorites.toString(),
                        "Album favorites" to capabilities.supportsAlbumFavorites.toString(),
                        "Track ratings" to capabilities.supportsTrackRatings.toString(),
                        "Play reporting" to capabilities.supportsPlayReporting.toString(),
                    )
                }.orEmpty(),
            ),
        ),
    )

fun androidApiCallRows(): List<Pair<String, String>> =
    buildList {
        NavidromeApiCallHistory.recent(8).forEach { call ->
            add("Navidrome ${call.method} ${call.endpoint}" to call.summary())
        }
        AndroidPopularTracksApiCallHistory.recent(8).forEach { call ->
            add("Deezer ${call.endpoint}" to call.summary())
        }
        AndroidLrclibApiCallHistory.recent(8).forEach { call ->
            add("LRCLIB ${call.endpoint}" to call.summary())
        }
    }.ifEmpty {
        listOf("Recent calls" to "None yet")
    }

private fun NavidromeApiCall.summary(): String =
    buildApiCallSummary(success = success, durationMillis = durationMillis, errorMessage = errorMessage, url = sanitizedUrl)

private fun AndroidPopularTracksApiCall.summary(): String =
    buildApiCallSummary(success = success, durationMillis = durationMillis, errorMessage = errorMessage, url = sanitizedUrl)

private fun AndroidLrclibApiCall.summary(): String =
    buildApiCallSummary(success = success, durationMillis = durationMillis, errorMessage = errorMessage, url = sanitizedUrl)

private fun buildApiCallSummary(
    success: Boolean,
    durationMillis: Long,
    errorMessage: String?,
    url: String,
): String =
    "${if (success) "OK" else "ERR"} ${durationMillis} ms" +
        errorMessage?.let { " - $it" }.orEmpty() +
        "\n$url"

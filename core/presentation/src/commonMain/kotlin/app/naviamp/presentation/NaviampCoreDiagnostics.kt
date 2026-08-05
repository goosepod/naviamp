package app.naviamp.presentation

import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.bytesLabel
import app.naviamp.ui.label
import kotlin.math.roundToInt

data class NaviampCoreDiagnosticsSnapshot(
    val platformRows: List<Pair<String, String>> = emptyList(),
    val storage: StorageCacheStats? = null,
)

fun interface NaviampCoreDiagnosticsPort {
    fun snapshot(): NaviampCoreDiagnosticsSnapshot
}

fun emptyNaviampCoreDiagnosticsPort() = NaviampCoreDiagnosticsPort { NaviampCoreDiagnosticsSnapshot() }

/** Shared refresh and failure policy around narrow host diagnostic facts. */
class NaviampCoreCachedDiagnosticsPort(
    private val platformRows: () -> List<Pair<String, String>>,
    private val storageStats: () -> StorageCacheStats,
    private val nowEpochMillis: () -> Long,
    private val refreshIntervalMillis: Long = 2_000L,
) : NaviampCoreDiagnosticsPort {
    private var cachedAt = Long.MIN_VALUE
    private var cachedStorage: StorageCacheStats? = null
    private var attempted = false

    override fun snapshot(): NaviampCoreDiagnosticsSnapshot {
        val now = nowEpochMillis()
        if (!attempted || now - cachedAt >= refreshIntervalMillis) {
            cachedStorage = runCatching(storageStats).getOrNull()
            cachedAt = now
            attempted = true
        }
        return NaviampCoreDiagnosticsSnapshot(platformRows = platformRows(), storage = cachedStorage)
    }
}

internal fun naviampCoreDiagnostics(
    shell: NaviampAppShellUiState,
    provider: MediaProvider?,
    sidecars: NaviampCoreNowPlayingSidecars,
    playbackEngineRows: List<Pair<String, String>>,
    external: NaviampCoreDiagnosticsSnapshot,
): NaviampDiagnosticsUi {
    val connection = shell.connectionSettings.connection
    val currentConnection = connection.savedConnections.firstOrNull { it.current }
    val nowPlaying = shell.nowPlaying
    val settings = shell.playback.settings
    val streamMetadata = sidecars.streamMetadata
    val storage = external.storage
    val sections = mutableListOf<NaviampDiagnosticsSectionUi>()

    sections += NaviampDiagnosticsSectionUi(
        "Application",
        listOf(
            "Version" to shell.general.about.version,
            "Build" to shell.general.about.buildNumber,
            "Route" to shell.shellChrome.selectedRoute.label,
            "Now Playing open" to shell.shellChrome.nowPlayingOpen.toString(),
        ) + external.platformRows,
    )
    sections += NaviampDiagnosticsSectionUi(
        "Connection",
        listOf(
            "Server" to (currentConnection?.serverUrl ?: connection.form.serverUrl).ifBlank { "Not set" },
            "Username" to (currentConnection?.username ?: connection.form.username).ifBlank { "Not set" },
            "Provider" to (provider?.displayName ?: "Not connected"),
            "Provider cache namespace" to (provider?.cacheNamespace ?: "Not connected"),
            "Source ID" to (shell.connectionSettings.currentSourceId ?: "None"),
            "Connected" to connection.connected.toString(),
            "Status" to (connection.status ?: "None"),
            "Server version" to (connection.serverVersion ?: "Unknown"),
            "Saved connections" to connection.savedConnections.size.toString(),
            "Selected libraries" to (currentConnection?.selectedLibrarySummary?.ifBlank { "All" } ?: "Unknown"),
        ),
    )
    sections += NaviampDiagnosticsSectionUi(
        "Library",
        listOf(
            "Syncing" to shell.library.syncStatus.isSyncing.toString(),
            "Status" to (shell.library.syncStatus.message ?: "Idle"),
            "Search query" to shell.library.query.ifBlank { "None" },
            "Loaded artists" to shell.library.artists.size.toString(),
            "Indexed artists" to (storage?.libraryArtistCount?.toString() ?: "Unknown"),
            "Indexed albums" to (storage?.libraryAlbumCount?.toString() ?: "Unknown"),
            "Indexed tracks" to (storage?.libraryTrackCount?.toString() ?: "Unknown"),
        ),
    )
    sections += NaviampDiagnosticsSectionUi(
        "Playback",
        listOf(
            "State" to (nowPlaying?.stateLabel ?: "Idle"),
            "Track ID" to (nowPlaying?.id ?: "None"),
            "Title" to (nowPlaying?.title ?: "None"),
            "Artist" to (nowPlaying?.subtitle ?: "None"),
            "Album" to (nowPlaying?.albumLine?.ifBlank { "Unknown" } ?: "None"),
            "Audio" to (nowPlaying?.audioInfo?.ifBlank { "Unknown" } ?: "None"),
            "Position" to nowPlaying?.positionSeconds.secondsLabel(),
            "Duration" to nowPlaying?.durationSeconds.secondsLabel(),
            "Queue" to "${(nowPlaying?.backTo?.size ?: 0) + (nowPlaying?.upNext?.size ?: 0) + if (nowPlaying != null) 1 else 0} tracks",
            "Shuffle" to (nowPlaying?.shuffleActive == true).toString(),
            "Repeat" to (nowPlaying?.repeatMode?.name ?: "Off"),
            "Volume" to "${settings.volumePercent}%",
            "Stream quality" to settings.streamQualityForNetwork(isMobileData = false).label(),
            "ReplayGain" to settings.replayGainMode.displayName,
            "Gapless" to settings.gaplessEnabled.toString(),
            "Crossfade" to if (settings.crossfadeDurationSeconds > 0) "${settings.crossfadeDurationSeconds}s" else "Off",
        ),
    )
    sections += NaviampDiagnosticsSectionUi(
        "Track sidecars",
        listOf(
            "Waveform" to (sidecars.waveform?.amplitudes?.let { "${it.size} buckets" } ?: "Unavailable"),
            "Embedded tags" to (sidecars.audioTags?.size?.toString() ?: "Not loaded"),
            "Lyrics" to (sidecars.lyrics?.let { "${it.source.name}, ${it.lines.size} lines" } ?: sidecars.lyricsStatus ?: "Not loaded"),
            "Lyrics synced" to (sidecars.lyrics?.synced?.toString() ?: "Unknown"),
            "Lyrics karaoke cues" to (sidecars.lyrics?.cueLines?.sumOf { it.cues.size }?.toString() ?: "Unknown"),
            "Lyrics offset" to (sidecars.lyrics?.offsetMillis?.let { "${it}ms" } ?: "None"),
            "Visualizer" to (sidecars.visualizerFrame?.bands?.let { "${it.size} FFT bands" } ?: "Waiting"),
            "Selected visualizer" to shell.shellChrome.selectedVisualizer.name,
            "Related tracks" to "${sidecars.relatedTracks.size} (${sidecars.relatedTracksSource.name})",
            "Stream metadata title" to (streamMetadata.title ?: "None"),
            "Stream metadata" to streamMetadata.properties.entries
                .sortedBy { it.key.lowercase() }
                .joinToString(", ") { (key, value) -> "$key=$value" }
                .ifBlank { "None" },
        ),
    )
    if (playbackEngineRows.isNotEmpty()) {
        sections += NaviampDiagnosticsSectionUi("Playback engine", playbackEngineRows)
    }
    provider?.capabilities?.let { capabilities ->
        sections += NaviampDiagnosticsSectionUi("Provider features", capabilities.diagnosticRows())
    }
    if (storage != null) {
        sections += NaviampDiagnosticsSectionUi(
            "Database",
            listOf(
                "Database" to storage.databaseLabel,
                "File size" to storage.databaseBytes.bytesLabel(),
                "Saved sources" to storage.mediaSourceCount.toString(),
                "Playback sessions" to storage.playbackSessionCount.toString(),
                "Pending provider actions" to storage.pendingProviderActionCount.toString(),
                "Failed provider actions" to storage.failedPendingProviderActionCount.toString(),
            ),
        )
        sections += NaviampDiagnosticsSectionUi(
            "Storage",
            listOf(
                "Images" to "${storage.imageCount} (${storage.imageBytes.bytesLabel()})",
                "Provider responses" to storage.responseCount.toString(),
                "Audio cache" to "${storage.audioCount} (${storage.audioBytes.bytesLabel()})",
                "Downloads" to "${storage.downloadCount} (${storage.downloadBytes.bytesLabel()})",
                "Waveforms" to "${storage.audioWaveformCount} (${storage.audioWaveformBytes.bytesLabel()})",
                "Lyrics" to "${storage.lyricsCount} (${storage.lyricsBytes.bytesLabel()})",
                "Hot images" to "${storage.hotImageCount} (${storage.hotImageBytes.bytesLabel()})",
                "Image budget" to storage.maxImageBytes.bytesLabel(),
                "Audio budget" to storage.maxAudioBytes.bytesLabel(),
                "Waveform budget" to storage.maxAudioWaveformBytes.bytesLabel(),
                "Hot image budget" to storage.maxHotImageBytes.bytesLabel(),
                "Audio cache directory" to storage.audioCacheDirectory.ifBlank { "Unknown" },
                "Download directory" to storage.downloadDirectory.ifBlank { "Unknown" },
            ),
        )
    }
    sections += shell.cache.diagnostics.sections
    val apiCalls = provider?.recentApiCalls(50).orEmpty()
    if (apiCalls.isNotEmpty()) {
        sections += NaviampDiagnosticsSectionUi(
            "Recent API calls",
            apiCalls.take(50).flatMapIndexed { index, call ->
                listOf(
                    "${index + 1}. ${call.source}" to "${if (call.success) "OK" else "ERROR"} · ${call.durationMillis}ms · ${call.endpoint}",
                    "URL" to call.sanitizedUrl,
                ) + call.errorMessage?.let { listOf("Error" to it) }.orEmpty()
            },
        )
    }
    return NaviampDiagnosticsUi(sections.distinctBy { it.title })
}

private fun Double?.secondsLabel(): String = this
    ?.let { seconds -> "${(seconds * 10.0).roundToInt() / 10.0}s" }
    ?: "Unknown"

private fun ProviderCapabilities.diagnosticRows(): List<Pair<String, String>> = listOf(
    "Streaming transcode" to supportsStreamingTranscode.toString(),
    "Download transcode" to supportsDownloadTranscode.toString(),
    "Artist radio" to supportsArtistRadio.toString(),
    "Album radio" to supportsAlbumRadio.toString(),
    "Track radio" to supportsTrackRadio.toString(),
    "Track favorites" to supportsTrackFavorites.toString(),
    "Artist favorites" to supportsArtistFavorites.toString(),
    "Album favorites" to supportsAlbumFavorites.toString(),
    "Track ratings" to supportsTrackRatings.toString(),
    "Play reporting" to supportsPlayReporting.toString(),
    "Smart playlists" to supportsSmartPlaylists.toString(),
    "Sonic similarity" to supportsSonicSimilarity.toString(),
)

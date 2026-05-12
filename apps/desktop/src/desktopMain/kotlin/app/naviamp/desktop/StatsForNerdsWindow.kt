package app.naviamp.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import app.naviamp.desktop.playback.AudioPrefetchStats
import app.naviamp.desktop.playback.CacheRuntimeStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun StatsForNerdsWindow(
    appColors: AppColors,
    info: StatsForNerdsInfo,
    onClose: () -> Unit,
) {
    val windowState = rememberWindowState(size = DpSize(720.dp, 760.dp))

    Window(
        state = windowState,
        title = "Naviamp - Stats for nerds",
        onCloseRequest = onClose,
    ) {
        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = appColors.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Stats for nerds",
                            color = appColors.primaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = onClose) {
                            Text("Close")
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatsSection(
                            appColors = appColors,
                            title = "App",
                            rows = listOf(
                                "Route" to info.route,
                                "OS" to info.os,
                                "Java" to info.javaVersion,
                                "Working dir" to info.workingDirectory,
                            ),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Connection",
                            rows = listOf(
                                "Server" to info.serverUrl.ifBlank { "Not set" },
                                "Username" to info.username.ifBlank { "Not set" },
                                "Provider" to info.providerName,
                                "Provider cache namespace" to info.providerCacheNamespace,
                                "Status" to (info.connectionStatus ?: "None"),
                            ),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Media Source",
                            rows = info.mediaSource?.rows() ?: listOf("Source" to "None saved"),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Library Import",
                            rows = info.librarySync.rows(info.cacheStats),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Playback",
                            rows = listOf(
                                "Engine" to info.playbackEngineName,
                                "Queue" to "${info.queueSize} tracks",
                                "Current index" to info.currentQueueIndex.toString(),
                                "Capabilities" to info.playbackCapabilities,
                                "Playback source" to info.cacheRuntime.playbackSource.label,
                            ),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Audio Prefetch",
                            rows = info.cacheRuntime.prefetch.rows(),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Stream",
                            rows = info.stream?.rows() ?: listOf("Now playing" to "Nothing"),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Database",
                            rows = listOf(
                                "Database" to info.cacheStats.databasePath,
                                "File size" to info.cacheStats.databaseBytes.bytesLabel(),
                                "Saved sources" to info.cacheStats.mediaSourceCount.toString(),
                                "Library index" to "${info.cacheStats.libraryArtistCount} artists, " +
                                    "${info.cacheStats.libraryAlbumCount} albums, " +
                                    "${info.cacheStats.libraryTrackCount} tracks",
                            ),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Cache",
                            rows = listOf(
                                "Images" to "${info.cacheStats.imageCount} (${info.cacheStats.imageBytes.bytesLabel()})",
                                "Audio files" to "${info.cacheStats.audioCount} (${info.cacheStats.audioBytes.bytesLabel()})",
                                "Waveforms" to "${info.cacheStats.audioWaveformCount} (${info.cacheStats.audioWaveformBytes.bytesLabel()})",
                                "Lyrics" to "${info.cacheStats.lyricsCount} (${info.cacheStats.lyricsBytes.bytesLabel()})",
                                "Provider responses" to info.cacheStats.responseCount.toString(),
                                "Hot images" to "${info.cacheStats.hotImageCount} (${info.cacheStats.hotImageBytes.bytesLabel()})",
                                "Image budget" to info.cacheStats.maxImageBytes.bytesLabel(),
                                "Audio budget" to info.cacheStats.maxAudioBytes.bytesLabel(),
                                "Waveform budget" to info.cacheStats.maxAudioWaveformBytes.bytesLabel(),
                                "Hot image budget" to info.cacheStats.maxHotImageBytes.bytesLabel(),
                            ),
                        )
                        StatsSection(
                            appColors = appColors,
                            title = "Provider Features",
                            rows = info.providerCapabilities.mapValues { it.value.toString() }.toList(),
                        )
                        ApiHistorySection(
                            appColors = appColors,
                            calls = info.apiCalls,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSection(
    appColors: AppColors,
    title: String,
    rows: List<Pair<String, String>>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.albumArtPlaceholder.copy(alpha = 0.16f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        rows.forEach { (label, value) ->
            Text(
                "$label: $value",
                color = appColors.secondaryText,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ApiHistorySection(
    appColors: AppColors,
    calls: List<ApiCallStats>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.albumArtPlaceholder.copy(alpha = 0.16f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("Navidrome API Calls", color = appColors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        if (calls.isEmpty()) {
            Text("No calls recorded yet.", color = appColors.secondaryText, fontSize = 11.sp)
            return@Column
        }

        calls.take(50).forEachIndexed { index, call ->
            if (index > 0) {
                HorizontalDivider(color = appColors.border.copy(alpha = 0.45f), thickness = 0.5.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    call.statusLabel,
                    color = if (call.success) appColors.secondaryText else MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.width(46.dp),
                )
                Text(
                    "${call.durationMillis} ms",
                    color = appColors.secondaryText,
                    fontSize = 11.sp,
                    modifier = Modifier.width(68.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        call.endpoint,
                        color = appColors.primaryText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        call.sanitizedUrl,
                        color = appColors.mutedText,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    call.errorMessage?.let { error ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

data class StatsForNerdsInfo(
    val route: String,
    val os: String,
    val javaVersion: String,
    val workingDirectory: String,
    val serverUrl: String,
    val username: String,
    val providerName: String,
    val providerCacheNamespace: String,
    val mediaSource: MediaSourceStats?,
    val connectionStatus: String?,
    val librarySync: LibrarySyncStats,
    val playbackEngineName: String,
    val playbackCapabilities: String,
    val queueSize: Int,
    val currentQueueIndex: Int,
    val cacheRuntime: CacheRuntimeStats,
    val stream: StreamStats?,
    val cacheStats: CacheStats,
    val providerCapabilities: Map<String, Boolean>,
    val apiCalls: List<ApiCallStats>,
)

data class MediaSourceStats(
    val id: String,
    val providerId: String,
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val cacheNamespace: String,
    val createdAtEpochMillis: Long,
    val lastConnectedAtEpochMillis: Long?,
    val lastSyncStartedAtEpochMillis: Long?,
    val lastSyncCompletedAtEpochMillis: Long?,
) {
    fun rows(): List<Pair<String, String>> =
        listOf(
            "ID" to id,
            "Provider ID" to providerId,
            "Display name" to displayName,
            "Base URL" to baseUrl,
            "Username" to username,
            "Cache namespace" to cacheNamespace,
            "Created" to createdAtEpochMillis.dateTimeLabel(),
            "Last connected" to lastConnectedAtEpochMillis.dateTimeLabel(),
            "Last sync started" to lastSyncStartedAtEpochMillis.dateTimeLabel(),
            "Last sync completed" to lastSyncCompletedAtEpochMillis.dateTimeLabel(),
        )
}

data class LibrarySyncStats(
    val isSyncing: Boolean,
    val status: String,
    val selectedTab: String,
    val query: String,
    val visibleArtists: Int,
    val visibleAlbums: Int,
    val visibleTracks: Int,
) {
    fun rows(cacheStats: CacheStats): List<Pair<String, String>> =
        listOf(
            "Running" to isSyncing.toString(),
            "Status" to status,
            "Selected tab" to selectedTab,
            "Search query" to query.ifBlank { "None" },
            "Visible results" to "$visibleArtists artists, $visibleAlbums albums, $visibleTracks tracks",
            "Indexed artists" to cacheStats.libraryArtistCount.toString(),
            "Indexed albums" to cacheStats.libraryAlbumCount.toString(),
            "Indexed tracks" to cacheStats.libraryTrackCount.toString(),
        )
}

data class StreamStats(
    val state: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val progress: String,
    val streamQuality: String,
    val replayGainMode: String,
    val codec: String,
    val bitrate: String,
    val contentType: String,
    val coverArtId: String,
    val waveformStatus: String,
    val waveformBuckets: String,
    val audioCacheStatus: String,
    val audioCacheSize: String,
    val audioCachePath: String,
) {
    fun rows(): List<Pair<String, String>> =
        listOf(
            "State" to state,
            "Track ID" to trackId,
            "Title" to title,
            "Artist" to artist,
            "Album" to album,
            "Duration" to duration,
            "Progress" to progress,
            "Stream quality" to streamQuality,
            "ReplayGain" to replayGainMode,
            "Codec" to codec,
            "Bitrate" to bitrate,
            "Content type" to contentType,
            "Cover art ID" to coverArtId,
            "Waveform status" to waveformStatus,
            "Waveform buckets" to waveformBuckets,
            "Audio cache" to audioCacheStatus,
            "Audio cache size" to audioCacheSize,
            "Audio cache path" to audioCachePath,
        )
}

private fun AudioPrefetchStats.rows(): List<Pair<String, String>> =
    listOf(
        "Enabled" to enabled.toString(),
        "Configured depth" to configuredDepth.toString(),
        "State" to if (running) "Running" else "Idle",
        "Queued" to queued.toString(),
        "Completed" to completed.toString(),
        "Failed" to failed.toString(),
        "Last error" to (lastError ?: "None"),
    )

fun SavedMediaSource.toStats(): MediaSourceStats =
    MediaSourceStats(
        id = id,
        providerId = providerId,
        displayName = displayName,
        baseUrl = baseUrl,
        username = username,
        cacheNamespace = cacheNamespace,
        createdAtEpochMillis = createdAtEpochMillis,
        lastConnectedAtEpochMillis = lastConnectedAtEpochMillis,
        lastSyncStartedAtEpochMillis = lastSyncStartedAtEpochMillis,
        lastSyncCompletedAtEpochMillis = lastSyncCompletedAtEpochMillis,
    )

data class ApiCallStats(
    val endpoint: String,
    val sanitizedUrl: String,
    val durationMillis: Long,
    val success: Boolean,
    val errorMessage: String?,
) {
    val statusLabel: String
        get() = if (success) "OK" else "ERROR"
}

private fun Long.bytesLabel(): String {
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    return when {
        this >= gib -> "%.1f GB".format(this / gib)
        this >= mib -> "%.1f MB".format(this / mib)
        this >= kib -> "%.1f KB".format(this / kib)
        else -> "$this B"
    }
}

private fun Long?.dateTimeLabel(): String =
    this?.let {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(it))
    } ?: "Never"

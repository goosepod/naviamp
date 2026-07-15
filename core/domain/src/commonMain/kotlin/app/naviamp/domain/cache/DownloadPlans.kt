package app.naviamp.domain.cache

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class DownloadBlockReason {
    MissingConnection,
    EmptyTracks,
    MobileDataDisabled,
}

data class DownloadPlan(
    val tracks: List<Track>,
    val blockedReason: DownloadBlockReason?,
) {
    val isReady: Boolean
        get() = blockedReason == null
}

sealed interface DownloadTracksResult {
    data class Blocked(val reason: DownloadBlockReason) : DownloadTracksResult
    data class Completed(val completed: Int) : DownloadTracksResult
    data class Failed(val completed: Int, val error: Throwable) : DownloadTracksResult
}

data class DownloadExecutionResult<Stats>(
    val result: DownloadTracksResult,
    val refreshDownloads: Boolean,
    val stats: Stats?,
)

fun planDownloadTracks(
    tracks: List<Track>,
    hasProvider: Boolean,
    hasSource: Boolean,
    isActiveNetworkMobileData: Boolean = false,
    allowMobileDownloads: Boolean = true,
    deduplicateTracks: Boolean = false,
): DownloadPlan {
    if (!hasProvider || !hasSource) {
        return DownloadPlan(emptyList(), DownloadBlockReason.MissingConnection)
    }
    val plannedTracks = if (deduplicateTracks) tracks.distinctBy { it.id } else tracks
    if (plannedTracks.isEmpty()) {
        return DownloadPlan(emptyList(), DownloadBlockReason.EmptyTracks)
    }
    if (isActiveNetworkMobileData && !allowMobileDownloads) {
        return DownloadPlan(emptyList(), DownloadBlockReason.MobileDataDisabled)
    }
    return DownloadPlan(plannedTracks, blockedReason = null)
}

suspend fun downloadTracksWithStatus(
    label: String,
    tracks: List<Track>,
    hasProvider: Boolean,
    hasSource: Boolean,
    isActiveNetworkMobileData: Boolean = false,
    allowMobileDownloads: Boolean = true,
    deduplicateTracks: Boolean = true,
    includeCompletedCount: Boolean = true,
    setStatus: (String) -> Unit,
    onJobUpdate: (DownloadJobUpdate) -> Unit = {},
    downloadTrack: suspend (Track) -> Unit,
): DownloadTracksResult {
    val plan = planDownloadTracks(
        tracks = tracks,
        hasProvider = hasProvider,
        hasSource = hasSource,
        isActiveNetworkMobileData = isActiveNetworkMobileData,
        allowMobileDownloads = allowMobileDownloads,
        deduplicateTracks = deduplicateTracks,
    )
    plan.blockedReason?.let { reason ->
        setStatus(downloadBlockedStatus(reason, label))
        return DownloadTracksResult.Blocked(reason)
    }

    setStatus(downloadStartingStatus(label))
    onJobUpdate(DownloadJobUpdate.Started)
    var completed = 0
    return try {
        plan.tracks.forEachIndexed { index, track ->
            currentCoroutineContext().ensureActive()
            if (plan.tracks.size > 1) {
                setStatus(downloadProgressStatus(label, index, plan.tracks.size))
            }
            onJobUpdate(DownloadJobUpdate.TrackStarted(track.id.value))
            downloadTrack(track)
            completed += 1
            onJobUpdate(DownloadJobUpdate.TrackCompleted(track.id.value))
        }
        setStatus(downloadCompletedStatus(label, if (includeCompletedCount) completed else null))
        onJobUpdate(DownloadJobUpdate.Completed)
        DownloadTracksResult.Completed(completed)
    } catch (cancelled: CancellationException) {
        onJobUpdate(DownloadJobUpdate.Cancelled)
        throw cancelled
    } catch (error: Exception) {
        setStatus(downloadErrorStatus(label, error))
        onJobUpdate(DownloadJobUpdate.Failed(
            trackId = plan.tracks.getOrNull(completed)?.id?.value,
            message = error.message ?: error::class.simpleName ?: "Download failed",
        ))
        DownloadTracksResult.Failed(completed, error)
    }
}

suspend fun redownloadTracksWithStatus(
    tracks: List<Track>,
    hasProvider: Boolean,
    hasSource: Boolean,
    isActiveNetworkMobileData: Boolean = false,
    allowMobileDownloads: Boolean = true,
    setStatus: (String) -> Unit,
    onJobUpdate: (DownloadJobUpdate) -> Unit = {},
    replaceTrack: suspend (Track) -> Unit,
): DownloadTracksResult =
    downloadTracksWithStatus(
        label = "downloads",
        tracks = tracks,
        hasProvider = hasProvider,
        hasSource = hasSource,
        isActiveNetworkMobileData = isActiveNetworkMobileData,
        allowMobileDownloads = allowMobileDownloads,
        deduplicateTracks = true,
        includeCompletedCount = true,
        setStatus = setStatus,
        onJobUpdate = onJobUpdate,
        downloadTrack = replaceTrack,
    )

fun shouldRefreshDownloadsAfter(result: DownloadTracksResult): Boolean =
    result is DownloadTracksResult.Completed || result is DownloadTracksResult.Failed && result.completed > 0

class DownloadService<DownloadedFile, DownloadedTrack>(
    private val downloadRepository: DownloadRepository<DownloadedFile, DownloadedTrack>,
    private val replacementRepository: DownloadReplacementRepository<DownloadedFile>,
) {
    suspend fun downloadTracksWithStatus(
        sourceId: String?,
        provider: MediaProvider?,
        tracks: List<Track>,
        quality: StreamQuality,
        maxDownloadBytes: Long,
        label: String,
        isActiveNetworkMobileData: Boolean = false,
        allowMobileDownloads: Boolean = true,
        includeCompletedCount: Boolean = true,
        setStatus: (String) -> Unit,
        onJobUpdate: (DownloadJobUpdate) -> Unit = {},
    ): DownloadTracksResult {
        val activeSourceId = sourceId
        val activeProvider = provider
        return downloadTracksWithStatus(
            label = label,
            tracks = tracks,
            hasProvider = activeProvider != null,
            hasSource = activeSourceId != null,
            isActiveNetworkMobileData = isActiveNetworkMobileData,
            allowMobileDownloads = allowMobileDownloads,
            deduplicateTracks = true,
            includeCompletedCount = includeCompletedCount,
            setStatus = setStatus,
            onJobUpdate = onJobUpdate,
            downloadTrack = { track ->
                downloadRepository.downloadAudioTrack(
                    sourceId = requireNotNull(activeSourceId),
                    provider = requireNotNull(activeProvider),
                    track = track,
                    quality = quality,
                    maxDownloadBytes = maxDownloadBytes,
                )
            },
        )
    }

    suspend fun redownloadTracksWithStatus(
        sourceId: String?,
        provider: MediaProvider?,
        tracks: List<Track>,
        quality: StreamQuality,
        maxDownloadBytes: Long,
        isActiveNetworkMobileData: Boolean = false,
        allowMobileDownloads: Boolean = true,
        setStatus: (String) -> Unit,
        onJobUpdate: (DownloadJobUpdate) -> Unit = {},
    ): DownloadTracksResult {
        val activeSourceId = sourceId
        val activeProvider = provider
        return redownloadTracksWithStatus(
            tracks = tracks,
            hasProvider = activeProvider != null,
            hasSource = activeSourceId != null,
            isActiveNetworkMobileData = isActiveNetworkMobileData,
            allowMobileDownloads = allowMobileDownloads,
            setStatus = setStatus,
            onJobUpdate = onJobUpdate,
            replaceTrack = { track ->
                replacementRepository.replaceDownloadedAudioTrack(
                    sourceId = requireNotNull(activeSourceId),
                    provider = requireNotNull(activeProvider),
                    track = track,
                    quality = quality,
                    maxDownloadBytes = maxDownloadBytes,
                )
            },
        )
    }
}

suspend fun <DownloadedFile, DownloadedTrack, Stats> DownloadService<DownloadedFile, DownloadedTrack>.downloadTracksWithRefresh(
    sourceId: String?,
    provider: MediaProvider?,
    tracks: List<Track>,
    quality: StreamQuality,
    maxDownloadBytes: Long,
    label: String,
    isActiveNetworkMobileData: Boolean = false,
    allowMobileDownloads: Boolean = true,
    includeCompletedCount: Boolean = true,
    setStatus: (String) -> Unit,
    onJobUpdate: (DownloadJobUpdate) -> Unit = {},
    shouldRefreshDownloads: (DownloadTracksResult) -> Boolean = ::shouldRefreshDownloadsAfter,
    loadStats: (suspend () -> Stats)? = null,
): DownloadExecutionResult<Stats> {
    val result = downloadTracksWithStatus(
        sourceId = sourceId,
        provider = provider,
        tracks = tracks,
        quality = quality,
        maxDownloadBytes = maxDownloadBytes,
        label = label,
        isActiveNetworkMobileData = isActiveNetworkMobileData,
        allowMobileDownloads = allowMobileDownloads,
        includeCompletedCount = includeCompletedCount,
        setStatus = setStatus,
        onJobUpdate = onJobUpdate,
    )
    val refreshDownloads = shouldRefreshDownloads(result)
    return DownloadExecutionResult(
        result = result,
        refreshDownloads = refreshDownloads,
        stats = if (refreshDownloads) loadStats?.invoke() else null,
    )
}

suspend fun <DownloadedFile, DownloadedTrack, Stats> DownloadService<DownloadedFile, DownloadedTrack>.redownloadTracksWithRefresh(
    sourceId: String?,
    provider: MediaProvider?,
    tracks: List<Track>,
    quality: StreamQuality,
    maxDownloadBytes: Long,
    isActiveNetworkMobileData: Boolean = false,
    allowMobileDownloads: Boolean = true,
    setStatus: (String) -> Unit,
    onJobUpdate: (DownloadJobUpdate) -> Unit = {},
    loadStats: (suspend () -> Stats)? = null,
): DownloadExecutionResult<Stats> {
    val result = redownloadTracksWithStatus(
        sourceId = sourceId,
        provider = provider,
        tracks = tracks,
        quality = quality,
        maxDownloadBytes = maxDownloadBytes,
        isActiveNetworkMobileData = isActiveNetworkMobileData,
        allowMobileDownloads = allowMobileDownloads,
        setStatus = setStatus,
        onJobUpdate = onJobUpdate,
    )
    val refreshDownloads = shouldRefreshDownloadsAfter(result)
    return DownloadExecutionResult(
        result = result,
        refreshDownloads = refreshDownloads,
        stats = if (refreshDownloads) loadStats?.invoke() else null,
    )
}

fun downloadBlockedStatus(reason: DownloadBlockReason, label: String): String =
    when (reason) {
        DownloadBlockReason.MissingConnection -> downloadConnectionRequiredStatus()
        DownloadBlockReason.EmptyTracks -> emptyDownloadStatus(label)
        DownloadBlockReason.MobileDataDisabled -> downloadMobileDataDisabledStatus()
    }

fun downloadConnectionRequiredStatus(): String =
    "Connect to Navidrome before downloading."

fun emptyDownloadStatus(label: String): String =
    "$label did not return any tracks."

fun downloadMobileDataDisabledStatus(): String =
    "Downloads over mobile data are disabled."

fun downloadStartingStatus(label: String): String =
    "Downloading $label..."

fun downloadProgressStatus(label: String, index: Int, total: Int): String =
    "Downloading $label (${index + 1}/$total)..."

fun downloadCompletedStatus(label: String, completed: Int? = null): String =
    if (completed == null) {
        "Downloaded $label."
    } else {
        "Downloaded $label ($completed tracks)."
    }

fun downloadErrorStatus(label: String, error: Throwable): String =
    error.message ?: "Could not download $label."

fun downloadedTrackRemovedStatus(trackTitle: String): String =
    "Removed $trackTitle."

fun downloadRemoveErrorStatus(error: Throwable): String =
    error.message ?: "Could not remove download."

fun <T> downloadTracksForPlayback(downloads: List<T>, index: Int, track: (T) -> Track): List<Track>? =
    if (downloads.isEmpty() || index !in downloads.indices) {
        null
    } else {
        downloads.map(track)
    }

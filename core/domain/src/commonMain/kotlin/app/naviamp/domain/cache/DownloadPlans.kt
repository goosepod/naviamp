package app.naviamp.domain.cache

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider

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
    var completed = 0
    return try {
        plan.tracks.forEachIndexed { index, track ->
            if (plan.tracks.size > 1) {
                setStatus(downloadProgressStatus(label, index, plan.tracks.size))
            }
            downloadTrack(track)
            completed += 1
        }
        setStatus(downloadCompletedStatus(label, if (includeCompletedCount) completed else null))
        DownloadTracksResult.Completed(completed)
    } catch (error: Exception) {
        setStatus(downloadErrorStatus(label, error))
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

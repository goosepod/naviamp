package app.naviamp.domain.cache

import app.naviamp.domain.Track

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

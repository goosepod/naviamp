package app.naviamp.desktop

import app.naviamp.domain.Track

fun downloadConnectionRequiredStatus(): String =
    "Connect to Navidrome before downloading."

fun emptyDownloadStatus(label: String): String =
    "$label did not return any tracks."

fun downloadStartingStatus(label: String): String =
    "Downloading $label..."

fun downloadProgressStatus(label: String, index: Int, total: Int): String =
    "Downloading $label (${index + 1}/$total)..."

fun downloadCompletedStatus(label: String, completed: Int): String =
    "Downloaded $label ($completed tracks)."

fun downloadErrorStatus(label: String, error: Throwable): String =
    error.message ?: "Could not download $label."

fun downloadedTrackRemovedStatus(download: DownloadedTrack): String =
    "Removed ${download.track.title}."

fun downloadTracksForPlayback(downloads: List<DownloadedTrack>, index: Int): List<Track>? =
    if (downloads.isEmpty() || index !in downloads.indices) {
        null
    } else {
        downloads.map { it.track }
    }

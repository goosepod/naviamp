package app.naviamp.domain.cache

import app.naviamp.domain.Track
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality

enum class DownloadJobStatus {
    Queued,
    Running,
    Completed,
    Failed,
    Cancelled,
}

fun StreamQuality.downloadContentType(originalContentType: String?): String? =
    when (this) {
        StreamQuality.Original -> originalContentType
        is StreamQuality.Transcoded -> when (codec) {
            AudioCodec.Opus -> "audio/ogg"
            AudioCodec.Mp3 -> "audio/mpeg"
            AudioCodec.Aac -> "audio/aac"
        }
    }

fun downloadedAudioQualityLabel(
    qualityKey: String,
    audioInfo: AudioInfo?,
    contentType: String?,
): String {
    val transcodedParts = qualityKey.split(':')
    if (transcodedParts.size >= 3 && transcodedParts.first() == "transcoded") {
        val codec = transcodedParts[1].uppercase()
        val bitrate = transcodedParts[2].toIntOrNull()
        return listOfNotNull(codec, bitrate?.let { "$it kbps" }).joinToString(" · ")
    }
    val codec = audioInfo?.codec?.uppercase()
        ?: contentType.downloadCodecLabel()
        ?: "ORIGINAL"
    val detail = when {
        audioInfo?.bitDepth != null && audioInfo.samplingRateHz != null ->
            "${audioInfo.bitDepth}-bit / ${audioInfo.samplingRateHz.sampleRateLabel()}"
        audioInfo?.bitrateKbps != null -> "${audioInfo.bitrateKbps} kbps"
        else -> null
    }
    return listOfNotNull(codec, detail).joinToString(" · ")
}

private fun String?.downloadCodecLabel(): String? =
    when (this?.substringBefore(';')?.lowercase()?.trim()) {
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/ogg", "audio/opus" -> "OPUS"
        "audio/aac", "audio/mp4", "audio/x-m4a" -> "AAC"
        "audio/wav", "audio/x-wav" -> "WAV"
        else -> null
    }

private fun Int.sampleRateLabel(): String =
    if (this % 1000 == 0) "${this / 1000} kHz" else "${this / 1000.0} kHz"

enum class DownloadJobItemStatus {
    Pending,
    Downloading,
    Completed,
    Failed,
    Cancelled,
}

data class DownloadJobItem(
    val track: Track,
    val status: DownloadJobItemStatus = DownloadJobItemStatus.Pending,
    val failureMessage: String? = null,
)

data class DownloadJob(
    val id: String,
    val label: String,
    val items: List<DownloadJobItem>,
    val status: DownloadJobStatus = DownloadJobStatus.Queued,
) {
    val completedCount: Int
        get() = items.count { it.status == DownloadJobItemStatus.Completed }

    val totalCount: Int
        get() = items.size

    val progress: Float
        get() = if (items.isEmpty()) 0f else completedCount.toFloat() / items.size

    val canCancel: Boolean
        get() = status == DownloadJobStatus.Queued || status == DownloadJobStatus.Running

    val canRetry: Boolean
        get() = status == DownloadJobStatus.Failed || status == DownloadJobStatus.Cancelled

    val retryTracks: List<Track>
        get() = if (canRetry) {
            items.filter { it.status != DownloadJobItemStatus.Completed }.map { it.track }
        } else {
            emptyList()
        }
}

const val MaximumRecentDownloadJobs = 8

fun List<DownloadJob>.withDownloadJob(job: DownloadJob): List<DownloadJob> =
    if (job.status == DownloadJobStatus.Completed) {
        filterNot { it.id == job.id }
    } else {
        (filterNot { it.id == job.id } + job)
            .sortedByDescending { it.id }
            .let { updated ->
                updated.filter { it.canCancel } +
                    updated.filterNot { it.canCancel }.take(MaximumRecentDownloadJobs)
            }
    }

fun DownloadJob.retryJob(newId: String): DownloadJob =
    createDownloadJob(
        id = newId,
        label = label,
        tracks = retryTracks,
    )

sealed interface DownloadJobUpdate {
    data object Started : DownloadJobUpdate
    data class TrackStarted(val trackId: String) : DownloadJobUpdate
    data class TrackCompleted(val trackId: String) : DownloadJobUpdate
    data class Failed(val trackId: String?, val message: String) : DownloadJobUpdate
    data object Completed : DownloadJobUpdate
    data object Cancelled : DownloadJobUpdate
}

fun createDownloadJob(
    id: String,
    label: String,
    tracks: List<Track>,
): DownloadJob =
    DownloadJob(
        id = id,
        label = label,
        items = tracks.distinctBy { it.id }.map(::DownloadJobItem),
    )

fun DownloadJob.updated(update: DownloadJobUpdate): DownloadJob =
    when (update) {
        DownloadJobUpdate.Started -> copy(status = DownloadJobStatus.Running)
        is DownloadJobUpdate.TrackStarted -> copy(
            status = DownloadJobStatus.Running,
            items = items.updateTrack(update.trackId) {
                it.copy(status = DownloadJobItemStatus.Downloading, failureMessage = null)
            },
        )
        is DownloadJobUpdate.TrackCompleted -> copy(
            items = items.updateTrack(update.trackId) {
                it.copy(status = DownloadJobItemStatus.Completed, failureMessage = null)
            },
        )
        is DownloadJobUpdate.Failed -> copy(
            status = DownloadJobStatus.Failed,
            items = update.trackId?.let { trackId ->
                items.updateTrack(trackId) {
                    it.copy(status = DownloadJobItemStatus.Failed, failureMessage = update.message)
                }
            } ?: items,
        )
        DownloadJobUpdate.Completed -> copy(
            status = DownloadJobStatus.Completed,
            items = items.map {
                it.copy(status = DownloadJobItemStatus.Completed, failureMessage = null)
            },
        )
        DownloadJobUpdate.Cancelled -> copy(
            status = DownloadJobStatus.Cancelled,
            items = items.map {
                if (it.status == DownloadJobItemStatus.Completed) {
                    it
                } else {
                    it.copy(status = DownloadJobItemStatus.Cancelled, failureMessage = null)
                }
            },
        )
    }

private fun List<DownloadJobItem>.updateTrack(
    trackId: String,
    transform: (DownloadJobItem) -> DownloadJobItem,
): List<DownloadJobItem> =
    map { item -> if (item.track.id.value == trackId) transform(item) else item }

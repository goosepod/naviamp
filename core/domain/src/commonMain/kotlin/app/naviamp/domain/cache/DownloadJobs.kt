package app.naviamp.domain.cache

import app.naviamp.domain.Track

enum class DownloadJobStatus {
    Queued,
    Running,
    Completed,
    Failed,
    Cancelled,
}

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

package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.createDownloadJob
import app.naviamp.domain.cache.updated
import app.naviamp.domain.cache.withDownloadJob

data class NaviampDownloadRetry(
    val label: String,
    val tracks: List<Track>,
    val replaceExisting: Boolean,
)

/** Owns observable download-job state, cancellation handles, retry intent, and stable job IDs. */
class NaviampDownloadJobController(
    private val jobs: () -> List<DownloadJob>,
    private val setJobs: (List<DownloadJob>) -> Unit,
) {
    private val cancellations = mutableMapOf<String, () -> Unit>()
    private val replacementJobs = mutableSetOf<String>()
    private var nextJobId = 0L

    fun create(label: String, tracks: List<Track>, replaceExisting: Boolean): DownloadJob? {
        val job = createDownloadJob(newJobId(), label, tracks).takeIf { it.items.isNotEmpty() } ?: return null
        setJobs(jobs().withDownloadJob(job))
        if (replaceExisting) replacementJobs += job.id
        return job
    }

    fun registerCancellation(jobId: String, cancel: () -> Unit) {
        if (jobs().any { it.id == jobId && it.canCancel }) {
            cancellations[jobId] = cancel
        }
    }

    fun complete(jobId: String) {
        cancellations.remove(jobId)
    }

    fun update(jobId: String, update: DownloadJobUpdate) {
        val current = jobs().firstOrNull { it.id == jobId } ?: return
        setJobs(jobs().withDownloadJob(current.updated(update)))
    }

    fun cancel(jobId: String): Boolean {
        val completedAny = jobs().firstOrNull { it.id == jobId }?.completedCount?.let { it > 0 } == true
        cancellations.remove(jobId)?.invoke()
        update(jobId, DownloadJobUpdate.Cancelled)
        return completedAny
    }

    fun retry(jobId: String): NaviampDownloadRetry? {
        val job = jobs().firstOrNull { it.id == jobId && it.canRetry } ?: return null
        return NaviampDownloadRetry(
            label = job.label,
            tracks = job.retryTracks,
            replaceExisting = jobId in replacementJobs,
        )
    }

    private fun newJobId(): String {
        nextJobId += 1
        return "download-${nextJobId.toString().padStart(12, '0')}"
    }
}

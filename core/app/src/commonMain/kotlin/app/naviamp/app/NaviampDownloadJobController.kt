package app.naviamp.app

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.cache.DownloadExecutionResult
import app.naviamp.domain.cache.DownloadJob
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.DownloadTracksResult
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedReconciliationPlan
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.createDownloadJob
import app.naviamp.domain.cache.downloadTracksWithRefresh
import app.naviamp.domain.cache.planKeepDownloadedReconciliation
import app.naviamp.domain.cache.redownloadTracksWithRefresh
import app.naviamp.domain.cache.shouldRefreshDownloadsAfter
import app.naviamp.domain.cache.updated
import app.naviamp.domain.cache.withDownloadJob
import app.naviamp.domain.provider.MediaProvider

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

    val currentJobs: List<DownloadJob> get() = jobs()

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

data class NaviampDownloadExecutionRequest(
    val jobId: String,
    val label: String,
    val tracks: List<Track>,
    val sourceId: String?,
    val provider: MediaProvider?,
    val quality: StreamQuality,
    val maxDownloadBytes: Long,
    val replaceExisting: Boolean,
    val isActiveNetworkMobileData: Boolean = false,
    val allowMobileDownloads: Boolean = true,
    val includeCompletedCount: Boolean = true,
    val refreshDownloadsAfter: (DownloadTracksResult) -> Boolean = ::shouldRefreshDownloadsAfter,
)

/** Shared execution and keep-downloaded reconciliation around platform repositories. */
class NaviampDownloadCoordinator<DownloadedFile, DownloadedTrack, Stats>(
    downloadRepository: DownloadRepository<DownloadedFile, DownloadedTrack>,
    downloadReplacementRepository: DownloadReplacementRepository<DownloadedFile>,
    private val keepDownloadedRepository: KeepDownloadedRepository,
    private val jobs: NaviampDownloadJobController,
    private val downloadedTrackId: (DownloadedTrack) -> String,
    private val loadStats: suspend () -> Stats,
) {
    private val downloads = DownloadService(downloadRepository, downloadReplacementRepository)
    private val downloadRepository = downloadRepository

    suspend fun execute(
        request: NaviampDownloadExecutionRequest,
        setStatus: (String) -> Unit,
    ): DownloadExecutionResult<Stats> =
        if (request.replaceExisting) {
            downloads.redownloadTracksWithRefresh(
                tracks = request.tracks,
                sourceId = request.sourceId,
                provider = request.provider,
                quality = request.quality,
                maxDownloadBytes = request.maxDownloadBytes,
                isActiveNetworkMobileData = request.isActiveNetworkMobileData,
                allowMobileDownloads = request.allowMobileDownloads,
                setStatus = setStatus,
                onJobUpdate = { update -> jobs.update(request.jobId, update) },
                loadStats = loadStats,
            )
        } else {
            downloads.downloadTracksWithRefresh(
                label = request.label,
                tracks = request.tracks,
                sourceId = request.sourceId,
                provider = request.provider,
                quality = request.quality,
                maxDownloadBytes = request.maxDownloadBytes,
                isActiveNetworkMobileData = request.isActiveNetworkMobileData,
                allowMobileDownloads = request.allowMobileDownloads,
                includeCompletedCount = request.includeCompletedCount,
                setStatus = setStatus,
                onJobUpdate = { update -> jobs.update(request.jobId, update) },
                shouldRefreshDownloads = request.refreshDownloadsAfter,
                loadStats = loadStats,
            )
        }

    fun reconcile(
        policy: KeepDownloadedCollectionPolicy,
        tracks: List<Track>,
    ): KeepDownloadedReconciliationPlan {
        val downloadedIds = downloadRepository.downloadedTracks(policy.sourceId)
            .mapTo(mutableSetOf(), downloadedTrackId)
        val otherRequiredIds = keepDownloadedRepository.keepDownloadedPolicies(policy.sourceId)
            .filterNot { it.kind == policy.kind && it.collectionId == policy.collectionId }
            .flatMapTo(mutableSetOf()) { other ->
                keepDownloadedRepository.keepDownloadedTrackIds(
                    other.sourceId,
                    other.kind,
                    other.collectionId,
                )
            }
        val plan = planKeepDownloadedReconciliation(
            tracks = tracks,
            previousTrackIds = keepDownloadedRepository.keepDownloadedTrackIds(
                policy.sourceId,
                policy.kind,
                policy.collectionId,
            ),
            downloadedTrackIds = downloadedIds,
            managedTrackIds = keepDownloadedRepository.managedKeepDownloadedTrackIds(policy.sourceId),
            trackIdsRequiredByOtherPolicies = otherRequiredIds,
            removeUnneededFiles = policy.removeUnneededFiles,
        )
        keepDownloadedRepository.replaceKeepDownloadedTrackIds(policy, plan.nextTrackIds)
        keepDownloadedRepository.markManagedKeepDownloadedTracks(
            policy.sourceId,
            plan.tracksToDownload.mapTo(mutableSetOf()) { track -> track.id.value },
        )
        plan.trackIdsToRemove.forEach { trackId ->
            downloadRepository.removeDownloadedAudio(policy.sourceId, TrackId(trackId))
        }
        keepDownloadedRepository.unmarkManagedKeepDownloadedTracks(policy.sourceId, plan.trackIdsToRemove)
        return plan
    }
}

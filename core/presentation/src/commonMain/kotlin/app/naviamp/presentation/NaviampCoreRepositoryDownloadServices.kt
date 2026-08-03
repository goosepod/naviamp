package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.keepDownloadedReconciliationApplication
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.DownloadService
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.planKeepDownloadedReconciliation
import app.naviamp.domain.cache.shouldRefreshDownloadsAfter

/**
 * Builds Core's complete download feature from portable repository contracts.
 *
 * Hosts provide only native file availability. Download ordering, playback, job behavior, status,
 * retry, storage accounting, and keep-downloaded reconciliation remain in shared code.
 */
fun <DownloadedFile, StoredDownload> repositoryNaviampCoreDownloadServices(
    downloadRepository: DownloadRepository<DownloadedFile, StoredDownload>,
    replacementRepository: DownloadReplacementRepository<DownloadedFile>,
    keepDownloadedRepository: KeepDownloadedRepository,
    toCoreDownload: (StoredDownload) -> NaviampCoreDownloadedTrack,
    isStoredDownloadAvailable: (StoredDownload) -> Boolean,
    storageStats: () -> NaviampCoreDownloadStorageSnapshot = { NaviampCoreDownloadStorageSnapshot() },
    network: NaviampCoreMobileNetworkPort = NaviampCoreMobileNetworkPort { false },
): NaviampCoreDownloadServices {
    val downloadService = DownloadService(downloadRepository, replacementRepository)
    val storage = object : NaviampCoreDownloadStoragePort {
        override suspend fun snapshot(sourceId: String): NaviampCoreDownloadStorageSnapshot {
            val downloads = downloadRepository.downloadedTracks(sourceId).map(toCoreDownload)
            return storageStats().copy(downloads = downloads)
        }

        override suspend fun pruneMissing(sourceId: String): Int {
            val missing = downloadRepository.downloadedTracks(sourceId).filterNot(isStoredDownloadAvailable)
            missing.forEach { stored ->
                downloadRepository.removeDownloadedAudio(sourceId, toCoreDownload(stored).track.id)
            }
            return missing.size
        }

        override suspend fun remove(sourceId: String, track: Track) {
            downloadRepository.removeDownloadedAudio(sourceId, track.id)
        }

        override suspend fun deleteAll(sourceId: String): Int {
            val downloads = downloadRepository.downloadedTracks(sourceId)
            downloads.forEach { stored ->
                downloadRepository.removeDownloadedAudio(sourceId, toCoreDownload(stored).track.id)
            }
            return downloads.size
        }
    }
    val transfer = NaviampCoreDownloadTransferPort { request, onStatus, onJobUpdate ->
        val result = if (request.replaceExisting) {
            downloadService.redownloadTracksWithStatus(
                sourceId = request.sourceId,
                provider = request.provider,
                tracks = request.tracks,
                quality = request.quality,
                maxDownloadBytes = request.maxDownloadBytes,
                isActiveNetworkMobileData = request.isActiveNetworkMobileData,
                allowMobileDownloads = request.allowMobileDownloads,
                setStatus = onStatus,
                onJobUpdate = onJobUpdate,
            )
        } else {
            downloadService.downloadTracksWithStatus(
                sourceId = request.sourceId,
                provider = request.provider,
                tracks = request.tracks,
                quality = request.quality,
                maxDownloadBytes = request.maxDownloadBytes,
                label = request.label,
                isActiveNetworkMobileData = request.isActiveNetworkMobileData,
                allowMobileDownloads = request.allowMobileDownloads,
                includeCompletedCount = request.includeCompletedCount,
                setStatus = onStatus,
                onJobUpdate = onJobUpdate,
            )
        }
        NaviampCoreDownloadTransferResult(shouldRefreshDownloadsAfter(result))
    }
    val keepDownloaded = object : NaviampCoreKeepDownloadedPort {
        override fun policies(sourceId: String) = keepDownloadedRepository.keepDownloadedPolicies(sourceId)

        override fun toggle(policy: KeepDownloadedCollectionPolicy): NaviampKeepDownloadedToggleResult {
            val existing = keepDownloadedRepository.keepDownloadedPolicy(
                policy.sourceId,
                policy.kind,
                policy.collectionId,
            )
            if (existing != null) {
                keepDownloadedRepository.deleteKeepDownloadedPolicy(
                    existing.sourceId,
                    existing.kind,
                    existing.collectionId,
                )
                return NaviampKeepDownloadedToggleResult.Disabled
            }
            keepDownloadedRepository.upsertKeepDownloadedPolicy(policy)
            return NaviampKeepDownloadedToggleResult.Enable
        }

        override fun reconcile(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) =
            planKeepDownloadedReconciliation(
                tracks = tracks,
                previousTrackIds = keepDownloadedRepository.keepDownloadedTrackIds(
                    policy.sourceId,
                    policy.kind,
                    policy.collectionId,
                ),
                downloadedTrackIds = downloadRepository.downloadedTracks(policy.sourceId)
                    .mapTo(mutableSetOf()) { toCoreDownload(it).track.id.value },
                managedTrackIds = keepDownloadedRepository.managedKeepDownloadedTrackIds(policy.sourceId),
                trackIdsRequiredByOtherPolicies = keepDownloadedRepository.keepDownloadedPolicies(policy.sourceId)
                    .filterNot { it.kind == policy.kind && it.collectionId == policy.collectionId }
                    .flatMapTo(mutableSetOf()) {
                        keepDownloadedRepository.keepDownloadedTrackIds(it.sourceId, it.kind, it.collectionId)
                    },
                removeUnneededFiles = policy.removeUnneededFiles,
            ).also { plan ->
                keepDownloadedRepository.replaceKeepDownloadedTrackIds(policy, plan.nextTrackIds)
                keepDownloadedRepository.markManagedKeepDownloadedTracks(
                    policy.sourceId,
                    plan.tracksToDownload.mapTo(mutableSetOf()) { it.id.value },
                )
                plan.trackIdsToRemove.forEach { trackId ->
                    downloadRepository.removeDownloadedAudio(
                        policy.sourceId,
                        app.naviamp.domain.TrackId(trackId),
                    )
                }
                keepDownloadedRepository.unmarkManagedKeepDownloadedTracks(policy.sourceId, plan.trackIdsToRemove)
            }.let { keepDownloadedReconciliationApplication(policy, it) }
    }
    return NaviampCoreDownloadServices(storage, transfer, keepDownloaded, network)
}

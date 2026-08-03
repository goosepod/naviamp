package app.naviamp.presentation

import app.naviamp.app.NaviampDownloadJobController
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.app.downloadsDeletedStatus
import app.naviamp.app.downloadsRefreshStatus
import app.naviamp.app.keepDownloadedDisabledStatus
import app.naviamp.app.keepDownloadedErrorStatus
import app.naviamp.app.keepDownloadedRefreshErrorStatus
import app.naviamp.app.naviampDownloadPreflightStatus
import app.naviamp.app.naviampKeepDownloadedFavoritesPolicy
import app.naviamp.app.naviampKeepDownloadedPlaylistPolicy
import app.naviamp.app.noTracksToDownloadStatus
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.KeepDownloadedActionValue
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampOfflineDashboardUi
import app.naviamp.ui.toDownloadJobUi
import app.naviamp.ui.toDownloadedTrackUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns Downloads/offline state, jobs, actions, playlist mutations, and keep-downloaded policy.
 *
 * Hosts implement only storage, transfer, and network detection. Core composition supplies the
 * shared playback transaction; hosts do not construct a second Downloads action graph or choose
 * product behavior.
 */
class NaviampCoreDownloadsController(
    private val scope: CoroutineScope,
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val storage: NaviampCoreDownloadStoragePort,
    private val transfer: NaviampCoreDownloadTransferPort,
    private val keepDownloaded: NaviampCoreKeepDownloadedPort,
    private val playback: NaviampCoreDownloadedPlaybackPort,
    private val network: NaviampCoreMobileNetworkPort = NaviampCoreMobileNetworkPort { false },
) : NaviampCoreCommandController {
    private var downloadedTracks = emptyList<NaviampCoreDownloadedTrack>()
    private var policies = emptyList<KeepDownloadedCollectionPolicy>()
    private var jobs = emptyList<app.naviamp.domain.cache.DownloadJob>()
    private var snapshotGeneration = 0L
    private val runningJobs = mutableMapOf<String, Job>()
    private val jobController = NaviampDownloadJobController(
        jobs = { jobs },
        setJobs = { updated ->
            jobs = updated
            publishJobs()
        },
    )

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult =
        if (command is NaviampCoreCommand.Downloads) NaviampCoreImmediateCommandResult.Deferred
        else NaviampCoreImmediateCommandResult.Unhandled

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.Downloads.TrackAction -> executeTrackAction(command.request)
            is NaviampCoreCommand.Downloads.CancelJob -> cancel(command.id)
            is NaviampCoreCommand.Downloads.RetryJob -> retry(command.id)
            NaviampCoreCommand.Downloads.Refresh -> refresh()
            NaviampCoreCommand.Downloads.ToggleKeepFavorites -> toggleKeepFavorites()
            NaviampCoreCommand.Downloads.DeleteAll -> deleteAll()
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    suspend fun refresh(status: String? = null, reconcile: Boolean = true) {
        val sourceId = sourceIdOrPublish() ?: return
        runCatching {
            val removed = storage.pruneMissing(sourceId)
            if (!loadSnapshot(sourceId)) return
            publishStatus(status ?: downloadsRefreshStatus(removed))
            if (reconcile) reconcilePolicies(sourceId)
        }.onFailure { cause -> publishStatus(cause.message ?: "Could not refresh downloads.") }
    }

    fun downloadTracks(
        label: String,
        tracks: List<Track>,
        replaceExisting: Boolean = false,
        includeCompletedCount: Boolean = true,
    ): Boolean {
        val provider = providerSource.current()
        val sourceId = currentSourceId()
        val playbackSettings = stateStore.state.value.shell.playback.settings
        val isMobile = network.isActiveNetworkMobileData()
        val blocked = naviampDownloadPreflightStatus(
            providerAvailable = provider != null,
            sourceId = sourceId,
            isActiveNetworkMobileData = isMobile,
            allowMobileDownloads = playbackSettings.allowMobileDownloads,
        )
        if (blocked != null) {
            publishStatus(blocked)
            return false
        }
        val job = jobController.create(label, tracks, replaceExisting)
        if (job == null) {
            publishStatus(noTracksToDownloadStatus())
            return false
        }
        val activeProvider = requireNotNull(provider)
        val activeSourceId = requireNotNull(sourceId)
        val running = scope.launch {
            try {
                val result = transfer.transfer(
                    request = NaviampCoreDownloadTransferRequest(
                        label = label,
                        tracks = tracks,
                        sourceId = activeSourceId,
                        provider = activeProvider,
                        quality = playbackSettings.downloadStreamQuality(),
                        maxDownloadBytes = stateStore.state.value.shell.cache.settings.maxDownloadBytes,
                        replaceExisting = replaceExisting,
                        allowMobileDownloads = playbackSettings.allowMobileDownloads,
                        isActiveNetworkMobileData = isMobile,
                        includeCompletedCount = includeCompletedCount,
                    ),
                    onStatus = { message ->
                        if (currentSourceId() == activeSourceId) publishStatus(message)
                    },
                    onJobUpdate = { update -> jobController.update(job.id, update) },
                )
                if (result.refreshDownloads) loadSnapshot(activeSourceId)
            } catch (cause: Throwable) {
                if (cause is kotlinx.coroutines.CancellationException) throw cause
                jobController.update(job.id, DownloadJobUpdate.Failed(null, cause.message ?: "Download failed"))
                publishStatus(cause.message ?: "Could not download $label.")
            } finally {
                jobController.complete(job.id)
                runningJobs.remove(job.id)
            }
        }
        runningJobs[job.id] = running
        jobController.registerCancellation(job.id, running::cancel)
        return true
    }

    /** Adapter used by the Core playlist transaction graph; hosts never interpret [option]. */
    suspend fun downloadPlaylist(playlist: Playlist, tracks: List<Track>, option: String?) {
        if (option != KeepDownloadedActionValue) {
            downloadTracks(playlist.name, tracks)
            return
        }
        val sourceId = sourceIdOrPublish() ?: return
        val policy = naviampKeepDownloadedPlaylistPolicy(sourceId, playlist)
        if (keepDownloaded.toggle(policy) == NaviampKeepDownloadedToggleResult.Disabled) {
            reloadPolicies(sourceId)
            publishStatus(keepDownloadedDisabledStatus(playlist.name))
        } else {
            reconcilePolicy(policy, tracks)
        }
    }

    /** Reconciles a changed server playlist immediately when Core is watching it. */
    suspend fun playlistTracksChanged(playlistId: String) {
        val sourceId = currentSourceId() ?: return
        val policy = keepDownloaded.policies(sourceId).firstOrNull { candidate ->
            candidate.collectionId == playlistId &&
                candidate.kind in setOf(
                    KeepDownloadedCollectionKind.Playlist,
                    KeepDownloadedCollectionKind.SmartPlaylist,
                )
        } ?: return
        val provider = providerSource.current() ?: return
        runCatching { provider.playlistTracks(playlistId) }
            .onSuccess { tracks -> reconcilePolicy(policy, tracks) }
            .onFailure { publishStatus(keepDownloadedRefreshErrorStatus(policy.name, it)) }
    }

    private suspend fun executeTrackAction(request: DownloadedTrackActionRequest) {
        val download = downloadedTracks.firstOrNull { it.storageId == request.download.id }
        if (download == null) {
            publishStatus("Downloaded track is no longer available.")
            return
        }
        when (request.action) {
            DownloadedTrackAction.Select -> playback.play(downloadedTracks.map { it.track }, downloadedTracks.indexOf(download))
            DownloadedTrackAction.AddToPlaylist -> addToPlaylist(download.track, request.playlistChoice?.id)
            DownloadedTrackAction.CreatePlaylistAndAdd -> createPlaylist(download.track, request.playlistName)
            DownloadedTrackAction.Remove -> remove(download)
        }
    }

    private suspend fun addToPlaylist(track: Track, playlistId: String?) {
        val provider = providerOrPublish() ?: return
        if (playlistId.isNullOrBlank()) {
            publishStatus("Choose a playlist first.")
            return
        }
        runCatching { provider.addTracksToPlaylist(playlistId, listOf(track.id)) }
            .onSuccess {
                playlistTracksChanged(playlistId)
                publishStatus("Added ${track.title} to playlist.")
            }
            .onFailure { publishStatus(it.message ?: "Could not add track to playlist.") }
    }

    private suspend fun createPlaylist(track: Track, requestedName: String?) {
        val provider = providerOrPublish() ?: return
        val name = requestedName?.trim().orEmpty()
        if (name.isEmpty()) {
            publishStatus("Playlist name cannot be blank.")
            return
        }
        runCatching { provider.createPlaylist(name, listOf(track.id)) }
            .onSuccess { publishStatus("Created $name.") }
            .onFailure { publishStatus(it.message ?: "Could not create playlist.") }
    }

    private suspend fun remove(download: NaviampCoreDownloadedTrack) {
        val sourceId = sourceIdOrPublish() ?: return
        runCatching { storage.remove(sourceId, download.track) }
            .onSuccess {
                if (loadSnapshot(sourceId)) {
                    publishStatus("Removed ${download.track.title} from downloads.")
                }
            }
            .onFailure { publishStatus(it.message ?: "Could not remove downloaded track.") }
    }

    private suspend fun deleteAll() {
        val sourceId = sourceIdOrPublish() ?: return
        runCatching { storage.deleteAll(sourceId) }
            .onSuccess { count ->
                if (loadSnapshot(sourceId)) publishStatus(downloadsDeletedStatus(count))
            }
            .onFailure { publishStatus(it.message ?: "Could not delete downloads.") }
    }

    private suspend fun toggleKeepFavorites() {
        val sourceId = sourceIdOrPublish() ?: return
        val policy = naviampKeepDownloadedFavoritesPolicy(sourceId)
        if (keepDownloaded.toggle(policy) == NaviampKeepDownloadedToggleResult.Disabled) {
            reloadPolicies(sourceId)
            publishStatus(keepDownloadedDisabledStatus("Favorites"))
            return
        }
        val provider = providerOrPublish() ?: return
        runCatching { provider.favoriteTracks() }
            .onSuccess { tracks -> reconcilePolicy(policy, tracks) }
            .onFailure { publishStatus(keepDownloadedErrorStatus("favorites", it)) }
    }

    private suspend fun reconcilePolicies(sourceId: String) {
        reloadPolicies(sourceId)
        val provider = providerSource.current() ?: return
        policies.forEach { policy ->
            runCatching {
                val tracks = when (policy.kind) {
                    KeepDownloadedCollectionKind.Playlist,
                    KeepDownloadedCollectionKind.SmartPlaylist,
                    -> provider.playlistTracks(policy.collectionId)
                    KeepDownloadedCollectionKind.Favorites -> provider.favoriteTracks()
                }
                reconcilePolicy(policy, tracks)
            }.onFailure { publishStatus(keepDownloadedRefreshErrorStatus(policy.name, it)) }
        }
    }

    private fun reconcilePolicy(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) {
        val application = keepDownloaded.reconcile(policy, tracks)
        reloadPolicies(policy.sourceId)
        application.status?.let(::publishStatus)
        application.downloadLabel?.let { label -> downloadTracks(label, application.tracksToDownload) }
        if (application.refreshDownloads) scope.launch { loadSnapshot(policy.sourceId) }
    }

    private suspend fun cancel(jobId: String) {
        val completedAny = jobController.cancel(jobId)
        if (completedAny) currentSourceId()?.let { loadSnapshot(it) }
        if (runningJobs[jobId] == null && jobs.none { it.id == jobId }) {
            publishStatus("Download job is no longer available.")
        }
    }

    private fun retry(jobId: String) {
        val retry = jobController.retry(jobId)
        if (retry == null) {
            publishStatus("Download job cannot be retried.")
            return
        }
        if (downloadTracks(retry.label, retry.tracks, retry.replaceExisting)) {
            jobController.dismiss(jobId)
        }
    }

    private suspend fun loadSnapshot(sourceId: String): Boolean {
        val generation = ++snapshotGeneration
        val snapshot = storage.snapshot(sourceId)
        if (generation != snapshotGeneration || currentSourceId() != sourceId) return false
        downloadedTracks = snapshot.downloads
        reloadPolicies(sourceId)
        val coverArt = providerSource.current()?.let { provider -> { id: String? -> id?.let(provider::coverArtUrl) } }
            ?: { _: String? -> null }
        stateStore.updateShell { shell ->
            shell.copy(
                downloads = shell.downloads.copy(
                    downloads = downloadedTracks.map { download -> download.toUi(coverArt) },
                    downloadBytes = downloadedTracks.sumOf(NaviampCoreDownloadedTrack::sizeBytes),
                    maxDownloadBytes = shell.cache.settings.maxDownloadBytes,
                    offlineDashboard = NaviampOfflineDashboardUi(
                        audioCacheCount = snapshot.audioCacheCount,
                        audioCacheBytes = snapshot.audioCacheBytes,
                        maxAudioCacheBytes = shell.cache.settings.maxAudioCacheBytes,
                        pendingProviderActionCount = snapshot.pendingProviderActionCount,
                    ),
                    keepFavoritesDownloaded = policies.any { it.kind == KeepDownloadedCollectionKind.Favorites },
                ),
            )
        }
        return true
    }

    private fun reloadPolicies(sourceId: String) {
        policies = keepDownloaded.policies(sourceId)
        stateStore.updateShell { shell ->
            shell.copy(
                downloads = shell.downloads.copy(
                    keepFavoritesDownloaded = policies.any { it.kind == KeepDownloadedCollectionKind.Favorites },
                ),
            )
        }
    }

    private fun publishJobs() {
        stateStore.updateShell { shell ->
            shell.copy(downloads = shell.downloads.copy(jobs = jobs.map { it.toDownloadJobUi() }))
        }
    }

    private fun publishStatus(message: String) {
        stateStore.updateShell { shell -> shell.copy(downloads = shell.downloads.copy(status = message)) }
    }

    private fun currentSourceId(): String? = stateStore.state.value.shell.connectionSettings.currentSourceId

    private fun sourceIdOrPublish(): String? = currentSourceId().also {
        if (it == null) publishStatus("Connect to Navidrome to use downloads.")
    }

    private fun providerOrPublish(): MediaProvider? = providerSource.current().also {
        if (it == null) publishStatus("Connect to Navidrome to use downloads.")
    }
}

private fun NaviampCoreDownloadedTrack.toUi(
    coverArtUrl: (String?) -> String?,
): NaviampDownloadedTrackUi = track.toDownloadedTrackUi(storageId, sizeBytes, qualityLabel, coverArtUrl)

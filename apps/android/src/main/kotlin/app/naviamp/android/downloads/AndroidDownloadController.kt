package app.naviamp.android

import android.content.Context
import app.naviamp.domain.Track
import app.naviamp.domain.cache.downloadBlockedStatus
import app.naviamp.domain.cache.downloadCompletedStatus
import app.naviamp.domain.cache.downloadErrorStatus
import app.naviamp.domain.cache.downloadRemoveErrorStatus
import app.naviamp.domain.cache.downloadStartingStatus
import app.naviamp.domain.cache.downloadedTrackRemovedStatus
import app.naviamp.domain.cache.planDownloadTracks
import app.naviamp.domain.settings.downloadStreamQuality
import app.naviamp.ui.NaviampDownloadedTrackUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun downloadAndroidTrack(
    context: Context,
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    track: Track,
) {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    scope.launch {
        with(state) {
            val plan = planDownloadTracks(
                tracks = listOf(track),
                hasProvider = activeProvider != null,
                hasSource = sourceId != null,
                isActiveNetworkMobileData = context.isActiveNetworkMobileData(),
                allowMobileDownloads = playbackSettings.allowMobileDownloads,
            )
            plan.blockedReason?.let { reason ->
                downloadStatus = downloadBlockedStatus(reason, track.title)
                status = downloadStatus.orEmpty()
                return@launch
            }
            val quality = playbackSettings.downloadStreamQuality()
            downloadStatus = downloadStartingStatus(track.title)
            status = downloadStatus.orEmpty()
            runCatching {
                withContext(Dispatchers.IO) {
                    storage.downloadAudioTrack(
                        sourceId = requireNotNull(sourceId),
                        provider = requireNotNull(activeProvider),
                        track = track,
                        quality = quality,
                        maxDownloadBytes = AndroidMaxDownloadBytes,
                    )
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
                downloadStatus = downloadCompletedStatus(track.title)
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = downloadErrorStatus("track", error)
                status = downloadStatus.orEmpty()
            }
        }
    }
}

fun downloadAndroidTracks(
    context: Context,
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    tracksToDownload: List<Track>,
    label: String = "tracks",
) {
    val activeProvider = state.provider
    val sourceId = state.activeSourceId
    scope.launch {
        with(state) {
            val plan = planDownloadTracks(
                tracks = tracksToDownload,
                hasProvider = activeProvider != null,
                hasSource = sourceId != null,
                isActiveNetworkMobileData = context.isActiveNetworkMobileData(),
                allowMobileDownloads = playbackSettings.allowMobileDownloads,
                deduplicateTracks = true,
            )
            plan.blockedReason?.let { reason ->
                downloadStatus = downloadBlockedStatus(reason, label)
                status = downloadStatus.orEmpty()
                return@launch
            }
            val quality = playbackSettings.downloadStreamQuality()
            downloadStatus = downloadStartingStatus(label)
            status = downloadStatus.orEmpty()
            runCatching {
                withContext(Dispatchers.IO) {
                    plan.tracks.forEach { track ->
                        storage.downloadAudioTrack(
                            sourceId = requireNotNull(sourceId),
                            provider = requireNotNull(activeProvider),
                            track = track,
                            quality = quality,
                            maxDownloadBytes = AndroidMaxDownloadBytes,
                        )
                    }
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
                downloadStatus = downloadCompletedStatus(label)
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = downloadErrorStatus(label, error)
                status = downloadStatus.orEmpty()
            }
        }
    }
}

fun removeAndroidDownload(
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    download: NaviampDownloadedTrackUi,
    findKnownTrack: (String) -> Track?,
) {
    val sourceId = state.activeSourceId ?: return
    scope.launch {
        with(state) {
            val track = downloadedTracks.firstOrNull { it.track.id.value == download.track.id }?.track
                ?: findKnownTrack(download.track.id)
                ?: return@launch
            runCatching {
                withContext(Dispatchers.IO) {
                    storage.removeDownloadedAudioForTrack(sourceId, track.id)
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
                downloadStatus = downloadedTrackRemovedStatus(track.title)
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = downloadRemoveErrorStatus(error)
                status = downloadStatus.orEmpty()
            }
        }
    }
}

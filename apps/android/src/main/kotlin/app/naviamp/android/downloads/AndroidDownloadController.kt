package app.naviamp.android

import android.content.Context
import app.naviamp.domain.Track
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
    val activeProvider = state.provider ?: return
    val sourceId = state.activeSourceId ?: return
    scope.launch {
        with(state) {
            if (context.isActiveNetworkMobileData() && !playbackSettings.allowMobileDownloads) {
                downloadStatus = "Downloads over mobile data are disabled."
                status = downloadStatus.orEmpty()
                return@launch
            }
            val quality = playbackSettings.downloadStreamQuality()
            downloadStatus = "Downloading ${track.title}..."
            status = downloadStatus.orEmpty()
            runCatching {
                withContext(Dispatchers.IO) {
                    storage.downloadAudioTrack(
                        sourceId = sourceId,
                        provider = activeProvider,
                        track = track,
                        quality = quality,
                        maxDownloadBytes = AndroidMaxDownloadBytes,
                    )
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
                downloadStatus = "Downloaded ${track.title}."
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = error.message ?: "Could not download track."
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
    val activeProvider = state.provider ?: return
    val sourceId = state.activeSourceId ?: return
    val uniqueTracks = tracksToDownload.distinctBy { it.id }
    if (uniqueTracks.isEmpty()) {
        state.status = "No tracks found."
        return
    }
    scope.launch {
        with(state) {
            if (context.isActiveNetworkMobileData() && !playbackSettings.allowMobileDownloads) {
                downloadStatus = "Downloads over mobile data are disabled."
                status = downloadStatus.orEmpty()
                return@launch
            }
            val quality = playbackSettings.downloadStreamQuality()
            downloadStatus = "Downloading $label..."
            status = downloadStatus.orEmpty()
            runCatching {
                withContext(Dispatchers.IO) {
                    uniqueTracks.forEach { track ->
                        storage.downloadAudioTrack(
                            sourceId = sourceId,
                            provider = activeProvider,
                            track = track,
                            quality = quality,
                            maxDownloadBytes = AndroidMaxDownloadBytes,
                        )
                    }
                }
            }.onSuccess {
                downloadRefreshToken += 1
                storageStats = withContext(Dispatchers.IO) { storage.stats() }
                downloadStatus = "Downloaded $label."
                status = downloadStatus.orEmpty()
            }.onFailure { error ->
                downloadStatus = error.message ?: "Could not download $label."
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
            withContext(Dispatchers.IO) {
                storage.removeDownloadedAudioForTrack(sourceId, track.id)
            }
            downloadRefreshToken += 1
            storageStats = withContext(Dispatchers.IO) { storage.stats() }
            downloadStatus = "Removed ${track.title}."
            status = downloadStatus.orEmpty()
        }
    }
}

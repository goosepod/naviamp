package app.naviamp.android.playback

import android.util.Log
import app.naviamp.android.AndroidStorageDependencies
import app.naviamp.domain.Album
import app.naviamp.domain.Track
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.planPlaybackSessionRestore
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection

internal class AndroidPlaybackServiceSessionController(
    private val storage: () -> AndroidStorageDependencies,
    private val currentMetadata: () -> AndroidPlaybackNotificationMetadata,
    private val setCurrentMetadata: (AndroidPlaybackNotificationMetadata) -> Unit,
    private val syncQueue: (PlaybackQueue) -> Unit,
    private val updateMediaSession: (AndroidPlaybackNotificationMetadata) -> Unit,
    private val loadCoverArt: (String, AndroidPlaybackNotificationMetadata) -> Unit,
) {
    fun hydrateSavedPlaybackSession(): Boolean {
        if (!currentMetadata().title.isNullOrBlank()) return false
        val storage = storage()
        val sourceId = storage.latestNavidromeSource()?.id ?: return false
        val session = storage.loadPlaybackSession(sourceId) ?: return false
        val restorePlan = planPlaybackSessionRestore(session)
        if (restorePlan !is PlaybackSessionRestorePlan.TrackSession) return false
        val track = restorePlan.currentTrack
        syncQueue(restorePlan.playbackQueue)
        val coverArtUrl = storage.savedCoverArtUrl(track)
        val metadata = AndroidPlaybackNotificationMetadata(
            title = track.title,
            subtitle = track.artistName,
            coverArtUrl = coverArtUrl,
        )
        setCurrentMetadata(metadata)
        AndroidPlaybackNotificationControls.positionMillis = restorePlan.restoredStartPositionSeconds
            ?.takeIf { it > 0.0 }
            ?.let { (it * 1_000.0).toLong() }
        AndroidPlaybackNotificationControls.durationMillis = track.durationSeconds
            ?.takeIf { it > 0 }
            ?.let { it * 1_000L }
        updateMediaSession(metadata)
        coverArtUrl?.let { loadCoverArt(it, metadata) }
        Log.i(
            "NaviampSession",
            "Hydrated Android Auto session source=$sourceId title=${track.title} position=${restorePlan.restoredStartPositionSeconds}",
        )
        return true
    }

    fun restoredNowPlayingMetadata(): AndroidPlaybackNotificationMetadata? {
        val storage = storage()
        val sourceId = storage.latestNavidromeSource()?.id ?: return null
        val session = storage.loadPlaybackSession(sourceId) ?: return null
        session.internetRadioStation?.let { station ->
            return AndroidPlaybackNotificationMetadata(
                title = station.name,
                subtitle = "Internet radio",
            )
        }
        val track = session.currentTrack() ?: return null
        return AndroidPlaybackNotificationMetadata(
            title = track.title,
            subtitle = track.artistName,
            coverArtUrl = storage.savedCoverArtUrl(track),
        )
    }
}

private fun MediaSourceRepository.savedCoverArtUrl(track: Track): String? {
    val coverArtId = track.coverArtId ?: track.albumId?.value ?: return null
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}

private fun MediaSourceRepository.savedCoverArtUrl(album: Album): String? {
    val coverArtId = album.coverArtId ?: album.id.value
    val connection = latestMediaSource()?.toNavidromeConnection() ?: return null
    return NavidromeProvider(connection).coverArtUrl(coverArtId)
}

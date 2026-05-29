package app.naviamp.domain.media

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.queue.PlaybackQueue

data class TrackPlaybackSelection(
    val tracks: List<Track>,
    val index: Int,
)

fun trackPlaybackSelection(
    tracks: List<Track>,
    index: Int,
    shuffle: Boolean = false,
): TrackPlaybackSelection? {
    if (tracks.isEmpty() || index !in tracks.indices) return null
    return TrackPlaybackSelection(
        tracks = if (shuffle) tracks.shuffled() else tracks,
        index = if (shuffle) 0 else index,
    )
}

fun Track?.withUpdatedTrack(updatedTrack: Track): Track? =
    this?.let { track ->
        if (track.id == updatedTrack.id) updatedTrack else track
    }

fun List<Track>.withUpdatedTrack(updatedTrack: Track): List<Track> =
    map { track ->
        if (track.id == updatedTrack.id) updatedTrack else track
    }

fun MediaSearchResults.withUpdatedTrack(updatedTrack: Track): MediaSearchResults =
    copy(tracks = tracks.withUpdatedTrack(updatedTrack))

fun AlbumDetails.withUpdatedTrack(updatedTrack: Track): AlbumDetails =
    copy(tracks = tracks.withUpdatedTrack(updatedTrack))

fun PlaybackQueue.withUpdatedTrack(updatedTrack: Track): PlaybackQueue =
    copy(tracks = tracks.withUpdatedTrack(updatedTrack))

suspend fun favoriteTrackUpdate(
    provider: MediaProvider,
    track: Track,
    favoritedAtIso8601: String?,
): Track? {
    if (!provider.capabilities.supportsTrackFavorites) return null
    val favorite = track.favoritedAtIso8601 == null
    provider.setTrackFavorite(track.id, favorite)
    return track.copy(favoritedAtIso8601 = if (favorite) favoritedAtIso8601 else null)
}

suspend fun ratedTrackUpdate(
    provider: MediaProvider,
    track: Track,
    rating: Int?,
): Track? {
    if (!provider.capabilities.supportsTrackRatings) return null
    provider.setTrackRating(track.id, rating)
    return track.copy(userRating = rating)
}

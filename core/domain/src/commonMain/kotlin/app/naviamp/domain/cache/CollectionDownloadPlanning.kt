package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaPage
import app.naviamp.domain.provider.MediaPageRequest

const val MaximumSubscribedArtistAlbums = 200
const val MaximumSubscribedCollectionTracks = 2_000

sealed interface CollectionDownloadPlanningResult {
    data class Ready(val plan: CollectionDownloadPlan) : CollectionDownloadPlanningResult
    data class TooLarge(val maximumItems: Int) : CollectionDownloadPlanningResult
    data object Unsupported : CollectionDownloadPlanningResult
}

data class CollectionDownloadPlan(
    val tracks: List<Track>,
    val albumCount: Int,
    val alreadyDownloadedCount: Int,
    val newTracks: List<Track>,
    val knownNewBytes: Long,
    val unknownSizeCount: Int,
    val remainingBudgetBytes: Long,
) {
    val estimatedNewBytes: Long? get() = knownNewBytes.takeIf { unknownSizeCount == 0 }
    val knownToExceedBudget: Boolean get() = knownNewBytes > remainingBudgetBytes
}

suspend fun planAlbumDownload(
    albumId: AlbumId,
    quality: StreamQuality,
    downloadedTrackIds: Set<TrackId>,
    currentDownloadBytes: Long,
    maxDownloadBytes: Long,
    loadTracksPage: suspend (AlbumId, MediaPageRequest) -> MediaPage<Track>?,
): CollectionDownloadPlanningResult = when (val tracks = enumerateFavoriteDownloadCatalog(
    maximumItems = MaximumSubscribedCollectionTracks,
    loadPage = { request -> loadTracksPage(albumId, request) },
)) {
    is FavoriteDownloadCatalog.Complete -> CollectionDownloadPlanningResult.Ready(
        buildCollectionDownloadPlan(tracks.items, 1, quality, downloadedTrackIds, currentDownloadBytes, maxDownloadBytes),
    )
    is FavoriteDownloadCatalog.TooLarge -> CollectionDownloadPlanningResult.TooLarge(tracks.maximumItems)
    FavoriteDownloadCatalog.Unsupported -> CollectionDownloadPlanningResult.Unsupported
}

suspend fun planArtistDownload(
    artistId: ArtistId,
    quality: StreamQuality,
    downloadedTrackIds: Set<TrackId>,
    currentDownloadBytes: Long,
    maxDownloadBytes: Long,
    loadAlbumsPage: suspend (ArtistId, MediaPageRequest) -> MediaPage<Album>?,
    loadTracksPage: suspend (AlbumId, MediaPageRequest) -> MediaPage<Track>?,
): CollectionDownloadPlanningResult {
    val albums = when (val catalog = enumerateFavoriteDownloadCatalog(
        maximumItems = MaximumSubscribedArtistAlbums,
        loadPage = { request -> loadAlbumsPage(artistId, request) },
    )) {
        is FavoriteDownloadCatalog.Complete -> catalog.items.distinctBy(Album::id)
        is FavoriteDownloadCatalog.TooLarge -> return CollectionDownloadPlanningResult.TooLarge(catalog.maximumItems)
        FavoriteDownloadCatalog.Unsupported -> return CollectionDownloadPlanningResult.Unsupported
    }
    val tracks = linkedMapOf<TrackId, Track>()
    for (album in albums) {
        val remaining = MaximumSubscribedCollectionTracks - tracks.size
        if (remaining == 0) return CollectionDownloadPlanningResult.TooLarge(MaximumSubscribedCollectionTracks)
        when (val catalog = enumerateFavoriteDownloadCatalog(
            maximumItems = remaining,
            loadPage = { request -> loadTracksPage(album.id, request) },
        )) {
            is FavoriteDownloadCatalog.Complete -> catalog.items.forEach { track -> tracks.putIfAbsent(track.id, track) }
            is FavoriteDownloadCatalog.TooLarge -> return CollectionDownloadPlanningResult.TooLarge(MaximumSubscribedCollectionTracks)
            FavoriteDownloadCatalog.Unsupported -> return CollectionDownloadPlanningResult.Unsupported
        }
    }
    return CollectionDownloadPlanningResult.Ready(
        buildCollectionDownloadPlan(tracks.values.toList(), albums.size, quality, downloadedTrackIds,
            currentDownloadBytes, maxDownloadBytes),
    )
}

private fun buildCollectionDownloadPlan(
    tracks: List<Track>,
    albumCount: Int,
    quality: StreamQuality,
    downloadedTrackIds: Set<TrackId>,
    currentDownloadBytes: Long,
    maxDownloadBytes: Long,
): CollectionDownloadPlan {
    val distinct = tracks.distinctBy(Track::id)
    val newTracks = distinct.filterNot { it.id in downloadedTrackIds }
    val estimates = newTracks.map { track -> track.estimatedDownloadBytes(quality) }
    return CollectionDownloadPlan(
        tracks = distinct,
        albumCount = albumCount,
        alreadyDownloadedCount = distinct.size - newTracks.size,
        newTracks = newTracks,
        knownNewBytes = estimates.filterNotNull().sum(),
        unknownSizeCount = estimates.count { it == null },
        remainingBudgetBytes = (maxDownloadBytes - currentDownloadBytes).coerceAtLeast(0),
    )
}

private fun Track.estimatedDownloadBytes(quality: StreamQuality): Long? {
    val seconds = durationSeconds?.takeIf { it > 0 } ?: return null
    val bitrate = when (quality) {
        StreamQuality.Original -> audioInfo?.bitrateKbps
        is StreamQuality.Transcoded -> quality.bitrateKbps
    }?.takeIf { it > 0 } ?: return null
    return seconds.toLong() * bitrate.toLong() * 125L
}

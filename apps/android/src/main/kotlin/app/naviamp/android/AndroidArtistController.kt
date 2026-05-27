package app.naviamp.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.Track
import app.naviamp.ui.SharedMediaItemUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun localAndroidArtistDetail(
    storage: AndroidStorage,
    sourceId: String,
    artistId: ArtistId,
    fallbackName: String?,
): ArtistDetails? {
    val tracks = storage.libraryTracksForArtist(sourceId, artistId, limit = 1_000)
    if (tracks.isEmpty()) return fallbackName?.let { name ->
        ArtistDetails(artist = Artist(artistId, name), albums = emptyList(), info = null)
    }
    val artistName = fallbackName ?: tracks.firstOrNull()?.artistName ?: "Unknown Artist"
    val albums = tracks
        .groupBy { track -> track.albumId?.value ?: track.albumTitle.orEmpty() }
        .mapNotNull { (_, albumTracks) ->
            val first = albumTracks.firstOrNull() ?: return@mapNotNull null
            val albumId = first.albumId ?: return@mapNotNull null
            Album(
                id = albumId,
                title = first.albumTitle ?: "Unknown Album",
                artistName = first.artistName,
                coverArtId = first.coverArtId,
                recentlyAddedAtIso8601 = null,
                releaseYear = first.albumReleaseYear,
            )
        }
        .sortedWith(compareBy<Album> { it.releaseYear ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
    return ArtistDetails(
        artist = Artist(artistId, artistName),
        albums = albums,
        info = null,
    )
}

fun openAndroidArtistDetails(
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    popularTracksService: ArtistPopularTracksService,
    artistId: ArtistId,
    fallbackName: String? = null,
    pushCurrentArtist: Boolean = true,
) {
    val activeProvider = state.provider ?: return
    val sourceId = state.activeSourceId
    with(state) {
        if (pushCurrentArtist) {
            contentState.artistDetail
                ?.artist
                ?.takeIf { currentArtist -> currentArtist.id != artistId }
                ?.let { currentArtist -> artistDetailBackStack = artistDetailBackStack + currentArtist }
        }
    }
    scope.launch {
        with(state) {
            status = "Loading ${fallbackName ?: "artist"}..."
            runCatching { activeProvider.artist(artistId) }
                .recoverCatching { error ->
                    val fallbackDetail = sourceId?.let { localAndroidArtistDetail(storage, it, artistId, fallbackName) }
                    fallbackDetail ?: throw error
                }
                .onSuccess { detail ->
                    contentState = contentState.showArtist(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                    status = if (detail.albums.isEmpty()) {
                        "No albums found for ${detail.artist.name}."
                    } else {
                        "Connected."
                    }
                    if (sourceId != null) {
                        artistPopularTracksStatusByArtistId =
                            artistPopularTracksStatusByArtistId + (artistId.value to "Loading popular tracks...")
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                popularTracksService.popularTracks(
                                    sourceId = sourceId,
                                    artist = detail.artist,
                                    limit = PopularTracksFetchLimit,
                                )
                            }.onSuccess { matches ->
                                val matchedTracks = matches
                                    .map { it.matchedTrack }
                                    .take(PopularTracksDisplayLimit)
                                withContext(Dispatchers.Main) {
                                    artistPopularTracksByArtistId =
                                        artistPopularTracksByArtistId + (artistId.value to matchedTracks)
                                    artistPopularTracksStatusByArtistId =
                                        artistPopularTracksStatusByArtistId + (
                                            artistId.value to matchedTracks
                                                .takeIf { it.isEmpty() }
                                                ?.let { "No popular tracks matched songs in your library." }
                                            )
                                }
                            }.onFailure { error ->
                                withContext(Dispatchers.Main) {
                                    artistPopularTracksStatusByArtistId =
                                        artistPopularTracksStatusByArtistId + (
                                            artistId.value to "Popular tracks unavailable: ${error.message ?: "unknown error"}"
                                            )
                                }
                            }
                        }
                    } else {
                        artistPopularTracksStatusByArtistId =
                            artistPopularTracksStatusByArtistId + (
                                artistId.value to "Popular tracks unavailable: no connected media source."
                                )
                    }
                }
                .onFailure { error -> status = error.message ?: "Artist failed to load." }
        }
    }
}

fun findAndroidSimilarArtists(
    scope: CoroutineScope,
    state: AndroidAppState,
    similarArtistsService: SimilarArtistsService,
    artistId: ArtistId,
    artistName: String,
) {
    with(state) {
        artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (artistId.value to "Finding similar artists...")
        artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId - artistId.value
    }
    scope.launch {
        with(state) {
            runCatching {
                similarArtistsService.similarArtists(
                    artistName = artistName,
                    limit = SimilarArtistsFetchLimit,
                )
            }.onSuccess { artists ->
                artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId + (
                    artistId.value to artists.take(SimilarArtistsDisplayLimit)
                    )
                artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (
                    artistId.value to if (artists.isEmpty()) "No similar artists found." else null
                    )
            }.onFailure { error ->
                artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (
                    artistId.value to "Similar artists unavailable: ${error.message ?: "unknown error"}"
                    )
            }
        }
    }
}

fun openAndroidExternalArtistUrl(
    context: Context,
    state: AndroidAppState,
    url: String,
) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { error ->
        state.status = "Could not open external artist page: ${error.message ?: "unknown error"}"
    }
}

fun openAndroidAlbumDetails(
    scope: CoroutineScope,
    state: AndroidAppState,
    selectedAlbum: SharedMediaItemUi,
) {
    val activeProvider = state.provider ?: return
    scope.launch {
        with(state) {
            status = "Loading ${selectedAlbum.title}..."
            runCatching {
                activeProvider.album(AlbumId(selectedAlbum.id))
            }.onSuccess { detail ->
                contentState = contentState.showAlbum(detail)
                tracks = detail.tracks
                nowPlayingOpen = false
                status = "Connected."
            }.onFailure { error ->
                status = error.message ?: "Album failed to load."
            }
        }
    }
}

fun loadAndroidArtistTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    action: (List<Track>) -> Unit,
) {
    val albums = state.artistDetail?.albums.orEmpty()
    val activeProvider = state.provider ?: return
    scope.launch {
        state.status = "Loading artist tracks..."
        action(
            albums.flatMap { album ->
                runCatching { activeProvider.album(album.id).tracks }.getOrDefault(emptyList())
            },
        )
    }
}

fun loadAndroidArtistAlbumTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    selectedAlbum: SharedMediaItemUi,
    action: (List<Track>) -> Unit,
) {
    val activeProvider = state.provider ?: return
    scope.launch {
        state.status = "Loading ${selectedAlbum.title}..."
        runCatching { activeProvider.album(AlbumId(selectedAlbum.id)).tracks }
            .onSuccess(action)
            .onFailure { error -> state.status = error.message ?: "Could not load album." }
    }
}

fun startAndroidArtistAlbumRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    selectedAlbum: SharedMediaItemUi,
    startAlbumRadio: (Album, List<Track>) -> Unit,
) {
    val activeProvider = state.provider ?: return
    scope.launch {
        state.status = "Starting ${selectedAlbum.title} radio..."
        runCatching { activeProvider.album(AlbumId(selectedAlbum.id)) }
            .onSuccess { detail -> startAlbumRadio(detail.album, detail.tracks) }
            .onFailure { error -> state.status = error.message ?: "Could not start album radio." }
    }
}

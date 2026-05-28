package app.naviamp.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.media.artistDetailLoadErrorStatus
import app.naviamp.domain.media.artistDetailLoadedStatus
import app.naviamp.domain.media.artistDetailLoadingStatus
import app.naviamp.domain.media.artistDetailsFromLibraryTracks
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.Track
import app.naviamp.ui.SharedMediaItemUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            status = artistDetailLoadingStatus(fallbackName)
            runCatching { activeProvider.artist(artistId) }
                .recoverCatching { error ->
                    val fallbackDetail = sourceId?.let {
                        artistDetailsFromLibraryTracks(
                            artistId = artistId,
                            fallbackName = fallbackName,
                            tracks = storage.libraryTracksForArtist(it, artistId, limit = 1_000),
                        )
                    }
                    fallbackDetail ?: throw error
                }
                .onSuccess { detail ->
                    contentState = contentState.showArtist(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                    status = artistDetailLoadedStatus(detail)
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
                .onFailure { error -> status = artistDetailLoadErrorStatus(error) }
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

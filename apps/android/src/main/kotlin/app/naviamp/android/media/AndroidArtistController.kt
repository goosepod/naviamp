package app.naviamp.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.media.albumDetailLoadErrorStatus
import app.naviamp.domain.media.albumDetailLoadedStatus
import app.naviamp.domain.media.albumDetailLoadingStatus
import app.naviamp.domain.media.ArtistDetailPopularTracksDisplayLimit
import app.naviamp.domain.media.ArtistDetailPopularTracksFetchLimit
import app.naviamp.domain.media.ArtistDetailSimilarArtistsDisplayLimit
import app.naviamp.domain.media.ArtistDetailSimilarArtistsFetchLimit
import app.naviamp.domain.media.artistPopularTracksUpdate
import app.naviamp.domain.media.artistDetailLoadErrorStatus
import app.naviamp.domain.media.artistDetailLoadedStatus
import app.naviamp.domain.media.artistDetailLoadingStatus
import app.naviamp.domain.media.loadingPopularTracksStatus
import app.naviamp.domain.media.loadingSimilarArtistsStatus
import app.naviamp.domain.media.loadAlbumDetails
import app.naviamp.domain.media.loadArtistDetails
import app.naviamp.domain.media.missingPopularTracksSourceStatus
import app.naviamp.domain.media.popularTracksUnavailableStatus
import app.naviamp.domain.media.similarArtistsUnavailableStatus
import app.naviamp.domain.media.similarArtistsUpdate
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.SharedTrackRowUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun openAndroidArtistDetails(
    scope: CoroutineScope,
    state: AndroidAppState,
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    popularTracksService: ArtistPopularTracksService,
    artistId: ArtistId,
    fallbackName: String? = null,
    pushCurrentArtist: Boolean = true,
) {
    val activeProvider = state.provider ?: return
    val sourceId = state.activeSourceId
    val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
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
            runCatching {
                loadArtistDetails(
                    libraryIndexRepository = libraryIndexRepository,
                    providerResponseService = providerResponseService,
                    provider = activeProvider,
                    artistId = artistId,
                    fallbackName = fallbackName,
                    sourceId = sourceId,
                )
            }
                .onSuccess { detail ->
                    contentState = contentState.showArtist(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                    status = artistDetailLoadedStatus(detail)
                    if (sourceId != null) {
                        artistPopularTracksStatusByArtistId =
                            artistPopularTracksStatusByArtistId + (artistId.value to loadingPopularTracksStatus())
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                popularTracksService.popularTracks(
                                    sourceId = sourceId,
                                    artist = detail.artist,
                                    limit = ArtistDetailPopularTracksFetchLimit,
                                )
                            }.onSuccess { matches ->
                                val update = artistPopularTracksUpdate(matches, ArtistDetailPopularTracksDisplayLimit)
                                withContext(Dispatchers.Main) {
                                    artistPopularTracksByArtistId =
                                        artistPopularTracksByArtistId + (artistId.value to update.tracks)
                                    artistPopularTracksStatusByArtistId =
                                        artistPopularTracksStatusByArtistId + (artistId.value to update.status)
                                }
                            }.onFailure { error ->
                                withContext(Dispatchers.Main) {
                                    artistPopularTracksStatusByArtistId =
                                        artistPopularTracksStatusByArtistId + (artistId.value to popularTracksUnavailableStatus(error))
                                }
                            }
                        }
                    } else {
                        artistPopularTracksStatusByArtistId =
                            artistPopularTracksStatusByArtistId + (artistId.value to missingPopularTracksSourceStatus())
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
        if (
            artistSimilarArtistsByArtistId[artistId.value].orEmpty().isNotEmpty() ||
            artistSimilarArtistsStatusByArtistId[artistId.value] != null
        ) {
            artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId - artistId.value
            artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId - artistId.value
            return
        }
        artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (artistId.value to loadingSimilarArtistsStatus())
        artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId - artistId.value
    }
    scope.launch {
        with(state) {
            runCatching {
                similarArtistsService.similarArtists(
                    artistName = artistName,
                    limit = ArtistDetailSimilarArtistsFetchLimit,
                )
            }.onSuccess { artists ->
                val update = similarArtistsUpdate(artists, ArtistDetailSimilarArtistsDisplayLimit)
                artistSimilarArtistsByArtistId = artistSimilarArtistsByArtistId + (
                    artistId.value to update.artists
                    )
                artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (
                    artistId.value to update.status
                    )
            }.onFailure { error ->
                artistSimilarArtistsStatusByArtistId = artistSimilarArtistsStatusByArtistId + (
                    artistId.value to similarArtistsUnavailableStatus(error)
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
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    selectedAlbum: SharedMediaItemUi,
) {
    val activeProvider = state.provider ?: return
    val sourceId = state.activeSourceId
    val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    scope.launch {
        with(state) {
            status = albumDetailLoadingStatus(selectedAlbum.title)
            runCatching {
                loadAlbumDetails(
                    libraryIndexRepository = libraryIndexRepository,
                    providerResponseService = providerResponseService,
                    provider = activeProvider,
                    albumId = AlbumId(selectedAlbum.id),
                    fallbackTitle = selectedAlbum.title,
                    fallbackArtistName = selectedAlbum.subtitle,
                    sourceId = sourceId,
                )
            }.onSuccess { detail ->
                contentState = contentState.showAlbum(detail)
                tracks = detail.tracks
                nowPlayingOpen = false
                status = albumDetailLoadedStatus()
            }.onFailure { error ->
                status = albumDetailLoadErrorStatus(error)
            }
        }
    }
}

fun openAndroidNowPlayingAlbumDetails(
    scope: CoroutineScope,
    state: AndroidAppState,
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
) {
    val activeProvider = state.provider ?: return
    val albumId = state.nowPlaying?.albumId ?: return
    val sourceId = state.activeSourceId
    val fallbackTitle = state.nowPlaying?.albumTitle
    val fallbackArtistName = state.nowPlaying?.artistName
    val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    scope.launch {
        with(state) {
            runCatching {
                loadAlbumDetails(
                    libraryIndexRepository = libraryIndexRepository,
                    providerResponseService = providerResponseService,
                    provider = activeProvider,
                    albumId = albumId,
                    fallbackTitle = fallbackTitle,
                    fallbackArtistName = fallbackArtistName,
                    sourceId = sourceId,
                )
            }
                .onSuccess { detail ->
                    contentState = contentState.showAlbum(detail)
                    nowPlayingOpen = false
                    navigationState = navigationState.copy(route = NaviampRoute.Library)
                }
                .onFailure { error -> status = albumDetailLoadErrorStatus(error) }
        }
    }
}

fun loadAndroidArtistTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    action: (List<Track>) -> Unit,
) {
    val albums = state.artistDetail?.albums.orEmpty()
    val activeProvider = state.provider ?: return
    val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    scope.launch {
        state.status = "Loading artist tracks..."
        action(
            albums.flatMap { album ->
                runCatching { providerResponseService.album(activeProvider, album.id).tracks }.getOrDefault(emptyList())
            },
        )
    }
}

fun loadAndroidArtistAlbumTracks(
    scope: CoroutineScope,
    state: AndroidAppState,
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    selectedAlbum: SharedMediaItemUi,
    action: (List<Track>) -> Unit,
) {
    val activeProvider = state.provider ?: return
    val sourceId = state.activeSourceId
    val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    scope.launch {
        state.status = albumDetailLoadingStatus(selectedAlbum.title)
        runCatching {
            loadAlbumDetails(
                libraryIndexRepository = libraryIndexRepository,
                providerResponseService = providerResponseService,
                provider = activeProvider,
                albumId = AlbumId(selectedAlbum.id),
                fallbackTitle = selectedAlbum.title,
                fallbackArtistName = selectedAlbum.subtitle,
                sourceId = sourceId,
            ).tracks
        }
            .onSuccess(action)
            .onFailure { error -> state.status = albumDetailLoadErrorStatus(error) }
    }
}

fun startAndroidArtistAlbumRadio(
    scope: CoroutineScope,
    state: AndroidAppState,
    selectedAlbum: SharedMediaItemUi,
    startAlbumRadio: (Album, List<Track>) -> Unit,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    scope.launch {
        state.status = "Starting ${selectedAlbum.title} radio..."
        runCatching { providerResponseService.album(activeProvider, AlbumId(selectedAlbum.id)) }
            .onSuccess { detail -> startAlbumRadio(detail.album, detail.tracks) }
            .onFailure { error -> state.status = error.message ?: "Could not start album radio." }
    }
}

internal class AndroidArtistActionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val queueController: PlaybackQueueController,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    private val providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val similarArtistsService: SimilarArtistsService,
    private val activeQueue: () -> List<Track>,
    private val openArtistDetails: (ArtistId, String?) -> Unit,
    private val playTrack: (Track, List<Track>) -> Unit,
    private val playRadioTrack: (Track, List<Track>) -> Unit,
    private val startTrackRadio: (Track) -> Unit,
    private val startAlbumRadio: (Album, List<Track>) -> Unit,
    private val rememberRecentRadioStream: (RecentRadioStream) -> Unit,
) {
    fun findSimilarArtists(artistId: ArtistId, artistName: String) {
        findAndroidSimilarArtists(scope, state, similarArtistsService, artistId, artistName)
    }

    fun openExternalArtistUrl(url: String) {
        openAndroidExternalArtistUrl(context, state, url)
    }

    fun handleShellArtistRadio(detail: SharedArtistDetailUi) {
        val artistId = ArtistId(detail.artist.id)
        startAndroidArtistRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            artistId = artistId,
            artistTitle = detail.artist.title,
            artist = state.artistDetail?.artist ?: Artist(artistId, detail.artist.title),
            playTrack = playRadioTrack,
            providerResponseCacheRepository = providerResponseCacheRepository,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }

    fun loadArtistTracks(action: (List<Track>) -> Unit) {
        loadAndroidArtistTracks(scope, state, providerResponseCacheRepository, action)
    }

    fun handleShellArtistShuffle() {
        loadArtistTracks { artistTracks ->
            val queue = artistTracks.distinctBy { it.id }.shuffled()
            queue.firstOrNull()?.let { playTrack(it, queue) }
                ?: run { state.status = "No artist tracks found." }
        }
    }

    fun handleShellArtistPopularRadio(detail: SharedArtistDetailUi) {
        startAndroidPopularTracksRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            artistTitle = detail.artist.title,
            popularTracks = state.artistPopularTracksByArtistId[detail.artist.id].orEmpty(),
            playTrack = playRadioTrack,
            providerResponseCacheRepository = providerResponseCacheRepository,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }

    fun handleArtistPopularPlay(detail: SharedArtistDetailUi) {
        playAndroidArtistPopularTracks(state, detail.artist.id, playTrack)
    }

    fun handleArtistPopularTrackSelected(selectedTrack: SharedTrackRowUi) {
        startAndroidArtistPopularTrackRadio(state, selectedTrack.id, activeQueue(), startTrackRadio)
    }

    fun handleArtistPopularAddToQueue(detail: SharedArtistDetailUi) {
        appendAndroidArtistPopularTracksToQueue(state, queueController, detail.artist.id)
    }

    fun handleSimilarArtistSelected(similarArtist: SharedSimilarArtistUi) {
        val artistId = similarArtist.localArtistId
        if (artistId == null) {
            state.status = "Artist is not in your library."
        } else {
            openArtistDetails(ArtistId(artistId), similarArtist.title)
        }
    }

    fun loadArtistAlbumTracks(selectedAlbum: SharedMediaItemUi, action: (List<Track>) -> Unit) {
        loadAndroidArtistAlbumTracks(
            scope = scope,
            state = state,
            libraryIndexRepository = libraryIndexRepository,
            providerResponseCacheRepository = providerResponseCacheRepository,
            selectedAlbum = selectedAlbum,
            action = action,
        )
    }

    fun handleArtistAlbumRadio(selectedAlbum: SharedMediaItemUi) {
        startAndroidArtistAlbumRadio(
            scope = scope,
            state = state,
            selectedAlbum = selectedAlbum,
            startAlbumRadio = startAlbumRadio,
            providerResponseCacheRepository = providerResponseCacheRepository,
        )
    }
}

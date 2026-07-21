package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute
import app.naviamp.app.NaviampDetailBackCommand
import app.naviamp.app.NaviampDetailKind
import app.naviamp.app.NaviampNavigationController

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.media.ArtistDetailPopularTracksDisplayLimit
import app.naviamp.domain.media.ArtistDetailPopularTracksFetchLimit
import app.naviamp.domain.media.ArtistDetailSimilarArtistsDisplayLimit
import app.naviamp.domain.media.ArtistDetailSimilarArtistsFetchLimit
import app.naviamp.domain.media.ArtistDetailFlowCoordinator
import app.naviamp.domain.media.ArtistDetailFlowRequest
import app.naviamp.domain.media.connectedDetailStatusAsNull
import app.naviamp.domain.media.loadingPopularTracksStatus
import app.naviamp.domain.media.loadingSimilarArtistsStatus
import app.naviamp.domain.media.loadArtistPopularTracksUpdate
import app.naviamp.domain.media.loadSimilarArtistsUpdate
import app.naviamp.domain.media.trackArtist
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

class DesktopArtistController(
    private val scope: CoroutineScope,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val navigationController: NaviampNavigationController,
    private val setRoute: (NaviampRoute) -> Unit,
    private val popularTracksService: ArtistPopularTracksService,
    private val similarArtistsService: SimilarArtistsService,
) {
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    var selectedArtist by mutableStateOf<Artist?>(null)
        private set
    var selectedArtistDetails by mutableStateOf<ArtistDetails?>(null)
        private set
    var selectedArtistStatus by mutableStateOf<String?>(null)
        private set
    var selectedArtistPopularTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var selectedArtistPopularTracksStatus by mutableStateOf<String?>(null)
        private set
    var selectedArtistSimilarArtists by mutableStateOf<List<SimilarArtistMatch>>(emptyList())
        private set
    var selectedArtistSimilarArtistsStatus by mutableStateOf<String?>(null)
        private set
    val artistDetailBackRoute: NaviampRoute
        get() = navigationController.artistDetailBackRoute

    fun updateSelectedArtistDetails(details: ArtistDetails?) {
        selectedArtistDetails = details
    }

    fun updateSelectedArtistStatus(status: String?) {
        selectedArtistStatus = status
    }

    fun openExternalArtistUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
            }
        }.onFailure { error ->
            selectedArtistSimilarArtistsStatus = "Could not open external artist page: ${error.message ?: "unknown error"}"
        }
    }

    fun toggleSimilarArtists(artist: Artist) {
        if (selectedArtistSimilarArtists.isNotEmpty() || selectedArtistSimilarArtistsStatus != null) {
            selectedArtistSimilarArtists = emptyList()
            selectedArtistSimilarArtistsStatus = null
            return
        }
        findSimilarArtists(artist)
    }

    private fun findSimilarArtists(artist: Artist) {
        selectedArtistSimilarArtistsStatus = loadingSimilarArtistsStatus()
        selectedArtistSimilarArtists = emptyList()
        scope.launch {
            val update = withContext(Dispatchers.IO) {
                loadSimilarArtistsUpdate(
                    artist = artist,
                    fetchLimit = ArtistDetailSimilarArtistsFetchLimit,
                    displayLimit = ArtistDetailSimilarArtistsDisplayLimit,
                    loadSimilarArtists = similarArtistsService::similarArtists,
                )
            }
            if (selectedArtist?.id == artist.id) {
                selectedArtistSimilarArtists = update.artists
                selectedArtistSimilarArtistsStatus = update.status
            }
        }
    }

    fun openArtistDetails(
        artist: Artist,
        backRouteOverride: NaviampRoute? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        val activeProvider = provider() ?: return
        if (pushCurrentArtist) {
            navigationController.recordArtistDetailOpened(
                artist = artist,
                backRouteOverride = backRouteOverride,
            )
        }
        selectedArtist = artist
        selectedArtistDetails = null
        selectedArtistPopularTracks = emptyList()
        selectedArtistPopularTracksStatus = null
        selectedArtistSimilarArtists = emptyList()
        selectedArtistSimilarArtistsStatus = null
        setRoute(NaviampRoute.ArtistDetail)
        scope.launch {
            ArtistDetailFlowCoordinator(
                setStatus = { status -> selectedArtistStatus = connectedDetailStatusAsNull(status) },
                applyDetail = { details ->
                    selectedArtistDetails = details
                    navigationController.updateActiveArtist(details.artist)
                },
            ).load(
                request = ArtistDetailFlowRequest(
                    libraryIndexRepository = libraryIndexRepository,
                    providerResponseService = providerResponseService,
                    provider = activeProvider,
                    artistId = artist.id,
                    fallbackName = artist.name,
                    sourceId = sourceId(),
                ),
                afterLoaded = { details -> loadPopularTracks(artist, details) },
            )
        }
    }

    fun openTrackArtistDetails(
        track: Track,
        artistId: String? = null,
        artistName: String? = null,
        backRouteOverride: NaviampRoute = NaviampRoute.Player,
    ) {
        val selectedName = artistName?.takeIf { it.isNotBlank() }
        val artist = artistId?.takeIf { it.isNotBlank() }?.let { id ->
            Artist(
                id = ArtistId(id),
                name = selectedName ?: track.artistName,
            )
        }
        if (artist != null) {
            openArtistDetails(artist, backRouteOverride = backRouteOverride)
            return
        }
        if (selectedName != null) {
            val localMatch = sourceId()?.let { id ->
                libraryIndexRepository.searchLibrary(id, selectedName, limit = 20).artists
                    .firstOrNull { candidate -> candidate.name.equals(selectedName, ignoreCase = true) }
            }
            if (localMatch != null) {
                openArtistDetails(localMatch, backRouteOverride = backRouteOverride)
                return
            }
            scope.launch {
                val remoteMatch = runCatching {
                    withContext(Dispatchers.IO) {
                        provider()?.search(selectedName, limit = 20)?.artists
                            ?.firstOrNull { candidate -> candidate.name.equals(selectedName, ignoreCase = true) }
                    }
                }.getOrNull()
                if (remoteMatch != null) {
                    openArtistDetails(remoteMatch, backRouteOverride = backRouteOverride)
                } else {
                    selectedArtistStatus = "Could not find artist $selectedName."
                }
            }
            return
        }
        openArtistDetails(trackArtist(track) ?: return, backRouteOverride = backRouteOverride)
    }

    fun closeArtistDetails() {
        when (val command = navigationController.closeActiveDetail(NaviampDetailKind.Artist)) {
            is NaviampDetailBackCommand.OpenArtist -> openArtistDetails(
                artist = command.artist,
                pushCurrentArtist = false,
            )
            is NaviampDetailBackCommand.Navigate -> setRoute(command.route)
        }
    }

    private suspend fun loadPopularTracks(artist: Artist, details: ArtistDetails) {
        selectedArtistPopularTracksStatus = loadingPopularTracksStatus()
        val update = loadArtistPopularTracksUpdate(
            sourceId = sourceId(),
            artist = details.artist,
            fetchLimit = ArtistDetailPopularTracksFetchLimit,
            displayLimit = ArtistDetailPopularTracksDisplayLimit,
            loadPopularTracks = popularTracksService::popularTracks,
        )
        if (selectedArtist?.id == artist.id) {
            selectedArtistPopularTracks = update.tracks
            selectedArtistPopularTracksStatus = update.status
        }
    }
}

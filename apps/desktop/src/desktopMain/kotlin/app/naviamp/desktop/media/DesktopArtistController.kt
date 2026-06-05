package app.naviamp.desktop

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.media.ArtistDetailPopularTracksDisplayLimit
import app.naviamp.domain.media.ArtistDetailPopularTracksFetchLimit
import app.naviamp.domain.media.ArtistDetailSimilarArtistsDisplayLimit
import app.naviamp.domain.media.ArtistDetailSimilarArtistsFetchLimit
import app.naviamp.domain.media.artistPopularTracksUpdate
import app.naviamp.domain.media.loadingPopularTracksStatus
import app.naviamp.domain.media.loadingSimilarArtistsStatus
import app.naviamp.domain.media.missingPopularTracksSourceStatus
import app.naviamp.domain.media.popularTracksUnavailableStatus
import app.naviamp.domain.media.similarArtistsUnavailableStatus
import app.naviamp.domain.media.similarArtistsUpdate
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
    private val currentRoute: () -> DesktopAppRoute,
    private val lastContentRoute: () -> DesktopAppRoute,
    private val setRoute: (DesktopAppRoute) -> Unit,
    private val selectedArtist: () -> Artist?,
    private val setSelectedArtist: (Artist?) -> Unit,
    private val setSelectedArtistDetails: (ArtistDetails?) -> Unit,
    private val setSelectedArtistStatus: (String?) -> Unit,
    private val setSelectedArtistPopularTracks: (List<Track>) -> Unit,
    private val setSelectedArtistPopularTracksStatus: (String?) -> Unit,
    private val setSelectedArtistSimilarArtists: (List<SimilarArtistMatch>) -> Unit,
    private val setSelectedArtistSimilarArtistsStatus: (String?) -> Unit,
    private val artistDetailBackRoute: () -> DesktopAppRoute,
    private val setArtistDetailBackRoute: (DesktopAppRoute) -> Unit,
    private val artistDetailBackStack: () -> List<Artist>,
    private val setArtistDetailBackStack: (List<Artist>) -> Unit,
    private val popularTracksService: ArtistPopularTracksService,
    private val similarArtistsService: SimilarArtistsService,
) {
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    fun openExternalArtistUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
            }
        }.onFailure { error ->
            setSelectedArtistSimilarArtistsStatus("Could not open external artist page: ${error.message ?: "unknown error"}")
        }
    }

    fun findSimilarArtists(artist: Artist) {
        setSelectedArtistSimilarArtistsStatus(loadingSimilarArtistsStatus())
        setSelectedArtistSimilarArtists(emptyList())
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    similarArtistsService.similarArtists(
                        artistName = artist.name,
                        limit = ArtistDetailSimilarArtistsFetchLimit,
                    )
                }
            }.onSuccess { artists ->
                if (selectedArtist()?.id == artist.id) {
                    val update = similarArtistsUpdate(artists, ArtistDetailSimilarArtistsDisplayLimit)
                    setSelectedArtistSimilarArtists(update.artists)
                    setSelectedArtistSimilarArtistsStatus(update.status)
                }
            }.onFailure { error ->
                if (selectedArtist()?.id == artist.id) {
                    setSelectedArtistSimilarArtistsStatus(similarArtistsUnavailableStatus(error))
                }
            }
        }
    }

    fun openArtistDetails(
        artist: Artist,
        backRouteOverride: DesktopAppRoute? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        val activeProvider = provider() ?: return
        val navigation = artistDetailNavigation(
            artist = artist,
            currentArtist = selectedArtist(),
            currentRoute = currentRoute(),
            currentBackStack = artistDetailBackStack(),
            currentBackRoute = artistDetailBackRoute(),
            lastContentRoute = lastContentRoute(),
            backRouteOverride = backRouteOverride,
            pushCurrentArtist = pushCurrentArtist,
        )
        setArtistDetailBackStack(navigation.backStack)
        setArtistDetailBackRoute(navigation.backRoute)
        setSelectedArtist(artist)
        setSelectedArtistDetails(null)
        setSelectedArtistPopularTracks(emptyList())
        setSelectedArtistPopularTracksStatus(null)
        setSelectedArtistSimilarArtists(emptyList())
        setSelectedArtistSimilarArtistsStatus(null)
        setSelectedArtistStatus("Loading...")
        setRoute(DesktopAppRoute.ArtistDetail)
        scope.launch {
            try {
                val details = loadArtistDetails(libraryIndexRepository, providerResponseService, activeProvider, artist, sourceId())
                setSelectedArtistDetails(details)
                setSelectedArtistStatus(null)
                loadPopularTracks(artist, details)
            } catch (exception: Exception) {
                setSelectedArtistStatus(artistLoadErrorStatus(exception))
            }
        }
    }

    fun openTrackArtistDetails(track: Track, backRouteOverride: DesktopAppRoute = DesktopAppRoute.Player) {
        openArtistDetails(trackArtist(track) ?: return, backRouteOverride = backRouteOverride)
    }

    fun closeArtistDetails() {
        val previousArtist = artistDetailBackStack().lastOrNull()
        if (previousArtist != null) {
            setArtistDetailBackStack(artistDetailBackStack().dropLast(1))
            openArtistDetails(
                artist = previousArtist,
                backRouteOverride = artistDetailBackRoute(),
                pushCurrentArtist = false,
            )
        } else {
            setRoute(artistDetailBackRoute())
        }
    }

    private suspend fun loadPopularTracks(artist: Artist, details: ArtistDetails) {
        val activeSourceId = sourceId()
        if (activeSourceId == null) {
            setSelectedArtistPopularTracksStatus(missingPopularTracksSourceStatus())
            return
        }
        setSelectedArtistPopularTracksStatus(loadingPopularTracksStatus())
        runCatching {
            popularTracksService.popularTracks(
                sourceId = activeSourceId,
                artist = details.artist,
                limit = ArtistDetailPopularTracksFetchLimit,
            )
        }.onSuccess { matches ->
            if (selectedArtist()?.id == artist.id) {
                val update = artistPopularTracksUpdate(matches, ArtistDetailPopularTracksDisplayLimit)
                setSelectedArtistPopularTracks(update.tracks)
                setSelectedArtistPopularTracksStatus(update.status)
            }
        }.onFailure { error ->
            if (selectedArtist()?.id == artist.id) {
                setSelectedArtistPopularTracksStatus(popularTracksUnavailableStatus(error))
            }
        }
    }
}

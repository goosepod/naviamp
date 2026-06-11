package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.media.albumDetailLoadErrorStatus
import app.naviamp.domain.media.loadAlbumDetails
import app.naviamp.domain.media.trackAlbum
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DesktopAlbumController(
    private val scope: CoroutineScope,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val currentRoute: () -> DesktopAppRoute,
    private val lastContentRoute: () -> DesktopAppRoute,
    private val albumDetailBackRoute: () -> DesktopAppRoute,
    private val setAlbumDetailBackRoute: (DesktopAppRoute) -> Unit,
    private val setRoute: (DesktopAppRoute) -> Unit,
    private val setSelectedAlbum: (Album?) -> Unit,
    private val setSelectedAlbumDetails: (AlbumDetails?) -> Unit,
    private val setSelectedAlbumStatus: (String?) -> Unit,
) {
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    fun openAlbumDetails(album: Album, backRouteOverride: DesktopAppRoute? = null) {
        val activeProvider = provider() ?: return
        setAlbumDetailBackRoute(
            resolveAlbumDetailBackRoute(
                currentRoute = currentRoute(),
                currentBackRoute = albumDetailBackRoute(),
                lastContentRoute = lastContentRoute(),
                backRouteOverride = backRouteOverride,
            ),
        )
        setSelectedAlbum(album)
        setSelectedAlbumDetails(null)
        setSelectedAlbumStatus("Loading...")
        setRoute(DesktopAppRoute.AlbumDetail)
        scope.launch {
            try {
                setSelectedAlbumDetails(loadAlbumDetails(libraryIndexRepository, providerResponseService, activeProvider, album, sourceId()))
                setSelectedAlbumStatus(null)
            } catch (exception: Exception) {
                setSelectedAlbumStatus(albumDetailLoadErrorStatus(exception))
            }
        }
    }

    fun openTrackAlbumDetails(track: Track) {
        openAlbumDetails(trackAlbum(track) ?: return, backRouteOverride = DesktopAppRoute.Player)
    }
}

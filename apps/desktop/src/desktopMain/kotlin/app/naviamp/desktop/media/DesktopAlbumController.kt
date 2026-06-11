package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private val setRoute: (DesktopAppRoute) -> Unit,
) {
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    var selectedAlbum by mutableStateOf<Album?>(null)
        private set
    var selectedAlbumDetails by mutableStateOf<AlbumDetails?>(null)
        private set
    var selectedAlbumStatus by mutableStateOf<String?>(null)
        private set
    var albumDetailBackRoute by mutableStateOf(DesktopAppRoute.Home)
        private set

    fun updateSelectedAlbumDetails(details: AlbumDetails?) {
        selectedAlbumDetails = details
    }

    fun openAlbumDetails(album: Album, backRouteOverride: DesktopAppRoute? = null) {
        val activeProvider = provider() ?: return
        albumDetailBackRoute =
            resolveAlbumDetailBackRoute(
                currentRoute = currentRoute(),
                currentBackRoute = albumDetailBackRoute,
                lastContentRoute = lastContentRoute(),
                backRouteOverride = backRouteOverride,
            )
        selectedAlbum = album
        selectedAlbumDetails = null
        selectedAlbumStatus = "Loading..."
        setRoute(DesktopAppRoute.AlbumDetail)
        scope.launch {
            try {
                selectedAlbumDetails = loadAlbumDetails(libraryIndexRepository, providerResponseService, activeProvider, album, sourceId())
                selectedAlbumStatus = null
            } catch (exception: Exception) {
                selectedAlbumStatus = albumDetailLoadErrorStatus(exception)
            }
        }
    }

    fun openTrackAlbumDetails(track: Track) {
        openAlbumDetails(trackAlbum(track) ?: return, backRouteOverride = DesktopAppRoute.Player)
    }
}

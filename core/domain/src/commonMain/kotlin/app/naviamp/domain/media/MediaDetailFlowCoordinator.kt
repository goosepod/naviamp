package app.naviamp.domain.media

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider

data class ArtistDetailFlowRequest(
    val libraryIndexRepository: LocalLibraryIndexRepository,
    val providerResponseService: ProviderResponseService,
    val provider: MediaProvider,
    val artistId: ArtistId,
    val fallbackName: String?,
    val sourceId: String?,
)

data class AlbumDetailFlowRequest(
    val libraryIndexRepository: LocalLibraryIndexRepository,
    val providerResponseService: ProviderResponseService,
    val provider: MediaProvider,
    val albumId: AlbumId,
    val fallbackTitle: String?,
    val fallbackArtistName: String?,
    val sourceId: String?,
)

class ArtistDetailFlowCoordinator(
    private val setStatus: (String?) -> Unit,
    private val applyDetail: (ArtistDetails) -> Unit,
    private val loadedStatus: (ArtistDetails) -> String? = ::artistDetailLoadedStatus,
) {
    suspend fun load(
        request: ArtistDetailFlowRequest,
        loadDetails: suspend (ArtistDetailFlowRequest) -> ArtistDetails = ::loadArtistDetailFlow,
        afterLoaded: suspend (ArtistDetails) -> Unit = {},
    ) {
        setStatus(artistDetailLoadingStatus(request.fallbackName))
        runCatching { loadDetails(request) }
            .onSuccess { detail ->
                applyDetail(detail)
                setStatus(loadedStatus(detail))
                afterLoaded(detail)
            }
            .onFailure { error ->
                setStatus(artistDetailLoadErrorStatus(error))
            }
    }
}

class AlbumDetailFlowCoordinator(
    private val setStatus: (String?) -> Unit,
    private val applyDetail: (AlbumDetails) -> Unit,
    private val loadedStatus: (AlbumDetails) -> String? = { albumDetailLoadedStatus() },
) {
    suspend fun load(
        request: AlbumDetailFlowRequest,
        loadDetails: suspend (AlbumDetailFlowRequest) -> AlbumDetails = ::loadAlbumDetailFlow,
    ) {
        setStatus(albumDetailLoadingStatus(request.fallbackTitle))
        runCatching { loadDetails(request) }
            .onSuccess { detail ->
                applyDetail(detail)
                setStatus(loadedStatus(detail))
            }
            .onFailure { error ->
                setStatus(albumDetailLoadErrorStatus(error))
            }
    }
}

suspend fun loadArtistDetailFlow(request: ArtistDetailFlowRequest): ArtistDetails =
    loadArtistDetails(
        libraryIndexRepository = request.libraryIndexRepository,
        providerResponseService = request.providerResponseService,
        provider = request.provider,
        artistId = request.artistId,
        fallbackName = request.fallbackName,
        sourceId = request.sourceId,
    )

suspend fun loadAlbumDetailFlow(request: AlbumDetailFlowRequest): AlbumDetails =
    loadAlbumDetails(
        libraryIndexRepository = request.libraryIndexRepository,
        providerResponseService = request.providerResponseService,
        provider = request.provider,
        albumId = request.albumId,
        fallbackTitle = request.fallbackTitle,
        fallbackArtistName = request.fallbackArtistName,
        sourceId = request.sourceId,
    )

fun connectedDetailStatusAsNull(status: String?): String? =
    status.takeUnless { it == albumDetailLoadedStatus() }

package app.naviamp.domain.home

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.RecentRadioStream

data class HomeContentLoadRequest(
    val provider: MediaProvider,
    val providerResponseService: ProviderResponseService? = null,
    val libraryRepository: HomeLibraryRepository? = null,
    val sourceId: String? = null,
    val date: HomeDate,
    val recentRadioStreams: List<RecentRadioStream> = emptyList(),
    val recentInternetRadioStations: List<InternetRadioStation> = emptyList(),
    val artistLimit: Int = HomeDefaultArtistLimit,
)

class HomeContentCoordinator(
    private val setHomeContent: (HomeContent) -> Unit,
    private val setHomeStatus: (String?) -> Unit,
) {
    suspend fun load(
        request: HomeContentLoadRequest,
        loadContent: suspend (HomeContentLoadRequest) -> HomeContent = ::loadHomeContent,
    ) {
        setHomeStatus(HomeLoadingStatus)
        runCatching { loadContent(request) }
            .onSuccess { content ->
                setHomeContent(content)
                setHomeStatus(null)
            }
            .onFailure { exception ->
                setHomeStatus(homeLoadFailureStatus(exception))
            }
    }
}

suspend fun loadHomeContent(request: HomeContentLoadRequest): HomeContent =
    HomeService(
        provider = request.provider,
        providerResponseService = request.providerResponseService,
        libraryRepository = request.libraryRepository,
        sourceId = request.sourceId,
        date = request.date,
    ).load(
        recentRadioStreams = request.recentRadioStreams,
        recentInternetRadioStations = request.recentInternetRadioStations,
        artistLimit = request.artistLimit,
    )

fun homeLoadFailureStatus(exception: Throwable): String =
    exception.message ?: HomeLoadFailureFallbackStatus

const val HomeLoadingStatus = "Loading home..."
const val HomeLoadFailureFallbackStatus = "Could not load home."

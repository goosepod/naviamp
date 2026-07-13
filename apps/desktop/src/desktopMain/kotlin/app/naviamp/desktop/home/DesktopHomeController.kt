package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeContentCoordinator
import app.naviamp.domain.home.HomeContentLoadRequest
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.home.loadHomeContent as loadSharedHomeContent
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.RecentRadioStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class DesktopHomeController(
    private val scope: CoroutineScope,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val homeLibraryRepository: HomeLibraryRepository,
    private val sourceId: () -> String?,
    private val recentRadioStreams: () -> List<RecentRadioStream>,
    private val recentInternetRadioStations: () -> List<InternetRadioStation>,
    private val setHomeContent: (HomeContent) -> Unit,
    private val setHomeStatus: (String?) -> Unit,
) {
    var refreshing by mutableStateOf(false)
        private set

    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    private val coordinator = HomeContentCoordinator(
        setHomeContent = setHomeContent,
        setHomeStatus = setHomeStatus,
    )

    fun loadHomeContent(provider: MediaProvider) {
        val activeSourceId = sourceId()
        scope.launch {
            refreshing = true
            try {
                coordinator.load(
                    request = HomeContentLoadRequest(
                        provider = provider,
                        providerResponseService = providerResponseService,
                        libraryRepository = homeLibraryRepository,
                        sourceId = activeSourceId,
                        date = currentHomeDate(),
                        recentRadioStreams = recentRadioStreams(),
                        recentInternetRadioStations = recentInternetRadioStations(),
                    ),
                    loadContent = { request ->
                        withContext(Dispatchers.IO) {
                            loadSharedHomeContent(request)
                        }
                    },
                )
            } finally {
                refreshing = false
            }
        }
    }

    private fun currentHomeDate(): HomeDate {
        val today = LocalDate.now()
        return HomeDate(year = today.year, dayOfYear = today.dayOfYear)
    }
}

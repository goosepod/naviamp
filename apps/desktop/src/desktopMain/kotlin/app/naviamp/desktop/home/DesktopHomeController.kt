package app.naviamp.desktop

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.home.HomeService
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
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    fun loadHomeContent(provider: MediaProvider) {
        val activeSourceId = sourceId()
        setHomeStatus("Loading home...")
        scope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    val today = LocalDate.now()
                    HomeService(
                        provider = provider,
                        providerResponseService = providerResponseService,
                        libraryRepository = homeLibraryRepository,
                        sourceId = activeSourceId,
                        date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
                    ).load(
                        recentRadioStreams = recentRadioStreams(),
                        recentInternetRadioStations = recentInternetRadioStations(),
                    )
                }
                setHomeContent(content)
                setHomeStatus(null)
            } catch (exception: Exception) {
                setHomeStatus(exception.message ?: "Could not load home.")
            }
        }
    }
}

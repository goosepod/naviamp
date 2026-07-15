package app.naviamp.android

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeContentLoadRequest
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.home.loadHomeContent
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.provider.navidrome.NavidromeProvider
import java.time.LocalDate

suspend fun loadBrowseState(
    provider: NavidromeProvider,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    @Suppress("UNUSED_PARAMETER") libraryRepository: HomeLibraryRepository? = null,
    @Suppress("UNUSED_PARAMETER") sourceId: String? = null,
    recentRadioStreams: List<RecentRadioStream> = emptyList(),
    recentInternetRadioStations: List<InternetRadioStation> = emptyList(),
): HomeContent {
    val today = LocalDate.now()
    return loadHomeContent(
        HomeContentLoadRequest(
            provider = provider,
            providerResponseService = providerResponseCacheRepository?.let(::ProviderResponseService),
            libraryRepository = null,
            sourceId = null,
            date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
            recentRadioStreams = recentRadioStreams,
            recentInternetRadioStations = recentInternetRadioStations,
            artistLimit = AndroidHomeArtistLimit,
        ),
    )
}

private const val AndroidHomeArtistLimit = 50

package app.naviamp.android

import app.naviamp.domain.Artist
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeContentLoadRequest
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.loadHomeContent
import app.naviamp.domain.library.LibrarySyncProgress
import app.naviamp.domain.library.LibrarySyncProgressPhase
import app.naviamp.domain.library.syncLibraryIndexAndMarkScanChecked
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.provider.navidrome.NavidromeProvider
import java.time.LocalDate

suspend fun loadBrowseState(
    provider: NavidromeProvider,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    recentRadioStreams: List<RecentRadioStream> = emptyList(),
    recentInternetRadioStations: List<InternetRadioStation> = emptyList(),
): HomeContent {
    val today = LocalDate.now()
    return loadHomeContent(
        HomeContentLoadRequest(
            provider = provider,
            providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) },
            date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
            recentRadioStreams = recentRadioStreams,
            recentInternetRadioStations = recentInternetRadioStations,
            artistLimit = AndroidLibraryArtistLimit,
        ),
    )
}

suspend fun syncAndroidLibrary(
    sourceId: String,
    provider: MediaProvider,
    libraryIndexRepository: LocalLibraryIndexRepository,
    onProgress: suspend (AndroidLibrarySyncProgress) -> Unit = {},
) {
    syncLibraryIndexAndMarkScanChecked(
        sourceId = sourceId,
        provider = provider,
        libraryIndexRepository = libraryIndexRepository,
        artistLimit = AndroidLibraryArtistLimit,
        albumPageSize = AndroidLibraryAlbumPageSize,
        includeAlbumTracks = false,
    ) { progress ->
        onProgress(progress.toAndroidLibrarySyncProgress())
    }
}

data class AndroidLibrarySyncProgress(
    val label: String,
    val artists: List<Artist>? = null,
)

private fun LibrarySyncProgress.toAndroidLibrarySyncProgress(): AndroidLibrarySyncProgress =
    AndroidLibrarySyncProgress(
        label = when (phase) {
            LibrarySyncProgressPhase.LoadingArtists -> "Loading library artists..."
            LibrarySyncProgressPhase.IndexedArtists -> "Indexed $artistCount artists."
            LibrarySyncProgressPhase.LoadingAlbums -> "Loading library albums ($albumCount)..."
            LibrarySyncProgressPhase.IndexedAlbums -> "Indexed $albumCount albums."
            LibrarySyncProgressPhase.LoadingTracks -> "Loading library tracks..."
            LibrarySyncProgressPhase.IndexedLibrary -> "Library indexed: $artistCount artists, $albumCount albums."
        },
        artists = artists,
    )

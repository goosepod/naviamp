package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.provider.navidrome.NavidromeProvider
import java.time.LocalDate

suspend fun loadBrowseState(
    provider: NavidromeProvider,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
    recentRadioStreams: List<app.naviamp.domain.settings.RecentRadioStream> = emptyList(),
    recentInternetRadioStations: List<app.naviamp.domain.InternetRadioStation> = emptyList(),
): HomeContent {
    val today = LocalDate.now()
    val home = HomeService(
        provider = provider,
        providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) },
        date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
    ).load(
        recentRadioStreams = recentRadioStreams,
        recentInternetRadioStations = recentInternetRadioStations,
        artistLimit = AndroidLibraryArtistLimit,
    )
    return home
}

suspend fun syncAndroidLibrary(
    sourceId: String,
    provider: MediaProvider,
    libraryIndexRepository: LocalLibraryIndexRepository,
    onProgress: suspend (AndroidLibrarySyncProgress) -> Unit = {},
) {
    libraryIndexRepository.markLibrarySyncStarted(sourceId)
    onProgress(AndroidLibrarySyncProgress(label = "Loading library artists..."))
    val artists = provider.artists(limit = AndroidLibraryArtistLimit)
    libraryIndexRepository.upsertLibraryArtists(sourceId, artists)
    onProgress(AndroidLibrarySyncProgress(label = "Indexed ${artists.size} artists.", artists = artists))

    val albums = mutableListOf<Album>()
    var offset = 0
    while (true) {
        onProgress(AndroidLibrarySyncProgress(label = "Loading library albums (${albums.size})..."))
        val page = provider.albums(limit = AndroidLibraryAlbumPageSize, offset = offset)
        if (page.isEmpty()) break
        albums += page
        libraryIndexRepository.upsertLibraryAlbums(sourceId, page)
        onProgress(AndroidLibrarySyncProgress(label = "Indexed ${albums.size} albums."))
        if (page.size < AndroidLibraryAlbumPageSize) break
        offset += AndroidLibraryAlbumPageSize
    }

    libraryIndexRepository.markLibrarySyncCompleted(sourceId)
    onProgress(AndroidLibrarySyncProgress(label = "Library indexed: ${artists.size} artists, ${albums.size} albums."))
}

data class AndroidLibrarySyncProgress(
    val label: String,
    val artists: List<Artist>? = null,
)

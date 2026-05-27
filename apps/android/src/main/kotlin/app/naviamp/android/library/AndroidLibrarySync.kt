package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.provider.navidrome.NavidromeProvider
import java.time.LocalDate

suspend fun loadBrowseState(provider: NavidromeProvider): HomeContent {
    val today = LocalDate.now()
    val home = HomeService(
        provider = provider,
        date = HomeDate(year = today.year, dayOfYear = today.dayOfYear),
    ).load()
    val artists = runCatching { provider.artists(limit = AndroidLibraryArtistLimit) }
        .getOrDefault(home.artists)
    return home.copy(artists = artists)
}

suspend fun syncAndroidLibrary(
    sourceId: String,
    provider: MediaProvider,
    storage: AndroidStorage,
    onProgress: suspend (AndroidLibrarySyncProgress) -> Unit = {},
) {
    storage.markLibrarySyncStarted(sourceId)
    onProgress(AndroidLibrarySyncProgress(label = "Loading library artists..."))
    val artists = provider.artists(limit = AndroidLibraryArtistLimit)
    storage.upsertLibraryArtists(sourceId, artists)
    onProgress(AndroidLibrarySyncProgress(label = "Indexed ${artists.size} artists.", artists = artists))

    val albums = mutableListOf<Album>()
    var offset = 0
    while (true) {
        onProgress(AndroidLibrarySyncProgress(label = "Loading library albums (${albums.size})..."))
        val page = provider.albums(limit = AndroidLibraryAlbumPageSize, offset = offset)
        if (page.isEmpty()) break
        albums += page
        storage.upsertLibraryAlbums(sourceId, page)
        onProgress(AndroidLibrarySyncProgress(label = "Indexed ${albums.size} albums."))
        if (page.size < AndroidLibraryAlbumPageSize) break
        offset += AndroidLibraryAlbumPageSize
    }

    storage.markLibrarySyncCompleted(sourceId)
    onProgress(AndroidLibrarySyncProgress(label = "Library indexed: ${artists.size} artists, ${albums.size} albums."))
}

data class AndroidLibrarySyncProgress(
    val label: String,
    val artists: List<Artist>? = null,
)

data class AndroidLibraryFreshness(
    val signature: String?,
    val previousSignature: String?,
    val scanning: Boolean,
)

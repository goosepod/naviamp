package app.naviamp.domain.library

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MaximumMediaPageSize

const val LibraryGenreInventoryLimit = 5_000

enum class LibrarySyncProgressPhase {
    LoadingArtists,
    IndexedArtists,
    LoadingAlbums,
    IndexedAlbums,
    LoadingTracks,
    IndexedLibrary,
}

data class LibrarySyncProgress(
    val phase: LibrarySyncProgressPhase,
    val artistCount: Int = 0,
    val albumCount: Int = 0,
    val trackCount: Int = 0,
    val completed: Int = 0,
    val total: Int? = null,
    val artists: List<Artist>? = null,
)

data class LibrarySyncResult(
    val artistCount: Int,
    val albumCount: Int,
    val trackCount: Int,
)

suspend fun syncLibraryIndex(
    sourceId: String,
    provider: MediaProvider,
    libraryIndexRepository: LocalLibraryIndexRepository,
    artistLimit: Int,
    albumPageSize: Int,
    includeAlbumTracks: Boolean = false,
    providerResponseService: ProviderResponseService? = null,
    onProgress: suspend (LibrarySyncProgress) -> Unit = {},
): LibrarySyncResult {
    libraryIndexRepository.markLibrarySyncStarted(sourceId)
    onProgress(LibrarySyncProgress(LibrarySyncProgressPhase.LoadingArtists))
    val artists = provider.artists(limit = artistLimit)
    libraryIndexRepository.upsertLibraryArtists(sourceId, artists)
    onProgress(
        LibrarySyncProgress(
            phase = LibrarySyncProgressPhase.IndexedArtists,
            artistCount = artists.size,
            completed = artists.size,
            artists = artists,
        ),
    )

    val albums = mutableListOf<Album>()
    var request: MediaPageRequest? = MediaPageRequest(limit = albumPageSize.coerceIn(1, MaximumMediaPageSize))
    while (request != null) {
        onProgress(
            LibrarySyncProgress(
                phase = LibrarySyncProgressPhase.LoadingAlbums,
                artistCount = artists.size,
                albumCount = albums.size,
                completed = albums.size,
            ),
        )
        val albumPage = provider.albumsPage(request)
        val page = albumPage.items
        if (page.isEmpty()) break
        albums += page
        libraryIndexRepository.upsertLibraryAlbums(sourceId, page)
        onProgress(
            LibrarySyncProgress(
                phase = LibrarySyncProgressPhase.IndexedAlbums,
                artistCount = artists.size,
                albumCount = albums.size,
                completed = albums.size,
            ),
        )
        request = albumPage.nextRequest
    }

    var trackCount = 0
    if (includeAlbumTracks) {
        var tracksPage = provider.libraryTracksPage(MediaPageRequest(limit = MaximumMediaPageSize))
        if (tracksPage != null) {
            val seen = mutableSetOf<app.naviamp.domain.TrackId>()
            while (tracksPage != null) {
                val tracks = tracksPage.items.filter { seen.add(it.id) }
                libraryIndexRepository.upsertLibraryTracks(sourceId, tracks)
                trackCount += tracks.size
                onProgress(LibrarySyncProgress(LibrarySyncProgressPhase.LoadingTracks,
                    artists.size, albums.size, trackCount, completed = trackCount))
                val next = tracksPage.nextRequest
                if (tracksPage.items.isEmpty() || next == null) break
                tracksPage = checkNotNull(provider.libraryTracksPage(next))
            }
        } else {
            albums.forEachIndexed { index, album ->
                onProgress(
                    LibrarySyncProgress(
                        phase = LibrarySyncProgressPhase.LoadingTracks,
                        artistCount = artists.size,
                        albumCount = albums.size,
                        trackCount = trackCount,
                        completed = index,
                        total = albums.size,
                    ),
                )
                val details = providerResponseService?.album(provider, album.id) ?: provider.album(album.id)
                libraryIndexRepository.upsertLibraryAlbums(sourceId, listOf(details.album))
                libraryIndexRepository.upsertLibraryTracks(sourceId, details.tracks)
                trackCount += details.tracks.size
            }
        }
    }

    // Genre discovery supplements the primary index. A provider-specific genre endpoint failure
    // must not leave an otherwise successful artist/album import marked as incomplete.
    runCatching { refreshLibraryGenreInventory(sourceId, provider, libraryIndexRepository) }
        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }

    libraryIndexRepository.markLibrarySyncCompleted(sourceId)
    onProgress(
        LibrarySyncProgress(
            phase = LibrarySyncProgressPhase.IndexedLibrary,
            artistCount = artists.size,
            albumCount = albums.size,
            trackCount = trackCount,
            completed = artists.size + albums.size + trackCount,
        ),
    )
    return LibrarySyncResult(
        artistCount = artists.size,
        albumCount = albums.size,
        trackCount = trackCount,
    )
}

suspend fun refreshLibraryGenreInventory(
    sourceId: String,
    provider: MediaProvider,
    libraryIndexRepository: LocalLibraryIndexRepository,
    limit: Int = LibraryGenreInventoryLimit,
) {
    libraryIndexRepository.replaceLibraryGenreInventory(
        sourceId = sourceId,
        genres = provider.genres(limit),
    )
}

suspend fun syncLibraryIndexAndMarkScanChecked(
    sourceId: String,
    provider: MediaProvider,
    libraryIndexRepository: LocalLibraryIndexRepository,
    artistLimit: Int,
    albumPageSize: Int,
    includeAlbumTracks: Boolean = false,
    providerResponseService: ProviderResponseService? = null,
    onProgress: suspend (LibrarySyncProgress) -> Unit = {},
): LibrarySyncResult {
    val result = syncLibraryIndex(
        sourceId = sourceId,
        provider = provider,
        libraryIndexRepository = libraryIndexRepository,
        artistLimit = artistLimit,
        albumPageSize = albumPageSize,
        includeAlbumTracks = includeAlbumTracks,
        providerResponseService = providerResponseService,
        onProgress = onProgress,
    )
    provider.libraryScanStatus()?.signature?.let { signature ->
        libraryIndexRepository.markLibraryScanChecked(sourceId, signature)
    }
    return result
}

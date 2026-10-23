package app.naviamp.domain.library

import kotlinx.coroutines.ensureActive
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

/** Preview needs track metadata, not a refresh of artists, genres or scan state. */
suspend fun syncSmartPlaylistPreviewTracks(
    sourceId: String,
    provider: MediaProvider,
    repository: LocalLibraryIndexRepository,
    onProgress: suspend (LibrarySyncProgress) -> Unit = {},
): Int {
    val seen = mutableSetOf<app.naviamp.domain.TrackId>()
    suspend fun report(phase: LibrarySyncProgressPhase = LibrarySyncProgressPhase.LoadingTracks,
                       albums: Int = 0, total: Int? = null) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        onProgress(LibrarySyncProgress(phase, albumCount = albums, trackCount = seen.size,
            completed = seen.size, total = total))
    }
    suspend fun store(tracks: List<app.naviamp.domain.Track>) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        repository.upsertLibraryTracks(sourceId, tracks.filter { seen.add(it.id) })
    }
    report()
    var page = provider.libraryTracksPage(MediaPageRequest(limit = MaximumMediaPageSize))
    if (page != null) {
        while (page != null) {
            val current = page
            store(current.items)
            report(total = current.totalItemCount)
            val next = current.nextRequest
            if (current.items.isEmpty() || next == null) {
                check(current.totalItemCount == null || seen.size >= current.totalItemCount) { "Incomplete track enumeration" }
                check(!current.hasMore) { "Incomplete track enumeration" }
                break
            }
            page = checkNotNull(provider.libraryTracksPage(next))
        }
    } else {
        val albums = linkedMapOf<app.naviamp.domain.AlbumId, Album>()
        var request: MediaPageRequest? = MediaPageRequest(limit = MaximumMediaPageSize)
        report(LibrarySyncProgressPhase.LoadingAlbums)
        while (request != null) {
            val current = provider.albumsPage(request)
            current.items.forEach { albums[it.id] = it }
            report(LibrarySyncProgressPhase.LoadingAlbums, albums.size)
            if (current.items.isEmpty() || current.nextRequest == null) {
                check(current.totalItemCount == null || albums.size >= current.totalItemCount) { "Incomplete album enumeration" }
                check(!current.hasMore) { "Incomplete album enumeration" }
                break
            }
            request = current.nextRequest
        }
        for (album in albums.values) {
            report(albums = albums.size)
            store(provider.album(album.id).tracks)
            report(albums = albums.size)
        }
    }
    // The callback also checks source identity before modifying the completed snapshot.
    report()
    repository.pruneLibraryTracks(sourceId, seen)
    report(LibrarySyncProgressPhase.IndexedLibrary)
    return seen.size
}

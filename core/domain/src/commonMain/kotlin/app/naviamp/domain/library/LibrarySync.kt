package app.naviamp.domain.library

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider

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
    var offset = 0
    while (true) {
        onProgress(
            LibrarySyncProgress(
                phase = LibrarySyncProgressPhase.LoadingAlbums,
                artistCount = artists.size,
                albumCount = albums.size,
                completed = albums.size,
            ),
        )
        val page = provider.albums(limit = albumPageSize, offset = offset)
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
        if (page.size < albumPageSize) break
        offset += albumPageSize
    }

    var trackCount = 0
    if (includeAlbumTracks) {
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

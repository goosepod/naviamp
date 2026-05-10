package app.naviamp.desktop

import app.naviamp.domain.provider.MediaProvider

class LibrarySync(
    private val cache: DesktopCache,
) {
    suspend fun sync(
        sourceId: String,
        provider: MediaProvider,
        onProgress: suspend (LibrarySyncProgress) -> Unit = {},
    ) {
        cache.markLibrarySyncStarted(sourceId)
        onProgress(LibrarySyncProgress("Loading artists", 0, null))
        val artists = provider.artists(limit = 100_000)
        cache.upsertLibraryArtists(sourceId, artists)
        onProgress(LibrarySyncProgress("Indexed artists", artists.size, null))

        val albums = mutableListOf<app.naviamp.domain.Album>()
        var offset = 0
        val pageSize = 500
        while (true) {
            onProgress(LibrarySyncProgress("Loading albums", albums.size, null))
            val page = provider.albums(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            albums += page
            cache.upsertLibraryAlbums(sourceId, page)
            onProgress(LibrarySyncProgress("Indexed albums", albums.size, null))
            if (page.size < pageSize) break
            offset += pageSize
        }

        var trackCount = 0
        albums.forEachIndexed { index, album ->
            onProgress(
                LibrarySyncProgress(
                    phase = "Loading tracks",
                    completed = index,
                    total = albums.size,
                ),
            )
            val details = cache.album(provider, album.id)
            cache.upsertLibraryAlbums(sourceId, listOf(details.album))
            cache.upsertLibraryTracks(sourceId, details.tracks)
            trackCount += details.tracks.size
        }

        cache.markLibrarySyncCompleted(sourceId)
        onProgress(
            LibrarySyncProgress(
                phase = "Library indexed",
                completed = artists.size + albums.size + trackCount,
                total = null,
            ),
        )
    }
}

data class LibrarySyncProgress(
    val phase: String,
    val completed: Int,
    val total: Int?,
) {
    fun label(): String =
        if (total == null) {
            "$phase: $completed"
        } else {
            "$phase: $completed/$total"
        }
}

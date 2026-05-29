package app.naviamp.desktop

import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.library.LibraryFreshness
import app.naviamp.domain.library.nextLibraryLimit as nextLibraryPageLimit
import app.naviamp.domain.provider.MediaProvider

class LibrarySync(
    private val cache: DesktopCache,
) {
    suspend fun syncAndMarkScanChecked(
        sourceId: String,
        provider: MediaProvider,
        onProgress: suspend (LibrarySyncProgress) -> Unit = {},
    ) {
        sync(
            sourceId = sourceId,
            provider = provider,
            onProgress = onProgress,
        )
        provider.libraryScanStatus()?.signature?.let { signature ->
            cache.markLibraryScanChecked(sourceId, signature)
        }
    }

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

fun DesktopCache.librarySnapshotFor(
    sourceId: String,
    query: String,
    limit: Int,
): LibrarySnapshot =
    if (query.isBlank()) {
        librarySnapshot(sourceId, limit = limit.toLong(), offset = 0)
    } else {
        searchLibrary(sourceId, query, limit = limit.toLong(), offset = 0)
    }

fun nextLibraryLimit(
    snapshot: LibrarySnapshot,
    tab: LibraryTab,
    currentLimit: Int,
    pageSize: Int,
): Int {
    val visibleCount = when (tab) {
        LibraryTab.Artists -> snapshot.artists.size
        LibraryTab.Albums -> snapshot.albums.size
    }
    return nextLibraryPageLimit(
        visibleCount = visibleCount,
        currentLimit = currentLimit,
        pageSize = pageSize,
    )
}

fun shouldAutoSyncLibrary(
    sourceId: String,
    cache: DesktopCache,
): Boolean {
    val indexStats = cache.libraryIndexStats(sourceId)
    return !indexStats.hasUsableIndex
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

suspend fun DesktopCache.libraryFreshnessFor(
    sourceId: String,
    provider: MediaProvider,
): LibraryFreshness {
    val scanStatus = provider.libraryScanStatus()
    val source = mediaSource(sourceId)
    return LibraryFreshness(
        signature = scanStatus?.signature,
        previousSignature = source?.lastLibraryScanSignature,
        scanning = scanStatus?.scanning == true,
    )
}

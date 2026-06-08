package app.naviamp.desktop

import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.library.LibrarySyncProgress
import app.naviamp.domain.library.LibrarySyncProgressPhase
import app.naviamp.domain.library.shouldAutoSyncLibrary as shouldAutoSyncLibraryIndex
import app.naviamp.domain.library.nextLibraryLimit as nextLibraryPageLimit
import app.naviamp.domain.library.syncLibraryIndex
import app.naviamp.domain.provider.MediaProvider

class DesktopLibrarySync(
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    private val providerResponseService: ProviderResponseService,
) {
    suspend fun syncAndMarkScanChecked(
        sourceId: String,
        provider: MediaProvider,
        onProgress: suspend (DesktopLibrarySyncProgress) -> Unit = {},
    ) {
        sync(
            sourceId = sourceId,
            provider = provider,
            onProgress = onProgress,
        )
        provider.libraryScanStatus()?.signature?.let { signature ->
            libraryIndexRepository.markLibraryScanChecked(sourceId, signature)
        }
    }

    suspend fun sync(
        sourceId: String,
        provider: MediaProvider,
        onProgress: suspend (DesktopLibrarySyncProgress) -> Unit = {},
    ) {
        syncLibraryIndex(
            sourceId = sourceId,
            provider = provider,
            libraryIndexRepository = libraryIndexRepository,
            artistLimit = 100_000,
            albumPageSize = 500,
            includeAlbumTracks = true,
            providerResponseService = providerResponseService,
        ) { progress ->
            onProgress(progress.toDesktopLibrarySyncProgress())
        }
    }
}

fun LocalLibraryIndexRepository.librarySnapshotFor(
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
    tab: DesktopLibraryTab,
    currentLimit: Int,
    pageSize: Int,
): Int {
    val visibleCount = when (tab) {
        DesktopLibraryTab.Artists -> snapshot.artists.size
        DesktopLibraryTab.Albums -> snapshot.albums.size
    }
    return nextLibraryPageLimit(
        visibleCount = visibleCount,
        currentLimit = currentLimit,
        pageSize = pageSize,
    )
}

fun shouldAutoSyncLibrary(
    sourceId: String,
    libraryIndexRepository: LocalLibraryIndexRepository,
): Boolean {
    val indexStats = libraryIndexRepository.libraryIndexStats(sourceId)
    return shouldAutoSyncLibraryIndex(indexStats)
}

data class DesktopLibrarySyncProgress(
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

private fun LibrarySyncProgress.toDesktopLibrarySyncProgress(): DesktopLibrarySyncProgress =
    when (phase) {
        LibrarySyncProgressPhase.LoadingArtists -> DesktopLibrarySyncProgress("Loading artists", 0, null)
        LibrarySyncProgressPhase.IndexedArtists -> DesktopLibrarySyncProgress("Indexed artists", artistCount, null)
        LibrarySyncProgressPhase.LoadingAlbums -> DesktopLibrarySyncProgress("Loading albums", albumCount, null)
        LibrarySyncProgressPhase.IndexedAlbums -> DesktopLibrarySyncProgress("Indexed albums", albumCount, null)
        LibrarySyncProgressPhase.LoadingTracks -> DesktopLibrarySyncProgress("Loading tracks", completed, total)
        LibrarySyncProgressPhase.IndexedLibrary -> {
            DesktopLibrarySyncProgress("Library indexed", artistCount + albumCount + trackCount, null)
        }
    }

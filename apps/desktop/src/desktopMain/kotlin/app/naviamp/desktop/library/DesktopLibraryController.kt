package app.naviamp.desktop

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.app.cacheDataClearedStatus
import app.naviamp.domain.app.libraryIndexClearedStatus
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.library.LibrarySyncCoordinator
import app.naviamp.domain.library.libraryConnectionRequiredStatus
import app.naviamp.domain.library.libraryFreshnessUpdate
import app.naviamp.domain.library.libraryLimitForOffset
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopLibraryController(
    private val scope: CoroutineScope,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    private val mediaSourceRepository: MediaSourceRepository,
    private val cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    private val libraryOffsetForLetter: (sourceId: String, tab: DesktopLibraryTab, letter: Char) -> Long,
    private val librarySync: DesktopLibrarySync,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val setConnectionStatus: (String) -> Unit,
    private val listState: LazyListState,
) {
    var query by mutableStateOf("")
        private set
    var tab by mutableStateOf(DesktopLibraryTab.Artists)
        private set
    var limit by mutableIntStateOf(LibraryPageSize)
        private set
    var snapshot by mutableStateOf(LibrarySnapshot())
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set

    fun updateQuery(query: String) {
        this.query = query
    }

    fun applyClearedState(snapshot: LibrarySnapshot, status: String?) {
        this.snapshot = snapshot
        this.status = status
    }

    fun refreshAfterQueryOrSourceChange() {
        limit = LibraryPageSize
        refreshLibrarySnapshot()
        scope.launch {
            listState.scrollToItem(0)
        }
    }

    fun refreshLibrarySnapshot() {
        val activeSourceId = sourceId()
        if (activeSourceId == null) {
            snapshot = LibrarySnapshot()
            status = libraryConnectionRequiredStatus()
            return
        }
        snapshot = libraryIndexRepository.librarySnapshotFor(activeSourceId, query, limit)
    }

    fun loadMoreLibraryRows() {
        val nextLimit = nextLibraryLimit(snapshot, tab, limit, LibraryPageSize)
        if (nextLimit == limit) return
        limit = nextLimit
        refreshLibrarySnapshot()
    }

    fun selectLibraryTab(tab: DesktopLibraryTab) {
        this.tab = tab
        limit = LibraryPageSize
        refreshLibrarySnapshot()
        scope.launch {
            listState.scrollToItem(0)
        }
    }

    fun jumpLibraryToLetter(letter: Char) {
        val activeSourceId = sourceId() ?: return
        if (query.isNotBlank()) return
        val offset = libraryOffsetForLetter(activeSourceId, tab, letter).toInt()
        limit = libraryLimitForOffset(offset, LibraryPageSize)
        refreshLibrarySnapshot()
        scope.launch {
            listState.scrollToItem((offset + 1).coerceAtLeast(0))
        }
    }

    fun startLibrarySync(force: Boolean = false) {
        val coordinator = librarySyncCoordinator()
        scope.launch {
            val uiContext = coroutineContext
            coordinator.startSync(
                force = force,
                sync = { sourceId, provider, setProgressStatus ->
                    withContext(Dispatchers.IO) {
                        librarySync.syncAndMarkScanChecked(
                            sourceId = sourceId,
                            provider = provider,
                            onProgress = { progress ->
                                withContext(uiContext) {
                                    setProgressStatus(progress.label())
                                }
                            },
                        )
                    }
                },
                onCompleted = { refreshLibrarySnapshot() },
            )
        }
    }

    private fun librarySyncCoordinator(): LibrarySyncCoordinator =
        LibrarySyncCoordinator(
            provider = provider,
            sourceId = sourceId,
            syncing = { syncing },
            setSyncing = { nextSyncing -> syncing = nextSyncing },
            status = { status },
            setStatus = { nextStatus -> status = nextStatus },
            libraryIndexRepository = libraryIndexRepository,
            mediaSourceRepository = mediaSourceRepository,
        )

    fun checkLibraryFreshness() {
        val coordinator = librarySyncCoordinator()
        scope.launch {
            coordinator.checkFreshness(
                loadFreshness = { sourceId, provider, currentStatus ->
                    withContext(Dispatchers.IO) {
                        libraryFreshnessUpdate(
                            sourceId = sourceId,
                            provider = provider,
                            mediaSourceRepository = mediaSourceRepository,
                            currentStatus = currentStatus,
                        )
                    }
                },
                markScanChecked = { sourceId, signature ->
                    withContext(Dispatchers.IO) {
                        libraryIndexRepository.markLibraryScanChecked(sourceId, signature)
                    }
                },
            )
        }
    }

    fun clearCacheData() {
        cacheMaintenanceRepository.clearCacheData()
        setConnectionStatus(cacheDataClearedStatus(detailed = true))
    }

    fun clearLibraryData() {
        sourceId()?.let { activeSourceId ->
            libraryIndexRepository.clearLibraryData(activeSourceId)
        } ?: libraryIndexRepository.clearLibraryData(null)
        snapshot = LibrarySnapshot()
        setConnectionStatus(libraryIndexClearedStatus(detailed = true))
    }
}

package app.naviamp.desktop

import androidx.compose.foundation.lazy.LazyListState
import app.naviamp.domain.app.cacheDataClearedStatus
import app.naviamp.domain.app.libraryIndexClearedStatus
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.library.evaluateLibraryFreshness
import app.naviamp.domain.library.libraryConnectionRequiredStatus
import app.naviamp.domain.library.libraryLimitForOffset
import app.naviamp.domain.library.librarySyncErrorStatus
import app.naviamp.domain.library.librarySyncStartingStatus
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopLibraryController(
    private val scope: CoroutineScope,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    private val mediaSourceRepository: MediaSourceRepository,
    private val cacheMaintenanceRepository: CacheMaintenanceRepository<CacheStats>,
    private val libraryOffsetForLetter: (sourceId: String, tab: LibraryTab, letter: Char) -> Long,
    private val librarySync: LibrarySync,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val libraryQuery: () -> String,
    private val libraryTab: () -> LibraryTab,
    private val libraryLimit: () -> Int,
    private val setLibraryLimit: (Int) -> Unit,
    private val librarySnapshot: () -> LibrarySnapshot,
    private val setLibrarySnapshot: (LibrarySnapshot) -> Unit,
    private val libraryStatus: () -> String?,
    private val setLibraryStatus: (String?) -> Unit,
    private val setConnectionStatus: (String) -> Unit,
    private val isLibrarySyncing: () -> Boolean,
    private val setLibrarySyncing: (Boolean) -> Unit,
    private val listState: LazyListState,
) {
    fun refreshLibrarySnapshot() {
        val activeSourceId = sourceId()
        if (activeSourceId == null) {
            setLibrarySnapshot(LibrarySnapshot())
            setLibraryStatus(libraryConnectionRequiredStatus())
            return
        }
        setLibrarySnapshot(libraryIndexRepository.librarySnapshotFor(activeSourceId, libraryQuery(), libraryLimit()))
    }

    fun loadMoreLibraryRows() {
        val nextLimit = nextLibraryLimit(librarySnapshot(), libraryTab(), libraryLimit(), LibraryPageSize)
        if (nextLimit == libraryLimit()) return
        setLibraryLimit(nextLimit)
        refreshLibrarySnapshot()
    }

    fun jumpLibraryToLetter(letter: Char) {
        val activeSourceId = sourceId() ?: return
        if (libraryQuery().isNotBlank()) return
        val offset = libraryOffsetForLetter(activeSourceId, libraryTab(), letter).toInt()
        setLibraryLimit(libraryLimitForOffset(offset, LibraryPageSize))
        refreshLibrarySnapshot()
        scope.launch {
            listState.scrollToItem((offset + 1).coerceAtLeast(0))
        }
    }

    fun startLibrarySync(force: Boolean = false) {
        val activeProvider = provider() ?: return
        val activeSourceId = sourceId() ?: return
        if (isLibrarySyncing()) return
        if (!force && !shouldAutoSyncLibrary(activeSourceId, libraryIndexRepository)) {
            setLibraryStatus(null)
            return
        }
        setLibrarySyncing(true)
        setLibraryStatus(librarySyncStartingStatus())
        scope.launch {
            val uiContext = coroutineContext
            try {
                withContext(Dispatchers.IO) {
                    librarySync.syncAndMarkScanChecked(
                        sourceId = activeSourceId,
                        provider = activeProvider,
                        onProgress = { progress ->
                            withContext(uiContext) {
                                setLibraryStatus(progress.label())
                            }
                        },
                    )
                }
                refreshLibrarySnapshot()
                setLibraryStatus(null)
            } catch (exception: Exception) {
                setLibraryStatus(librarySyncErrorStatus(exception))
            } finally {
                setLibrarySyncing(false)
            }
        }
    }

    fun checkLibraryFreshness() {
        val activeProvider = provider() ?: return
        val activeSourceId = sourceId() ?: return
        if (isLibrarySyncing()) return
        scope.launch {
            val freshness = withContext(Dispatchers.IO) {
                mediaSourceRepository.libraryFreshnessFor(activeSourceId, activeProvider)
            }
            val update = freshness.evaluateLibraryFreshness(libraryStatus())
            update.signatureToMarkChecked?.let { signature ->
                withContext(Dispatchers.IO) {
                    libraryIndexRepository.markLibraryScanChecked(activeSourceId, signature)
                }
            }
            update.status?.let { status ->
                setLibraryStatus(status)
            }
            if (update.clearStatus) {
                setLibraryStatus(null)
            }
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
        setLibrarySnapshot(LibrarySnapshot())
        setConnectionStatus(libraryIndexClearedStatus(detailed = true))
    }
}

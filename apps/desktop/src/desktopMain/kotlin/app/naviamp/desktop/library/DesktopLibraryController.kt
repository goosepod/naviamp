package app.naviamp.desktop

import app.naviamp.domain.cache.StorageCacheStats

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.app.cacheDataClearedStatus
import app.naviamp.domain.app.libraryIndexClearedStatus
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.library.libraryConnectionRequiredStatus
import app.naviamp.domain.library.ArtistLibraryIndex
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DesktopLibraryController(
    private val scope: CoroutineScope,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    private val cacheMaintenanceRepository: CacheMaintenanceRepository<StorageCacheStats>,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val setConnectionStatus: (String) -> Unit,
    private val listState: LazyListState,
) {
    var query by mutableStateOf("")
        private set
    var tab by mutableStateOf(DesktopLibraryTab.Artists)
        private set
    var snapshot by mutableStateOf(LibrarySnapshot())
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set

    private val artistIndex = ArtistLibraryIndex(libraryIndexRepository)

    fun updateQuery(query: String) {
        this.query = query
    }

    fun applyClearedState(snapshot: LibrarySnapshot, status: String?) {
        this.snapshot = LibrarySnapshot()
        this.status = status
    }

    fun refreshAfterQueryOrSourceChange() {
        snapshot = LibrarySnapshot()
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
        snapshot = artistIndex.snapshot(activeSourceId, query)
        status = if (snapshot.artists.isEmpty()) "No artists indexed yet. Refresh to load them." else null
    }

    fun refreshArtistIndex() {
        if (syncing) return
        val activeProvider = provider()
        val activeSourceId = sourceId()
        if (activeProvider == null || activeSourceId == null) {
            refreshLibrarySnapshot()
            return
        }
        syncing = true
        status = "Refreshing artists…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    artistIndex.refresh(activeSourceId, activeProvider)
                }
            }.onSuccess {
                snapshot = artistIndex.snapshot(activeSourceId, query)
                status = "${snapshot.artists.size} artists indexed."
            }.onFailure { error ->
                status = "Could not refresh artists: ${error.message ?: error::class.simpleName}"
            }
            syncing = false
        }
    }

    fun jumpLibraryToLetter(letter: Char) {
        if (query.isNotBlank()) return
        val boundary = if (letter == '#') "" else letter.lowercaseChar().toString()
        val offset = snapshot.artists.indexOfFirst { artist ->
            artist.name.lowercase() >= boundary
        }.takeIf { it >= 0 } ?: return
        scope.launch { listState.scrollToItem(offset + 1) }
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

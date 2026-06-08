package app.naviamp.android

import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.library.libraryFreshnessUpdate
import app.naviamp.domain.library.librarySyncCompletedStatus
import app.naviamp.domain.library.librarySyncErrorStatus
import app.naviamp.domain.library.librarySyncStartingStatus
import app.naviamp.domain.library.shouldAutoSyncLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun startAndroidLibrarySync(
    scope: CoroutineScope,
    state: AndroidAppState,
    libraryIndexRepository: LocalLibraryIndexRepository,
    force: Boolean = false,
) {
    val activeProvider = state.provider ?: return
    val sourceId = state.activeSourceId ?: return
    if (state.isLibrarySyncing) return
    if (!force && !shouldAutoSyncLibrary(libraryIndexRepository.libraryIndexStats(sourceId))) {
        state.libraryStatus = null
        return
    }
    state.isLibrarySyncing = true
    state.libraryStatus = librarySyncStartingStatus()
    scope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                syncAndroidLibrary(sourceId, activeProvider, libraryIndexRepository) { progress ->
                    withContext(Dispatchers.Main) {
                        if (progress.artists != null) {
                            state.homeState = state.homeState.copy(artists = progress.artists)
                        }
                        state.libraryStatus = progress.label
                        if (state.nowPlaying == null && state.nowPlayingStation == null) {
                            state.status = progress.label
                        }
                    }
                }
            }
        }.onSuccess {
            state.libraryStatus = null
            if (state.nowPlaying == null && state.nowPlayingStation == null) {
                state.status = librarySyncCompletedStatus()
            }
        }.onFailure { error ->
            state.libraryStatus = librarySyncErrorStatus(error)
            state.status = state.libraryStatus.orEmpty()
        }
        state.isLibrarySyncing = false
    }
}

fun checkAndroidLibraryFreshness(
    scope: CoroutineScope,
    state: AndroidAppState,
    mediaSourceRepository: MediaSourceRepository,
    libraryIndexRepository: LocalLibraryIndexRepository,
) {
    val activeProvider = state.provider ?: return
    val sourceId = state.activeSourceId ?: return
    if (state.isLibrarySyncing) return
    scope.launch {
        val freshness = withContext(Dispatchers.IO) {
            libraryFreshnessUpdate(
                sourceId = sourceId,
                provider = activeProvider,
                mediaSourceRepository = mediaSourceRepository,
                currentStatus = state.libraryStatus,
            )
        }
        freshness.signatureToMarkChecked?.let { signature ->
            withContext(Dispatchers.IO) {
                libraryIndexRepository.markLibraryScanChecked(sourceId, signature)
            }
        }
        freshness.status?.let { status ->
            state.libraryStatus = status
        }
        if (freshness.clearStatus) {
            state.libraryStatus = null
        }
    }
}

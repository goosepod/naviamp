package app.naviamp.android

import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.library.LibraryFreshness
import app.naviamp.domain.library.evaluateLibraryFreshness
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
                activeProvider.libraryScanStatus()?.signature?.let { signature ->
                    libraryIndexRepository.markLibraryScanChecked(sourceId, signature)
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
            val scanStatus = activeProvider.libraryScanStatus()
            LibraryFreshness(
                signature = scanStatus?.signature,
                previousSignature = mediaSourceRepository.mediaSource(sourceId)?.lastLibraryScanSignature,
                scanning = scanStatus?.scanning == true,
            )
        }
        val update = freshness.evaluateLibraryFreshness(state.libraryStatus)
        update.signatureToMarkChecked?.let { signature ->
            withContext(Dispatchers.IO) {
                libraryIndexRepository.markLibraryScanChecked(sourceId, signature)
            }
        }
        update.status?.let { status ->
            state.libraryStatus = status
        }
        if (update.clearStatus) {
            state.libraryStatus = null
        }
    }
}

package app.naviamp.android

import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.library.LibrarySyncCoordinator
import app.naviamp.domain.library.libraryFreshnessUpdate
import app.naviamp.domain.library.librarySyncCompletedStatus
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
    val coordinator = androidLibrarySyncCoordinator(state, libraryIndexRepository)
    scope.launch {
        coordinator.startSync(
            force = force,
            sync = { sourceId, activeProvider, setProgressStatus ->
                withContext(Dispatchers.IO) {
                    syncAndroidLibrary(sourceId, activeProvider, libraryIndexRepository) { progress ->
                        withContext(Dispatchers.Main) {
                            if (progress.artists != null) {
                                state.homeState = state.homeState.copy(artists = progress.artists)
                            }
                            setProgressStatus(progress.label)
                            if (state.nowPlaying == null && state.nowPlayingStation == null) {
                                state.status = progress.label
                            }
                        }
                    }
                }
            },
            onCompleted = {
                if (state.nowPlaying == null && state.nowPlayingStation == null) {
                    state.status = librarySyncCompletedStatus()
                }
            },
            onFailed = { status -> state.status = status },
        )
    }
}

private fun androidLibrarySyncCoordinator(
    state: AndroidAppState,
    libraryIndexRepository: LocalLibraryIndexRepository,
    mediaSourceRepository: MediaSourceRepository? = null,
): LibrarySyncCoordinator =
    LibrarySyncCoordinator(
        provider = { state.provider },
        sourceId = { state.activeSourceId },
        syncing = { state.isLibrarySyncing },
        setSyncing = { syncing -> state.isLibrarySyncing = syncing },
        status = { state.libraryStatus },
        setStatus = { status -> state.libraryStatus = status },
        libraryIndexRepository = libraryIndexRepository,
        mediaSourceRepository = mediaSourceRepository,
    )

fun checkAndroidLibraryFreshness(
    scope: CoroutineScope,
    state: AndroidAppState,
    mediaSourceRepository: MediaSourceRepository,
    libraryIndexRepository: LocalLibraryIndexRepository,
) {
    val coordinator = androidLibrarySyncCoordinator(state, libraryIndexRepository, mediaSourceRepository)
    scope.launch {
        coordinator.checkFreshness(
            loadFreshness = { sourceId, activeProvider, currentStatus ->
                withContext(Dispatchers.IO) {
                    libraryFreshnessUpdate(
                        sourceId = sourceId,
                        provider = activeProvider,
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

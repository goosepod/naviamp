package app.naviamp.domain.library

import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.provider.MediaProvider

sealed interface LibrarySyncStartPlan {
    data object MissingConnection : LibrarySyncStartPlan
    data object AlreadySyncing : LibrarySyncStartPlan
    data object SkipAutoSync : LibrarySyncStartPlan
    data class Start(
        val sourceId: String,
        val provider: MediaProvider,
    ) : LibrarySyncStartPlan
}

fun librarySyncStartPlan(
    provider: MediaProvider?,
    sourceId: String?,
    syncing: Boolean,
    force: Boolean,
    shouldAutoSync: (String) -> Boolean,
): LibrarySyncStartPlan =
    when {
        provider == null || sourceId == null -> LibrarySyncStartPlan.MissingConnection
        syncing -> LibrarySyncStartPlan.AlreadySyncing
        !force && !shouldAutoSync(sourceId) -> LibrarySyncStartPlan.SkipAutoSync
        else -> LibrarySyncStartPlan.Start(sourceId = sourceId, provider = provider)
    }

class LibrarySyncCoordinator(
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val syncing: () -> Boolean,
    private val setSyncing: (Boolean) -> Unit,
    private val status: () -> String?,
    private val setStatus: (String?) -> Unit,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    private val mediaSourceRepository: MediaSourceRepository? = null,
    private val shouldAutoSync: (String) -> Boolean = { activeSourceId ->
        shouldAutoSyncLibrary(libraryIndexRepository.libraryIndexStats(activeSourceId))
    },
) {
    suspend fun startSync(
        force: Boolean,
        sync: suspend (
            sourceId: String,
            provider: MediaProvider,
            setProgressStatus: suspend (String) -> Unit,
        ) -> Unit,
        onSkippedAutoSync: suspend () -> Unit = {},
        onCompleted: suspend () -> Unit = {},
        onFailed: suspend (String) -> Unit = {},
    ) {
        when (
            val plan = librarySyncStartPlan(
                provider = provider(),
                sourceId = sourceId(),
                syncing = syncing(),
                force = force,
                shouldAutoSync = shouldAutoSync,
            )
        ) {
            LibrarySyncStartPlan.MissingConnection,
            LibrarySyncStartPlan.AlreadySyncing,
            -> return
            LibrarySyncStartPlan.SkipAutoSync -> {
                setStatus(null)
                onSkippedAutoSync()
            }
            is LibrarySyncStartPlan.Start -> {
                setSyncing(true)
                setStatus(librarySyncStartingStatus())
                try {
                    sync(plan.sourceId, plan.provider) { progressStatus -> setStatus(progressStatus) }
                    setStatus(null)
                    onCompleted()
                } catch (error: Throwable) {
                    val errorStatus = librarySyncErrorStatus(error)
                    setStatus(errorStatus)
                    onFailed(errorStatus)
                } finally {
                    setSyncing(false)
                }
            }
        }
    }

    suspend fun checkFreshness(
        loadFreshness: suspend (
            sourceId: String,
            provider: MediaProvider,
            currentStatus: String?,
        ) -> LibraryFreshnessUpdate = { activeSourceId, activeProvider, currentStatus ->
            mediaSourceRepository?.let { repository ->
                libraryFreshnessUpdate(
                    sourceId = activeSourceId,
                    provider = activeProvider,
                    mediaSourceRepository = repository,
                    currentStatus = currentStatus,
                )
            } ?: LibraryFreshnessUpdate()
        },
        markScanChecked: suspend (sourceId: String, signature: String) -> Unit = { activeSourceId, signature ->
            libraryIndexRepository.markLibraryScanChecked(activeSourceId, signature)
        },
    ) {
        val activeProvider = provider() ?: return
        val activeSourceId = sourceId() ?: return
        if (syncing()) return
        val freshness = loadFreshness(activeSourceId, activeProvider, status())
        freshness.signatureToMarkChecked?.let { signature ->
            markScanChecked(activeSourceId, signature)
        }
        freshness.status?.let(setStatus)
        if (freshness.clearStatus) {
            setStatus(null)
        }
    }
}

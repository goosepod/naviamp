package app.naviamp.domain.library

import app.naviamp.domain.cache.LibraryIndexStats
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.provider.MediaProvider

const val LibraryFreshnessCheckIntervalMillis = 60_000L

data class LibraryFreshness(
    val signature: String?,
    val previousSignature: String?,
    val scanning: Boolean,
)

data class LibraryFreshnessUpdate(
    val signatureToMarkChecked: String? = null,
    val status: String? = null,
    val clearStatus: Boolean = false,
)

fun LibraryFreshness.evaluateLibraryFreshness(currentStatus: String?): LibraryFreshnessUpdate {
    val currentSignature = signature ?: return LibraryFreshnessUpdate()
    return when {
        previousSignature == null -> LibraryFreshnessUpdate(signatureToMarkChecked = currentSignature)
        previousSignature != currentSignature -> LibraryFreshnessUpdate(
            status = if (scanning) {
                "Navidrome is scanning. Refresh library after the scan finishes."
            } else {
                "Library changed on server. Refresh library to import updates."
            },
        )
        currentStatus?.startsWith("Library changed on server") == true ||
            currentStatus?.startsWith("Navidrome is scanning") == true -> LibraryFreshnessUpdate(clearStatus = true)
        else -> LibraryFreshnessUpdate()
    }
}

suspend fun libraryFreshnessUpdate(
    sourceId: String,
    provider: MediaProvider,
    mediaSourceRepository: MediaSourceRepository,
    currentStatus: String?,
): LibraryFreshnessUpdate {
    val scanStatus = provider.libraryScanStatus()
    val source = mediaSourceRepository.mediaSource(sourceId)
    return LibraryFreshness(
        signature = scanStatus?.signature,
        previousSignature = source?.lastLibraryScanSignature,
        scanning = scanStatus?.scanning == true,
    ).evaluateLibraryFreshness(currentStatus)
}

fun shouldAutoSyncLibrary(indexStats: LibraryIndexStats): Boolean =
    !indexStats.hasUsableIndex

fun libraryConnectionRequiredStatus(): String =
    "Connect to Navidrome to import your library."

fun librarySyncStartingStatus(): String =
    "Starting library import..."

fun librarySyncCompletedStatus(): String =
    "Library refreshed."

fun librarySyncErrorStatus(error: Throwable): String =
    error.message ?: "Could not import library."

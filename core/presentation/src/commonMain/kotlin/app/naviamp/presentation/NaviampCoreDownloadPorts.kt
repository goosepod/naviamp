package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.PersistedDownloadJob
import app.naviamp.domain.provider.MediaProvider

data class NaviampCoreDownloadedTrack(
    val storageId: String,
    val track: Track,
    val sizeBytes: Long,
    val qualityLabel: String = "",
)

/** How a requested download ended; reasons are only those the product reports specifically. */
sealed interface NaviampCoreDownloadOutcome {
    data object Completed : NaviampCoreDownloadOutcome
    /** Every requested track was already saved, so nothing was transferred. */
    data object AlreadyDownloaded : NaviampCoreDownloadOutcome
    data object Cancelled : NaviampCoreDownloadOutcome
    data class Failed(val reason: NaviampCoreDownloadFailure) : NaviampCoreDownloadOutcome
}

enum class NaviampCoreDownloadFailure { StorageLimit, MobileDataDisabled, Other }

internal fun downloadFailureFromMessage(message: String?): NaviampCoreDownloadFailure =
    if (message == app.naviamp.domain.cache.DownloadStorageLimitExceededMessage) NaviampCoreDownloadFailure.StorageLimit
    else NaviampCoreDownloadFailure.Other

data class NaviampCoreDownloadStorageSnapshot(
    val downloads: List<NaviampCoreDownloadedTrack> = emptyList(),
    val audioCacheCount: Long = 0L,
    val audioCacheBytes: Long = 0L,
    val pendingProviderActionCount: Long = 0L,
)

/** Filesystem and database effects. Core owns when and why each operation occurs. */
interface NaviampCoreDownloadStoragePort {
    suspend fun snapshot(sourceId: String): NaviampCoreDownloadStorageSnapshot
    /** Read-only completed downloads whose native files are currently available; null if unverifiable. */
    suspend fun availableDownloads(sourceId: String): List<NaviampCoreDownloadedTrack>? = null
    suspend fun pruneMissing(sourceId: String): Int
    suspend fun remove(sourceId: String, track: Track)
    suspend fun deleteAll(sourceId: String): Int
}

data class NaviampCoreDownloadTransferRequest(
    val label: String,
    val tracks: List<Track>,
    val sourceId: String,
    val provider: MediaProvider,
    val quality: StreamQuality,
    val maxDownloadBytes: Long,
    val replaceExisting: Boolean,
    val allowMobileDownloads: Boolean,
    val isActiveNetworkMobileData: Boolean,
    val includeCompletedCount: Boolean,
    val manualRetention: Boolean = true,
)

data class NaviampCoreDownloadTransferResult(val refreshDownloads: Boolean)

/** Audio-byte transfer effect; job identity, state, status, retry, and cancellation stay in Core. */
fun interface NaviampCoreDownloadTransferPort {
    suspend fun transfer(
        request: NaviampCoreDownloadTransferRequest,
        onStatus: (String) -> Unit,
        onJobUpdate: (DownloadJobUpdate) -> Unit,
    ): NaviampCoreDownloadTransferResult
}

/** Persistence/repository boundary for the common keep-downloaded policy selected by Core. */
interface NaviampCoreKeepDownloadedPort {
    fun savedJobs(sourceId: String): List<PersistedDownloadJob> = emptyList()
    fun saveJob(job: PersistedDownloadJob) = Unit
    fun deleteJob(sourceId: String, jobId: String) = Unit

    fun policies(sourceId: String): List<KeepDownloadedCollectionPolicy>
    fun toggle(policy: KeepDownloadedCollectionPolicy): NaviampKeepDownloadedToggleResult
    fun reconcile(
        policy: KeepDownloadedCollectionPolicy,
        tracks: List<Track>,
    ): NaviampKeepDownloadedReconciliationApplication
}

fun interface NaviampCoreDownloadedPlaybackPort {
    suspend fun play(downloads: List<Track>, index: Int)
}

fun interface NaviampCoreMobileNetworkPort {
    fun isActiveNetworkMobileData(): Boolean
}

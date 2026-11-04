package app.naviamp.domain.provider

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

data class DiscoveryAcquisitionScope(val sourceId: String, val accountKey: String)

@Serializable
enum class DiscoveryAcquisitionPhase {
    Saved, MissingSource, WaitingForRelease, Queued, Searching, Processing, Downloading, Importing,
    Partial, Complete, Available, Blocked, Failed, Cancelled, Uncertain, Conflict, Rejected, Unknown;
    val active: Boolean get() = this in setOf(Queued, Searching, Processing, Downloading, Importing, Complete)
}

@Serializable
data class DiscoveryAcquisitionRecord(
    val id: String,
    val artistId: String,
    val artistName: String,
    val actionId: String,
    val albumRequest: Boolean,
    val releaseId: String? = null,
    val releaseTitle: String? = null,
    val operation: ProviderDiscoveryOperation,
    val phase: DiscoveryAcquisitionPhase,
    val createdAtMillis: Long,
    val checkedAtMillis: Long? = null,
    val rawStatus: String? = null,
    val trackTitle: String? = null,
) {
    val action: ProviderDiscoveryAction get() = if (albumRequest) ProviderDiscoveryAction.RequestAlbum(actionId)
        else ProviderDiscoveryAction.RequestTrack(actionId)
}

interface DiscoveryAcquisitionRepository {
    fun records(scope: DiscoveryAcquisitionScope): List<DiscoveryAcquisitionRecord>
    fun record(scope: DiscoveryAcquisitionScope, id: String): DiscoveryAcquisitionRecord?
    fun save(scope: DiscoveryAcquisitionScope, record: DiscoveryAcquisitionRecord)
}

class MemoryDiscoveryAcquisitionRepository : DiscoveryAcquisitionRepository {
    private val rows = mutableMapOf<Pair<DiscoveryAcquisitionScope, String>, DiscoveryAcquisitionRecord>()
    override fun records(scope: DiscoveryAcquisitionScope) = rows.filterKeys { it.first == scope }.values
        .sortedByDescending { it.createdAtMillis }.take(200)
    override fun record(scope: DiscoveryAcquisitionScope, id: String) = rows[scope to id]
    override fun save(scope: DiscoveryAcquisitionScope, record: DiscoveryAcquisitionRecord) { rows[scope to record.id] = record }
}

data class ProviderDiscoveryAcquisitionStatus(
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Unsupported,
    val phase: DiscoveryAcquisitionPhase = DiscoveryAcquisitionPhase.Unknown,
    val rawStatus: String? = null,
    val waitingForRelease: ProviderDiscoveryWaitingForRelease? = null,
)

data class DiscoveryAcquisitionSnapshot(
    val records: List<DiscoveryAcquisitionRecord>,
    val activity: ProviderDiscoveryActivities = ProviderDiscoveryActivities(),
    val ownership: Map<String, ProviderDiscoveryReleaseOwnership> = emptyMap(),
    val status: ProviderDiscoveryStatus = ProviderDiscoveryStatus.Ready,
) {
    val active: Boolean get() = records.any { it.phase.active } || activity.items.any {
        (it.releaseGroupMbid != null || it.identity?.managedAlbumId != null || it.kind in setOf("album_requested", "track_download", "album_grab")) &&
            acquisitionPhase(it.status) in setOf(DiscoveryAcquisitionPhase.Queued, DiscoveryAcquisitionPhase.Searching, DiscoveryAcquisitionPhase.Processing,
            DiscoveryAcquisitionPhase.Downloading, DiscoveryAcquisitionPhase.Importing)
    }
}

/** Common persistence/no-replay policy. It only reads status while reconciling; never acquires. */
class DiscoveryAcquisitionTracker(
    private val repository: DiscoveryAcquisitionRepository,
    private val nowMillis: () -> Long,
) {
    private val operationMutex = Mutex()
    fun records(scope: DiscoveryAcquisitionScope) = repository.records(scope)
    fun record(scope: DiscoveryAcquisitionScope, artistId: String, action: ProviderDiscoveryAction) =
        repository.record(scope, key(artistId, action))

    suspend fun submit(scope: DiscoveryAcquisitionScope, artist: ProviderDiscoveryArtist,
        action: ProviderDiscoveryAction, release: ProviderDiscoveryRelease?,
        trackTitle: String? = null,
        submit: suspend () -> ProviderDiscoveryOperation): ProviderDiscoveryOperation = operationMutex.withLock {
        val id = key(artist.id, action)
        val previous = repository.record(scope, id)
        if (previous?.operation?.status?.blocksRepeatedRequest == true) return@withLock previous.operation
        val intent = DiscoveryAcquisitionRecord(id, artist.id, artist.name,
            when (action) { is ProviderDiscoveryAction.RequestAlbum -> action.id; is ProviderDiscoveryAction.RequestTrack -> action.id; else -> error("Not acquisition") },
            action is ProviderDiscoveryAction.RequestAlbum, release?.id, release?.title,
            ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Uncertain), DiscoveryAcquisitionPhase.Uncertain, nowMillis(), trackTitle = trackTitle)
        try { repository.save(scope, intent) } catch (error: Throwable) {
            if (error is CancellationException) throw error
            return@withLock ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Unavailable)
        }
        val result = try { submit() } catch (error: Throwable) {
            if (error is CancellationException) throw error
            ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Uncertain)
        }
        repository.save(scope, intent.copy(operation = result, phase = result.acquisitionPhase()))
        result
    }

    suspend fun refresh(scope: DiscoveryAcquisitionScope, discovery: ProviderDiscovery): DiscoveryAcquisitionSnapshot = operationMutex.withLock {
        val activity = try { discovery.requestActivity() } catch (error: Throwable) {
            if (error is CancellationException) throw error
            ProviderDiscoveryActivities(ProviderDiscoveryStatus.Unavailable)
        }
        var status = activity.status
        val ownership = mutableMapOf<String, ProviderDiscoveryReleaseOwnership>()
        for (record in repository.records(scope).sortedBy { it.checkedAtMillis ?: Long.MIN_VALUE }.take(20)) {
            var next = record.copy(checkedAtMillis = nowMillis())
            var observedStatus = false
            val identity = record.operation.identity
            if (identity != null) {
                val observed = try { discovery.acquisitionStatus(identity) } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    ProviderDiscoveryAcquisitionStatus(ProviderDiscoveryStatus.Unavailable)
                }
                if (observed.status == ProviderDiscoveryStatus.Ready) {
                    observedStatus = true
                    if (status == ProviderDiscoveryStatus.Unsupported) status = ProviderDiscoveryStatus.Ready
                    next = next.copy(phase = observed.phase,
                    rawStatus = observed.rawStatus, checkedAtMillis = nowMillis(),
                    operation = next.operation.copy(waitingForRelease = observed.waitingForRelease ?: next.operation.waitingForRelease))
                }
                else if (observed.status != ProviderDiscoveryStatus.Unsupported) status = observed.status
            }
            // History completion is not proof of import or playback availability. Correlate IDs only.
            if (!observedStatus && activity.status == ProviderDiscoveryStatus.Ready && identity != null) {
                val matches = activity.items.filter { it.identity?.let { other ->
                    identity.jobIds.any { it in other.jobIds } ||
                        (identity.requestGroupId != null && identity.requestGroupId == other.requestGroupId) ||
                        (identity.managedAlbumId != null && identity.managedAlbumId == other.managedAlbumId)
                } == true }
                if (matches.size == 1) next = next.copy(phase = acquisitionPhase(matches.single().status),
                    rawStatus = matches.single().status, checkedAtMillis = nowMillis())
            }
            val releaseId = record.releaseId?.takeIf { runCatching { DiscoveryReleaseGroupMbid(it) }.isSuccess }
            if (releaseId != null) {
                val owned = try { discovery.releaseOwnership(DiscoveryReleaseGroupMbid(releaseId)) } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Unavailable)
                }
                ownership[record.id] = owned
                if (owned.status !in setOf(ProviderDiscoveryStatus.Ready, ProviderDiscoveryStatus.Unsupported)) status = owned.status
                if (owned.status == ProviderDiscoveryStatus.Ready && owned.libraryAlbum != null &&
                    (record.albumRequest && owned.availability == ProviderDiscoveryReleaseAvailability.Available ||
                        !record.albumRequest && owned.ownedRecordings.any { it.value == record.actionId }))
                    next = next.copy(phase = DiscoveryAcquisitionPhase.Available, checkedAtMillis = nowMillis())
                else if (next.phase == DiscoveryAcquisitionPhase.Available)
                    next = next.copy(phase = DiscoveryAcquisitionPhase.Complete)
            }
            val operationStatus = when (next.phase) {
                DiscoveryAcquisitionPhase.WaitingForRelease -> ProviderDiscoveryOperationStatus.WaitingForRelease
                DiscoveryAcquisitionPhase.Available -> ProviderDiscoveryOperationStatus.AlreadyOwned
                DiscoveryAcquisitionPhase.Queued, DiscoveryAcquisitionPhase.Searching, DiscoveryAcquisitionPhase.Processing, DiscoveryAcquisitionPhase.Downloading,
                DiscoveryAcquisitionPhase.Importing -> ProviderDiscoveryOperationStatus.Queued
                DiscoveryAcquisitionPhase.MissingSource -> ProviderDiscoveryOperationStatus.MissingSource
                DiscoveryAcquisitionPhase.Rejected -> ProviderDiscoveryOperationStatus.Rejected
                DiscoveryAcquisitionPhase.Conflict -> ProviderDiscoveryOperationStatus.Conflict
                DiscoveryAcquisitionPhase.Uncertain, DiscoveryAcquisitionPhase.Unknown -> ProviderDiscoveryOperationStatus.Uncertain
                else -> ProviderDiscoveryOperationStatus.Saved
            }
            next = next.copy(operation = next.operation.copy(status = operationStatus,
                waitingForRelease = next.operation.waitingForRelease.takeIf { next.phase == DiscoveryAcquisitionPhase.WaitingForRelease }))
            if (next != record) repository.save(scope, next)
        }
        DiscoveryAcquisitionSnapshot(repository.records(scope), activity, ownership, status)
    }

    companion object {
        fun key(artistId: String, action: ProviderDiscoveryAction): String = "$artistId:" + when (action) {
            is ProviderDiscoveryAction.RequestAlbum -> "album:${action.id}"
            is ProviderDiscoveryAction.RequestTrack -> "track:${action.id}"
            else -> error("Not acquisition")
        }
        /** No hidden/idle loop, no polling future release dates, and bounded error retries. */
        fun nextPollMillis(snapshot: DiscoveryAcquisitionSnapshot, attempts: Int, failures: Int): Long? =
            if (!snapshot.active || attempts >= 8 || failures >= 3 || snapshot.status in setOf(
                ProviderDiscoveryStatus.Unsupported, ProviderDiscoveryStatus.Forbidden, ProviderDiscoveryStatus.SignInRequired,
                ProviderDiscoveryStatus.PasswordRequired)) null else (30_000L * (1L shl failures.coerceIn(0, 2)))
    }
}

fun ProviderDiscoveryOperation.acquisitionPhase(): DiscoveryAcquisitionPhase = when (status) {
    ProviderDiscoveryOperationStatus.WaitingForRelease -> DiscoveryAcquisitionPhase.WaitingForRelease
    ProviderDiscoveryOperationStatus.Queued, ProviderDiscoveryOperationStatus.AlreadyQueued -> DiscoveryAcquisitionPhase.Queued
    ProviderDiscoveryOperationStatus.AlreadyOwned -> DiscoveryAcquisitionPhase.Complete
    ProviderDiscoveryOperationStatus.MissingSource -> DiscoveryAcquisitionPhase.MissingSource
    ProviderDiscoveryOperationStatus.Saved -> DiscoveryAcquisitionPhase.Saved
    ProviderDiscoveryOperationStatus.Rejected -> DiscoveryAcquisitionPhase.Rejected
    ProviderDiscoveryOperationStatus.Conflict -> DiscoveryAcquisitionPhase.Conflict
    else -> DiscoveryAcquisitionPhase.Uncertain
}

fun acquisitionPhase(raw: String?): DiscoveryAcquisitionPhase = when (raw?.lowercase()) {
    "unreleased" -> DiscoveryAcquisitionPhase.WaitingForRelease
    "queued", "pending", "adding" -> DiscoveryAcquisitionPhase.Queued
    "searching" -> DiscoveryAcquisitionPhase.Searching
    "processing" -> DiscoveryAcquisitionPhase.Processing
    "downloading" -> DiscoveryAcquisitionPhase.Downloading
    "importing", "moving" -> DiscoveryAcquisitionPhase.Importing
    "partial" -> DiscoveryAcquisitionPhase.Partial
    "complete", "completed", "available", "downloaded", "added" -> DiscoveryAcquisitionPhase.Complete
    "blocked", "review_required" -> DiscoveryAcquisitionPhase.Blocked
    "failed" -> DiscoveryAcquisitionPhase.Failed
    "cancelled", "canceled" -> DiscoveryAcquisitionPhase.Cancelled
    "download_source_missing" -> DiscoveryAcquisitionPhase.MissingSource
    "saved", "inlibrary" -> DiscoveryAcquisitionPhase.Saved
    else -> DiscoveryAcquisitionPhase.Unknown
}

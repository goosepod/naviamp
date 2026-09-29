package app.naviamp.storage

import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.PersistedDownloadJob
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Portable SQLDelight implementation of keep-downloaded policy persistence. */
class StorageKeepDownloadedStore(
    private val queries: NaviampStorageQueries,
    private val nowEpochMillis: () -> Long,
) : KeepDownloadedRepository {
    private val jobJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun savedDownloadJobs(sourceId: String): List<PersistedDownloadJob> =
        queries.selectDownloadJobs(sourceId).executeAsList().mapNotNull { encoded ->
            runCatching { jobJson.decodeFromString<PersistedDownloadJob>(encoded) }.getOrNull()
        }.filter { it.sourceId == sourceId }

    override fun saveDownloadJob(job: PersistedDownloadJob) {
        queries.upsertDownloadJob(job.sourceId, job.job.id, jobJson.encodeToString(job))
    }

    override fun deleteDownloadJob(sourceId: String, jobId: String) {
        queries.deleteDownloadJob(sourceId, jobId)
    }

    override fun keepDownloadedPolicies(sourceId: String): List<KeepDownloadedCollectionPolicy> =
        queries.selectKeepDownloadedPolicies(sourceId).executeAsList().map(::toPolicy)

    override fun keepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ): KeepDownloadedCollectionPolicy? =
        queries.selectKeepDownloadedPolicy(sourceId, kind.name, collectionId).executeAsOneOrNull()?.let(::toPolicy)

    override fun upsertKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy) {
        queries.upsertKeepDownloadedPolicy(
            policy.sourceId,
            policy.kind.name,
            policy.collectionId,
            policy.name,
            if (policy.removeUnneededFiles) 1L else 0L,
            nowEpochMillis(),
        )
    }

    override fun deleteKeepDownloadedPolicy(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ) {
        queries.deleteKeepDownloadedPolicy(sourceId, kind.name, collectionId)
    }

    override fun keepDownloadedTrackIds(
        sourceId: String,
        kind: KeepDownloadedCollectionKind,
        collectionId: String,
    ): Set<String> = queries.selectKeepDownloadedTrackIds(sourceId, kind.name, collectionId).executeAsList().toSet()

    override fun replaceKeepDownloadedTrackIds(policy: KeepDownloadedCollectionPolicy, trackIds: Set<String>) {
        queries.transaction {
            upsertKeepDownloadedPolicy(policy)
            queries.deleteKeepDownloadedCollectionTracks(policy.sourceId, policy.kind.name, policy.collectionId)
            trackIds.forEach {
                queries.insertKeepDownloadedCollectionTrack(policy.sourceId, policy.kind.name, policy.collectionId, it)
            }
        }
    }

    override fun managedKeepDownloadedTrackIds(sourceId: String): Set<String> =
        queries.selectManagedKeepDownloadedTrackIds(sourceId).executeAsList().toSet()

    override fun markManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) {
        queries.transaction { trackIds.forEach { queries.insertManagedKeepDownloadedTrack(sourceId, it) } }
    }

    override fun unmarkManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) {
        queries.transaction { trackIds.forEach { queries.deleteManagedKeepDownloadedTrack(sourceId, it) } }
    }

    override fun manuallyRetainedTrackIds(sourceId: String): Set<String> =
        queries.selectManualDownloadRetentionTrackIds(sourceId).executeAsList().toSet()

    override fun retainManualTrack(sourceId: String, trackId: String) {
        queries.insertManualDownloadRetention(sourceId, trackId)
    }

    override fun releaseTrackRetention(sourceId: String, trackId: String) {
        queries.deleteDownloadRetentionForTrack(sourceId, trackId)
    }

    private fun toPolicy(row: Keep_downloaded_collection) = KeepDownloadedCollectionPolicy(
        sourceId = row.source_id,
        kind = KeepDownloadedCollectionKind.valueOf(row.collection_kind),
        collectionId = row.collection_id,
        name = row.name,
        removeUnneededFiles = row.remove_unneeded_files != 0L,
    )
}

package app.naviamp.storage

import app.naviamp.domain.provider.*
import kotlinx.serialization.json.Json

/** Portable request persistence; never participates in the replayable provider-action queue. */
class StorageDiscoveryAcquisitionStore(
    private val queries: NaviampStorageQueries,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : DiscoveryAcquisitionRepository {
    override fun records(scope: DiscoveryAcquisitionScope): List<DiscoveryAcquisitionRecord> =
        queries.selectDiscoveryAcquisitions(scope.sourceId, scope.accountKey).executeAsList()
            .mapNotNull { decode(it) }
    override fun record(scope: DiscoveryAcquisitionScope, id: String): DiscoveryAcquisitionRecord? =
        queries.selectDiscoveryAcquisition(scope.sourceId, scope.accountKey, id).executeAsOneOrNull()?.let {
            // Unknown/corrupt durable intent must not be mistaken for permission to submit again.
            json.decodeFromString<DiscoveryAcquisitionRecord>(it)
        }
    override fun save(scope: DiscoveryAcquisitionScope, record: DiscoveryAcquisitionRecord) {
        require(record.id.isNotBlank() && scope.sourceId.isNotBlank() && scope.accountKey.isNotBlank())
        queries.upsertDiscoveryAcquisition(scope.sourceId, scope.accountKey, record.id,
            json.encodeToString(record), record.createdAtMillis)
    }
    private fun decode(payload: String): DiscoveryAcquisitionRecord? = runCatching {
        json.decodeFromString<DiscoveryAcquisitionRecord>(payload)
    }.getOrNull()
}

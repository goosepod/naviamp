package app.naviamp.android

import app.naviamp.domain.cache.ProviderResponseStore
import app.naviamp.storage.NaviampStorageQueries

class AndroidProviderResponseStore(
    private val queries: NaviampStorageQueries,
) : ProviderResponseStore {
    override fun cachedResponse(cacheKey: String): String? =
        queries.selectResponse(cacheKey).executeAsOneOrNull()

    override fun touchResponse(
        cacheKey: String,
        lastAccessedEpochMillis: Long,
    ) {
        queries.touchResponse(lastAccessedEpochMillis, cacheKey)
    }

    override fun upsertResponse(
        cacheKey: String,
        providerId: String,
        resourceType: String,
        resourceId: String,
        payload: String,
        createdAtEpochMillis: Long,
        lastAccessedEpochMillis: Long,
    ) {
        queries.upsertResponse(
            cache_key = cacheKey,
            provider_id = providerId,
            resource_type = resourceType,
            resource_id = resourceId,
            payload = payload,
            created_at_epoch_millis = createdAtEpochMillis,
            last_accessed_epoch_millis = lastAccessedEpochMillis,
        )
    }

    override fun deleteResponsesByProviderAndType(
        providerId: String,
        resourceType: String,
    ) {
        queries.deleteResponsesByProviderAndType(
            provider_id = providerId,
            resource_type = resourceType,
        )
    }

    override fun deleteResponseByProviderTypeAndId(
        providerId: String,
        resourceType: String,
        resourceId: String,
    ) {
        queries.deleteResponseByProviderTypeAndId(
            provider_id = providerId,
            resource_type = resourceType,
            resource_id = resourceId,
        )
    }
}

package app.naviamp.domain.cache

import app.naviamp.domain.provider.MediaProvider

interface ProviderResponseStore {
    fun cachedResponse(cacheKey: String): String?

    fun touchResponse(
        cacheKey: String,
        lastAccessedEpochMillis: Long,
    )

    fun upsertResponse(
        cacheKey: String,
        providerId: String,
        resourceType: String,
        resourceId: String,
        payload: String,
        createdAtEpochMillis: Long,
        lastAccessedEpochMillis: Long,
    )

    fun deleteResponsesByProviderAndType(
        providerId: String,
        resourceType: String,
    )

    fun deleteResponseByProviderTypeAndId(
        providerId: String,
        resourceType: String,
        resourceId: String,
    )
}

class ProviderResponseCacheService(
    private val store: ProviderResponseStore,
    private val nowMillis: () -> Long,
) : ProviderResponseCacheRepository {
    override suspend fun <T> cachedProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T {
        val key = cacheKey(provider, resourceType, resourceId)
        store.cachedResponse(key)?.let { payload ->
            store.touchResponse(key, nowMillis())
            return decode(payload)
        }

        val value = fetch()
        val now = nowMillis()
        store.upsertResponse(
            cacheKey = key,
            providerId = provider.cacheNamespace,
            resourceType = resourceType,
            resourceId = resourceId,
            payload = encode(value),
            createdAtEpochMillis = now,
            lastAccessedEpochMillis = now,
        )
        return value
    }

    override fun invalidateProviderResponses(
        provider: MediaProvider,
        resourceType: String,
    ) {
        store.deleteResponsesByProviderAndType(
            providerId = provider.cacheNamespace,
            resourceType = resourceType,
        )
    }

    override fun invalidateProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
    ) {
        store.deleteResponseByProviderTypeAndId(
            providerId = provider.cacheNamespace,
            resourceType = resourceType,
            resourceId = resourceId,
        )
    }

    private fun cacheKey(provider: MediaProvider, resourceType: String, resourceId: String): String =
        "${provider.cacheNamespace}:$resourceType:$resourceId"
}

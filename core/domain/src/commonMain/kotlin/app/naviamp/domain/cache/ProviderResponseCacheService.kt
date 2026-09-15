package app.naviamp.domain.cache

import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CancellationException

data class CachedProviderResponse(
    val payload: String,
    val createdAtEpochMillis: Long,
)

interface ProviderResponseStore {
    fun cachedResponse(cacheKey: String): CachedProviderResponse?

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
        store.cachedResponse(key)?.let { cached ->
            store.touchResponse(key, nowMillis())
            return decode(cached.payload)
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

    override suspend fun <T> revalidatedProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        maxAgeMillis: Long,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T {
        require(maxAgeMillis >= 0) { "The provider response maximum age cannot be negative." }
        val key = cacheKey(provider, resourceType, resourceId)
        val cached = store.cachedResponse(key)
        val now = nowMillis()
        if (cached != null && now - cached.createdAtEpochMillis in 0 until maxAgeMillis) {
            store.touchResponse(key, now)
            return decode(cached.payload)
        }
        return try {
            fetch().also { value ->
                val refreshedAt = nowMillis()
                store.upsertResponse(
                    cacheKey = key,
                    providerId = provider.cacheNamespace,
                    resourceType = resourceType,
                    resourceId = resourceId,
                    payload = encode(value),
                    createdAtEpochMillis = refreshedAt,
                    lastAccessedEpochMillis = refreshedAt,
                )
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            cached?.let {
                store.touchResponse(key, nowMillis())
                return decode(it.payload)
            }
            throw cause
        }
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

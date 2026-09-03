package app.naviamp.domain.cache

import app.naviamp.domain.provider.MediaProvider

data class StoredObjectBytes(
    val key: String,
    val sizeBytes: Long,
)

interface ObjectByteStore {
    suspend fun objectBytes(key: String): ByteArray?

    suspend fun objectKeys(): List<String> = emptyList()

    suspend fun writeObjectBytes(
        key: String,
        bytes: ByteArray,
    ): StoredObjectBytes

    fun deleteObjectBytes(key: String)
}

class ObjectByteStoreService(
    private val store: ObjectByteStore,
) {
    suspend fun cachedBytes(key: String): ByteArray? =
        store.objectBytes(key)

    suspend fun bytes(
        key: String,
        fetch: suspend () -> ByteArray,
    ): ByteArray =
        store.objectBytes(key)
            ?: fetch().also { bytes -> store.writeObjectBytes(key, bytes) }

    suspend fun bytes(
        key: String,
        equivalentKey: (String) -> Boolean,
        fetch: suspend () -> ByteArray,
    ): ByteArray = store.objectBytes(key)
        ?: store.objectKeys()
            .firstOrNull { candidate -> candidate != key && equivalentKey(candidate) }
            ?.let { legacyKey -> store.objectBytes(legacyKey) }
            ?.also { bytes -> store.writeObjectBytes(key, bytes) }
        ?: fetch().also { bytes -> store.writeObjectBytes(key, bytes) }

    suspend fun bytesForProvider(
        provider: MediaProvider,
        url: String,
        fetch: suspend () -> ByteArray,
    ): ByteArray {
        val stableKey = provider.artworkCacheKey(url)
        return bytes(
            key = stableKey,
            equivalentKey = { candidate -> provider.artworkCacheKey(candidate) == stableKey },
            fetch = fetch,
        )
    }

}

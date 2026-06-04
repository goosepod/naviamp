package app.naviamp.domain.cache

import app.naviamp.domain.network.KtorSharedHttpClient

data class StoredObjectBytes(
    val key: String,
    val sizeBytes: Long,
)

interface ObjectByteStore {
    suspend fun objectBytes(key: String): ByteArray?

    suspend fun writeObjectBytes(
        key: String,
        bytes: ByteArray,
    ): StoredObjectBytes

    fun deleteObjectBytes(key: String)
}

class ObjectByteStoreService(
    private val store: ObjectByteStore,
    private val httpClient: KtorSharedHttpClient = KtorSharedHttpClient(),
) {
    suspend fun bytes(
        key: String,
        fetch: suspend () -> ByteArray,
    ): ByteArray =
        store.objectBytes(key)
            ?: fetch().also { bytes -> store.writeObjectBytes(key, bytes) }

    suspend fun remoteBytes(url: String): ByteArray =
        bytes(url) {
            httpClient.getBytes(url) ?: throw IllegalStateException("Could not download bytes.")
        }
}

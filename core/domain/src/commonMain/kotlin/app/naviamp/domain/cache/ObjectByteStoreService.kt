package app.naviamp.domain.cache

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
) {
    suspend fun cachedBytes(key: String): ByteArray? =
        store.objectBytes(key)

    suspend fun bytes(
        key: String,
        fetch: suspend () -> ByteArray,
    ): ByteArray =
        store.objectBytes(key)
            ?: fetch().also { bytes -> store.writeObjectBytes(key, bytes) }

}

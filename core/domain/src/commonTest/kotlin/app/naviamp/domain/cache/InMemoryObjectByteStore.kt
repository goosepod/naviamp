package app.naviamp.domain.cache

class InMemoryObjectByteStore : ObjectByteStore {
    private val objects = mutableMapOf<String, ByteArray>()

    override suspend fun objectBytes(key: String): ByteArray? =
        objects[key]?.copyOf()

    override suspend fun writeObjectBytes(key: String, bytes: ByteArray): StoredObjectBytes {
        objects[key] = bytes.copyOf()
        return StoredObjectBytes(key = key, sizeBytes = bytes.size.toLong())
    }

    override fun deleteObjectBytes(key: String) {
        objects.remove(key)
    }
}

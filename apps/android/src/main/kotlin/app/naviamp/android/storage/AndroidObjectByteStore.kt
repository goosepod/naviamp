package app.naviamp.android

import app.naviamp.domain.cache.ObjectByteStore
import app.naviamp.domain.cache.StoredObjectBytes
import app.naviamp.domain.cache.MaximumPersistentArtworkCacheBytes
import app.naviamp.storage.NaviampStorageQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidObjectByteStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
    private val maxImageCacheBytes: Long = MaximumPersistentArtworkCacheBytes,
) : ObjectByteStore {
    override suspend fun objectBytes(key: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val now = nowMillis()
            queries.selectImage(key).executeAsOneOrNull()?.also {
                queries.touchImage(now, key)
            }
        }

    override suspend fun writeObjectBytes(key: String, bytes: ByteArray): StoredObjectBytes =
        withContext(Dispatchers.IO) {
            val now = nowMillis()
            queries.upsertImage(
                url = key,
                bytes = bytes,
                size_bytes = bytes.size.toLong(),
                created_at_epoch_millis = now,
                last_accessed_epoch_millis = now,
            )
            trim()
            StoredObjectBytes(key = key, sizeBytes = bytes.size.toLong())
        }

    override fun deleteObjectBytes(key: String) {
        queries.deleteImage(key)
    }

    private fun trim() {
        var cacheSize = queries.imageCacheSize().executeAsOne()
        while (cacheSize > maxImageCacheBytes) {
            val oldest = queries.oldestImages(100).executeAsList()
            if (oldest.isEmpty()) return
            oldest.forEach { image ->
                if (cacheSize <= maxImageCacheBytes) return
                queries.deleteImage(image.url)
                cacheSize -= image.size_bytes
            }
        }
    }
}

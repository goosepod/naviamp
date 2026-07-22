package app.naviamp.storage

import app.naviamp.domain.cache.ObjectByteStore
import app.naviamp.domain.cache.StoredObjectBytes
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class StorageObjectByteStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
    private val maxImageCacheBytes: Long,
    private val workContext: CoroutineContext = EmptyCoroutineContext,
) : ObjectByteStore {
    override suspend fun objectBytes(key: String): ByteArray? =
        withContext(workContext + NonCancellable) {
            val now = nowMillis()
            queries.selectImage(key).executeAsOneOrNull()?.also {
                queries.touchImage(now, key)
            }
        }

    override suspend fun writeObjectBytes(key: String, bytes: ByteArray): StoredObjectBytes =
        withContext(workContext + NonCancellable) {
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

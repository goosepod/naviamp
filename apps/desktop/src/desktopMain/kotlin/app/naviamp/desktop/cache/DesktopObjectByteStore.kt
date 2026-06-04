package app.naviamp.desktop

import app.naviamp.domain.cache.ObjectByteStore
import app.naviamp.domain.cache.StoredObjectBytes
import app.naviamp.storage.NaviampStorageQueries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class DesktopObjectByteStore(
    private val queries: NaviampStorageQueries,
    private val nowMillis: () -> Long,
    private val afterWrite: () -> Unit = {},
) : ObjectByteStore {
    override suspend fun objectBytes(key: String): ByteArray? =
        withContext(Dispatchers.IO + NonCancellable) {
            val now = nowMillis()
            queries.selectImage(key).executeAsOneOrNull()?.also {
                queries.touchImage(now, key)
            }
        }

    override suspend fun writeObjectBytes(key: String, bytes: ByteArray): StoredObjectBytes =
        withContext(Dispatchers.IO + NonCancellable) {
            val now = nowMillis()
            queries.upsertImage(
                url = key,
                bytes = bytes,
                size_bytes = bytes.size.toLong(),
                created_at_epoch_millis = now,
                last_accessed_epoch_millis = now,
            )
            afterWrite()
            StoredObjectBytes(key = key, sizeBytes = bytes.size.toLong())
        }

    override fun deleteObjectBytes(key: String) {
        queries.deleteImage(key)
    }
}

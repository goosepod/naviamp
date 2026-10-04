package app.naviamp.app

import app.naviamp.domain.provider.ProviderMediaByteResponse

/** Shared provider byte source; the host never receives a provider URL or chooses media identity. */
interface NaviampCastMediaByteSource {
    suspend fun prepare(resource: NaviampCastMediaResource) = Unit
    suspend fun release(resource: NaviampCastMediaResource) = Unit
    suspend fun clear() = Unit
    suspend fun stream(
        resource: NaviampCastMediaResource,
        range: NaviampCastRequestedRange?,
        headOnly: Boolean,
        onResponse: suspend (ProviderMediaByteResponse) -> Unit,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean
}

/** Opaque temporary byte storage. Core decides what to stage and when to release it. */
fun interface NaviampCastMediaStore {
    suspend fun write(writeBytes: suspend (NaviampCastMediaWriter) -> Boolean): NaviampCastStoredMedia
}

fun interface NaviampCastMediaWriter {
    suspend fun write(bytes: ByteArray, count: Int)
}

interface NaviampCastStoredMedia {
    val sizeBytes: Long
    suspend fun read(offset: Long, count: Int): ByteArray
    suspend fun delete()
}

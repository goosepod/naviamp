package app.naviamp.presentation

import app.naviamp.app.NaviampCastMediaByteSource
import app.naviamp.app.NaviampCastMediaKind
import app.naviamp.app.NaviampCastMediaResource
import app.naviamp.app.NaviampCastRequestedRange
import app.naviamp.app.resolve
import app.naviamp.app.toHttpHeader
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.CoverArtSize
import app.naviamp.domain.provider.ProviderMediaByteResponse
import app.naviamp.app.NaviampCastMediaStore
import app.naviamp.app.NaviampCastStoredMedia
import app.naviamp.domain.StreamQuality
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Uses the active shared provider session; credential-bearing URLs never cross the endpoint. */
class NaviampCoreCastMediaByteSource(
    private val providers: NaviampCoreMediaProviderSource,
    private val store: NaviampCastMediaStore? = null,
    private val maxTrackBytes: Long = 256L * 1024 * 1024,
    private val maxTotalBytes: Long = 512L * 1024 * 1024,
) : NaviampCastMediaByteSource {
    private val prepared = mutableMapOf<NaviampCastMediaResource, NaviampCastStoredMedia>()
    private val preparation = Mutex()

    override suspend fun prepare(resource: NaviampCastMediaResource) = preparation.withLock {
        if (resource.kind != NaviampCastMediaKind.Track || resource.quality !is StreamQuality.Transcoded ||
            resource in prepared) return@withLock
        val provider = checkNotNull(providers.current()?.takeIf { it.cacheNamespace == resource.sourceId })
        val budget = minOf(maxTrackBytes, maxTotalBytes - prepared.values.sumOf { it.sizeBytes })
        require(budget > 0)
        var received = 0L
        var expectedLength: Long? = null
        val file = withTimeout(30_000) {
            checkNotNull(store) { "Cast transcoding requires temporary byte storage." }.write { writer ->
                provider.streamTrackBytes(StreamRequest(TrackId(resource.id), resource.quality), null, false,
                    onResponse = { response ->
                        check(response.statusCode == 200)
                        val length = response.contentLength
                        check(length == null || length in 0..budget)
                        expectedLength = length
                    },
                    writeChunk = { bytes, count ->
                        check(count >= 0 && count <= bytes.size && count.toLong() <= budget - received)
                        writer.write(bytes, count)
                        received += count
                    },
                ) && received > 0 && (expectedLength == null || expectedLength == received)
            }
        }
        if (providers.current()?.cacheNamespace != resource.sourceId || file.sizeBytes != received) {
            withContext(NonCancellable) { file.delete() }
            error("Cast media source changed during preparation.")
        }
        prepared[resource] = file
    }

    override suspend fun release(resource: NaviampCastMediaResource) = preparation.withLock {
        prepared.remove(resource)?.delete()
        Unit
    }

    override suspend fun clear() = preparation.withLock {
        val files = prepared.values.toList()
        prepared.clear()
        withContext(NonCancellable) { files.forEach { it.delete() } }
    }

    override suspend fun stream(
        resource: NaviampCastMediaResource,
        range: NaviampCastRequestedRange?,
        headOnly: Boolean,
        onResponse: suspend (ProviderMediaByteResponse) -> Unit,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean {
        val provider = providers.current()?.takeIf { it.cacheNamespace == resource.sourceId } ?: return false
        if (resource.kind == NaviampCastMediaKind.Track && resource.quality is StreamQuality.Transcoded) {
            val file = preparation.withLock { prepared[resource] } ?: return false
            val resolved = range?.resolve(file.sizeBytes)
            if (range != null && resolved == null) {
                onResponse(ProviderMediaByteResponse(416, "audio/mpeg", 0, "bytes */${file.sizeBytes}"))
                return true
            }
            val first = resolved?.firstByte ?: 0L
            val length = resolved?.length ?: file.sizeBytes
            onResponse(ProviderMediaByteResponse(if (resolved == null) 200 else 206,
                "audio/mpeg", length, resolved?.contentRange))
            if (!headOnly) {
                var offset = first
                val end = first + length
                while (offset < end) {
                    val bytes = file.read(offset, minOf(CHUNK_BYTES.toLong(), end - offset).toInt())
                    check(bytes.isNotEmpty() && bytes.size.toLong() <= end - offset)
                    writeChunk(bytes, bytes.size)
                    offset += bytes.size
                }
            }
            return true
        }
        return when (resource.kind) {
            NaviampCastMediaKind.Track -> provider.streamTrackBytes(
                request = StreamRequest(TrackId(resource.id), resource.quality),
                rangeHeader = range?.toHttpHeader(),
                headOnly = headOnly,
                onResponse = onResponse,
                writeChunk = writeChunk,
            )
            NaviampCastMediaKind.Artwork -> {
                val url = provider.coverArtUrl(resource.id, CoverArtSize.Thumbnail)
                val bytes = provider.bytesForOwnedUrl(url)?.takeIf { it.size <= MAX_ARTWORK_BYTES } ?: return false
                val resolved = range?.resolve(bytes.size.toLong())
                val contentType = artworkContentType(bytes)
                if (range != null && resolved == null) {
                    onResponse(ProviderMediaByteResponse(416, contentType, 0, "bytes */${bytes.size}"))
                    return true
                }
                val first = resolved?.firstByte?.toInt() ?: 0
                val endExclusive = (resolved?.lastByteInclusive?.toInt() ?: bytes.lastIndex) + 1
                onResponse(ProviderMediaByteResponse(
                    statusCode = if (resolved == null) 200 else 206,
                    contentType = contentType,
                    contentLength = (endExclusive - first).toLong(),
                    contentRange = resolved?.contentRange,
                ))
                if (!headOnly) {
                    var offset = first
                    while (offset < endExclusive) {
                        val count = minOf(CHUNK_BYTES, endExclusive - offset)
                        writeChunk(bytes.copyOfRange(offset, offset + count), count)
                        offset += count
                    }
                }
                true
            }
        }
    }

    private fun artworkContentType(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 12 && bytes.decodeToString(0, 4) == "RIFF" &&
            bytes.decodeToString(8, 12) == "WEBP" -> "image/webp"
        bytes.size >= 4 && bytes.decodeToString(0, 4) == "GIF8" -> "image/gif"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
        const val CHUNK_BYTES = 64 * 1024
    }
}

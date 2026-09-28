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

/** Uses the active shared provider session; credential-bearing URLs never cross the endpoint. */
class NaviampCoreCastMediaByteSource(
    private val providers: NaviampCoreMediaProviderSource,
) : NaviampCastMediaByteSource {
    override suspend fun stream(
        resource: NaviampCastMediaResource,
        range: NaviampCastRequestedRange?,
        headOnly: Boolean,
        onResponse: suspend (ProviderMediaByteResponse) -> Unit,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean {
        val provider = providers.current()?.takeIf { it.cacheNamespace == resource.sourceId } ?: return false
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

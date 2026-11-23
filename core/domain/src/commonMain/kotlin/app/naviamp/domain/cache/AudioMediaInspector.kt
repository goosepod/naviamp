package app.naviamp.domain.cache

/** Containers recognized from saved bytes. Recognizing a container does not identify its codec. */
enum class ObservedAudioContainer(val contentType: String, val fileExtension: String) {
    Mp3("audio/mpeg", ".mp3"),
    Aac("audio/aac", ".aac"),
    Flac("audio/flac", ".flac"),
    Ogg("audio/ogg", ".ogg"),
    Mp4("audio/mp4", ".m4a"),
    Wav("audio/wav", ".wav"),
}

/**
 * Observations of a completed transfer. Truncation is reported only where the container declares
 * its own extent (MP4 boxes, Ogg pages, RIFF size, ID3 tag size); other formats rely on the
 * transport's declared-length check and cannot prove completeness.
 */
data class ObservedAudioMedia(
    val receivedBytes: Long,
    val container: ObservedAudioContainer?,
    val isErrorDocument: Boolean,
    val isTruncated: Boolean,
) {
    val isAcceptable: Boolean
        get() = receivedBytes > 0L && !isErrorDocument && !isTruncated

    val fileExtension: String
        get() = container?.fileExtension ?: NeutralAudioExtension

    /** An expected type the probe could have recognized but did not observe is discarded. */
    fun savedContentType(expectedContentType: String?): String? =
        container?.contentType ?: expectedContentType.takeIf { it.audioExtension() == NeutralAudioExtension }
}

/** Bounded streaming probe: it keeps only small header windows, never the whole response. */
class AudioMediaInspector {
    private class Window(
        val offset: Long,
        length: Int,
        val minimumAtEnd: Int = length,
        val onComplete: (ByteArray, Int) -> Unit,
    ) {
        val bytes = ByteArray(length)
        var filled = 0
    }

    private var received = 0L
    private val documentPrefix = ByteArray(DocumentPrefixBytes)
    private var documentPrefixSize = 0
    private var window: Window? = Window(0L, SignatureBytes, minimumAtEnd = 1) { bytes, size -> identify(0L, bytes, size, 0) }
    private var container: ObservedAudioContainer? = null
    private var declaredEnd: Long? = null
    private var oggEndOfStream: Boolean? = null

    fun accept(bytes: ByteArray, count: Int) {
        require(count in 0..bytes.size)
        if (documentPrefixSize < documentPrefix.size) {
            val take = minOf(count, documentPrefix.size - documentPrefixSize)
            bytes.copyInto(documentPrefix, documentPrefixSize, 0, take)
            documentPrefixSize += take
        }
        val chunkEnd = received + count
        while (true) {
            val current = window ?: break
            val from = current.offset + current.filled
            val needed = current.bytes.size - current.filled
            val take = when {
                // A window may start inside bytes already consumed, such as audio right after a short ID3 tag.
                from < documentPrefixSize -> minOf(needed, (documentPrefixSize - from).toInt()).also { take ->
                    documentPrefix.copyInto(current.bytes, current.filled, from.toInt(), from.toInt() + take)
                }
                from in received until chunkEnd -> minOf(needed, (chunkEnd - from).toInt()).also { take ->
                    val start = (from - received).toInt()
                    bytes.copyInto(current.bytes, current.filled, start, start + take)
                }
                from < received -> { window = null; break }
                else -> break
            }
            current.filled += take
            if (current.filled < current.bytes.size) break
            window = null
            current.onComplete(current.bytes, current.filled)
        }
        received = chunkEnd
    }

    fun finish(): ObservedAudioMedia {
        window?.takeIf { it.filled >= it.minimumAtEnd && it.filled > 0 }?.let { pending ->
            window = null
            pending.onComplete(pending.bytes, pending.filled)
        }
        val truncated = (declaredEnd?.let { received < it } ?: false) || oggEndOfStream == false
        return ObservedAudioMedia(
            receivedBytes = received,
            container = container,
            isErrorDocument = isErrorDocument(),
            isTruncated = truncated,
        )
    }

    private fun identify(offset: Long, bytes: ByteArray, size: Int, id3Depth: Int) {
        fun matches(at: Int, text: String) =
            size >= at + text.length && text.indices.all { bytes[at + it] == text[it].code.toByte() }
        fun u(at: Int) = bytes[at].toInt() and 0xFF
        when {
            matches(0, "ID3") && size >= 10 && id3Depth < MaxId3Tags -> {
                val tagSize = (u(6) shl 21) or (u(7) shl 14) or (u(8) shl 7) or u(9)
                val footer = if (u(5) and 0x10 != 0) 10 else 0
                val tagEnd = offset + 10 + tagSize + footer
                declaredEnd = tagEnd
                container = ObservedAudioContainer.Mp3
                window = Window(tagEnd, SignatureBytes, minimumAtEnd = 1) { next, nextSize ->
                    val fallback = container
                    container = null
                    identify(tagEnd, next, nextSize, id3Depth + 1)
                    if (container == null) container = fallback
                }
            }
            matches(0, "fLaC") -> container = ObservedAudioContainer.Flac
            matches(0, "OggS") && size >= OggHeaderBytes -> {
                container = ObservedAudioContainer.Ogg
                readOggPage(offset, bytes)
            }
            matches(4, "ftyp") -> {
                container = ObservedAudioContainer.Mp4
                readMp4Box(offset, bytes, size)
            }
            matches(0, "RIFF") && matches(8, "WAVE") -> {
                container = ObservedAudioContainer.Wav
                val riffSize = (u(4).toLong()) or (u(5).toLong() shl 8) or (u(6).toLong() shl 16) or (u(7).toLong() shl 24)
                // Streaming encoders write 0 or a near-maximum placeholder before the length is known.
                if (riffSize in 4L until 0xFFFF_FFF0L) declaredEnd = offset + 8 + riffSize
            }
            size >= 2 && u(0) == 0xFF && (u(1) and 0xF6) == 0xF0 -> container = ObservedAudioContainer.Aac
            size >= 2 && u(0) == 0xFF && (u(1) and 0xE0) == 0xE0 && (u(1) shr 1 and 0x03) != 0 ->
                container = ObservedAudioContainer.Mp3
        }
    }

    private fun readMp4Box(offset: Long, header: ByteArray, size: Int) {
        if (size < 8) return
        val boxSize = header.bigEndian(0, 4)
        when {
            boxSize == 0L -> declaredEnd = null
            boxSize == 1L -> if (size >= 16) {
                nextMp4Box(offset, header.bigEndian(8, 8), 16)
            } else {
                window = Window(offset + 8, 8) { large, _ -> nextMp4Box(offset, large.bigEndian(0, 8), 16) }
            }
            else -> nextMp4Box(offset, boxSize, 8)
        }
    }

    private fun nextMp4Box(offset: Long, boxSize: Long, headerSize: Int) {
        if (boxSize < headerSize) {
            declaredEnd = null
            return
        }
        val next = offset + boxSize
        declaredEnd = next
        window = Window(next, 16, minimumAtEnd = 8) { bytes, size -> readMp4Box(next, bytes, size) }
    }

    private fun readOggPage(offset: Long, header: ByteArray) {
        if (!(header[0] == 'O'.code.toByte() && header[1] == 'g'.code.toByte() &&
                header[2] == 'g'.code.toByte() && header[3] == 'S'.code.toByte())) {
            // Unknown data after a page: stop judging rather than reject unfamiliar framing.
            declaredEnd = null
            oggEndOfStream = null
            return
        }
        val endOfStream = header[5].toInt() and 0x04 != 0
        val segmentCount = header[26].toInt() and 0xFF
        val segmentsOffset = offset + OggHeaderBytes
        declaredEnd = segmentsOffset + segmentCount
        oggEndOfStream = false
        val onSegments: (ByteArray, Int) -> Unit = { segments, _ ->
            var payload = 0L
            for (index in 0 until segmentCount) payload += segments[index].toInt() and 0xFF
            val pageEnd = segmentsOffset + segmentCount + payload
            declaredEnd = pageEnd
            oggEndOfStream = endOfStream
            window = Window(pageEnd, OggHeaderBytes) { next, _ -> readOggPage(pageEnd, next) }
        }
        if (segmentCount == 0) onSegments(ByteArray(0), 0)
        else window = Window(segmentsOffset, segmentCount, onComplete = onSegments)
    }

    private fun isErrorDocument(): Boolean {
        val start = documentPrefix.copyOf(documentPrefixSize).decodeToString()
            .trimStart('﻿', ' ', '\t', '\r', '\n')
            .lowercase()
        return ErrorDocumentPrefixes.any(start::startsWith)
    }
}

private fun ByteArray.bigEndian(at: Int, length: Int): Long {
    var value = 0L
    for (index in at until at + length) value = (value shl 8) or (this[index].toLong() and 0xFF)
    return value
}

private const val NeutralAudioExtension = ".audio"
private const val SignatureBytes = 32
private const val OggHeaderBytes = 27
private const val DocumentPrefixBytes = 512
private const val MaxId3Tags = 4
private val ErrorDocumentPrefixes = listOf(
    "<!doctype html", "<html", "<head", "<body", "<?xml", "<error", "<subsonic-response", "{", "[",
)

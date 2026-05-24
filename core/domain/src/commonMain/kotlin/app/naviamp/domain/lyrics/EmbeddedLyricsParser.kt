package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource

fun embeddedLyricsFromId3v2(bytes: ByteArray): Lyrics? {
    if (bytes.size < Id3HeaderSize) return null
    if (bytes[0].toInt().toChar() != 'I' || bytes[1].toInt().toChar() != 'D' || bytes[2].toInt().toChar() != '3') {
        return null
    }
    val version = bytes[3].toInt() and 0xff
    val tagSize = syncSafeInt(bytes, 6).takeIf { it > 0 } ?: return null
    val tagEnd = (Id3HeaderSize + tagSize).coerceAtMost(bytes.size)
    var offset = Id3HeaderSize
    while (offset + Id3FrameHeaderSize <= tagEnd) {
        val frameId = ascii(bytes, offset, 4)
        if (frameId.isBlank() || frameId.any { it.code == 0 }) break
        val frameSize = if (version >= 4) syncSafeInt(bytes, offset + 4) else bigEndianInt(bytes, offset + 4)
        if (frameSize <= 0 || offset + Id3FrameHeaderSize + frameSize > tagEnd) break
        val contentStart = offset + Id3FrameHeaderSize
        val content = bytes.copyOfRange(contentStart, contentStart + frameSize)
        parseId3LyricsFrame(frameId, content)?.let { return it }
        offset = contentStart + frameSize
    }
    return null
}

private fun parseId3LyricsFrame(frameId: String, content: ByteArray): Lyrics? =
    when (frameId) {
        "USLT" -> parseUsltFrame(content)
        "TXXX" -> parseTxxxLyricsFrame(content)
        else -> null
    }

private fun parseUsltFrame(content: ByteArray): Lyrics? {
    if (content.size < 5) return null
    val encoding = content[0].toInt() and 0xff
    val textStart = 4
    val descriptionEnd = terminatorIndex(content, textStart, encoding)
    val lyricsStart = (descriptionEnd + terminatorSize(encoding)).coerceAtMost(content.size)
    val text = decodeText(content, lyricsStart, content.size, encoding)
    return lyricsFromText(LyricsSource.Embedded, text)
}

private fun parseTxxxLyricsFrame(content: ByteArray): Lyrics? {
    if (content.isEmpty()) return null
    val encoding = content[0].toInt() and 0xff
    val descriptionStart = 1
    val descriptionEnd = terminatorIndex(content, descriptionStart, encoding)
    val description = decodeText(content, descriptionStart, descriptionEnd, encoding)
    if (!description.isLyricsDescription()) return null
    val textStart = (descriptionEnd + terminatorSize(encoding)).coerceAtMost(content.size)
    return lyricsFromText(LyricsSource.Embedded, decodeText(content, textStart, content.size, encoding))
}

private fun String.isLyricsDescription(): Boolean =
    trim().lowercase().replace(" ", "").replace("_", "").replace("-", "") in setOf(
        "lyrics",
        "lyric",
        "unsyncedlyrics",
        "unsynchronizedlyrics",
        "syncedlyrics",
        "synchronizedlyrics",
    )

private fun terminatorIndex(bytes: ByteArray, start: Int, encoding: Int): Int {
    val step = terminatorSize(encoding)
    if (step == 2) {
        var index = start
        while (index + 1 < bytes.size) {
            if (bytes[index].toInt() == 0 && bytes[index + 1].toInt() == 0) return index
            index += 2
        }
        return bytes.size
    }
    for (index in start until bytes.size) {
        if (bytes[index].toInt() == 0) return index
    }
    return bytes.size
}

private fun terminatorSize(encoding: Int): Int =
    if (encoding == 1 || encoding == 2) 2 else 1

private fun decodeText(bytes: ByteArray, start: Int, end: Int, encoding: Int): String {
    if (start >= end || start >= bytes.size) return ""
    val safeEnd = end.coerceAtMost(bytes.size)
    return when (encoding) {
        0 -> buildString {
            for (index in start until safeEnd) append((bytes[index].toInt() and 0xff).toChar())
        }
        1 -> decodeUtf16(bytes, start, safeEnd, byteOrderFromBom = true)
        2 -> decodeUtf16(bytes, start, safeEnd, byteOrderFromBom = false)
        else -> bytes.copyOfRange(start, safeEnd).decodeToString()
    }.trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
}

private fun decodeUtf16(bytes: ByteArray, start: Int, end: Int, byteOrderFromBom: Boolean): String {
    var index = start
    var littleEndian = false
    if (byteOrderFromBom && index + 1 < end) {
        val first = bytes[index].toInt() and 0xff
        val second = bytes[index + 1].toInt() and 0xff
        if (first == 0xff && second == 0xfe) {
            littleEndian = true
            index += 2
        } else if (first == 0xfe && second == 0xff) {
            littleEndian = false
            index += 2
        }
    }
    return buildString {
        while (index + 1 < end) {
            val high: Int
            val low: Int
            if (littleEndian) {
                low = bytes[index].toInt() and 0xff
                high = bytes[index + 1].toInt() and 0xff
            } else {
                high = bytes[index].toInt() and 0xff
                low = bytes[index + 1].toInt() and 0xff
            }
            val codeUnit = (high shl 8) or low
            if (codeUnit != 0) append(codeUnit.toChar())
            index += 2
        }
    }
}

private fun ascii(bytes: ByteArray, start: Int, length: Int): String =
    buildString(length) {
        repeat(length) { index ->
            append((bytes[start + index].toInt() and 0xff).toChar())
        }
    }

private fun syncSafeInt(bytes: ByteArray, start: Int): Int =
    ((bytes[start].toInt() and 0x7f) shl 21) or
        ((bytes[start + 1].toInt() and 0x7f) shl 14) or
        ((bytes[start + 2].toInt() and 0x7f) shl 7) or
        (bytes[start + 3].toInt() and 0x7f)

private fun bigEndianInt(bytes: ByteArray, start: Int): Int =
    ((bytes[start].toInt() and 0xff) shl 24) or
        ((bytes[start + 1].toInt() and 0xff) shl 16) or
        ((bytes[start + 2].toInt() and 0xff) shl 8) or
        (bytes[start + 3].toInt() and 0xff)

private const val Id3HeaderSize = 10
private const val Id3FrameHeaderSize = 10

package app.naviamp.desktop

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

data class AudioTag(
    val key: String,
    val value: String,
)

class AudioTagReader {
    fun read(path: Path): List<AudioTag> {
        if (!path.exists()) return emptyList()
        return runCatching {
            Files.newInputStream(path).use { input ->
                val header = input.readNBytes(HeaderProbeBytes)
                when {
                    header.startsWith("ID3") -> readId3v2(path, header)
                    header.startsWith("fLaC") -> readFlacVorbisComments(path)
                    else -> emptyList()
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun readId3v2(path: Path, header: ByteArray): List<AudioTag> {
        if (header.size < Id3HeaderBytes) return emptyList()
        val majorVersion = header[3].toInt() and 0xFF
        val flags = header[5].toInt() and 0xFF
        val tagSize = synchsafeInt(header, 6)
        if (tagSize <= 0 || tagSize > MaxTagBytes) return emptyList()

        var tagBytes = Files.newInputStream(path).use { input ->
            input.skip(Id3HeaderBytes.toLong())
            input.readNBytes(tagSize)
        }
        if ((flags and 0x80) != 0) {
            tagBytes = removeUnsynchronization(tagBytes)
        }

        var offset = 0
        if ((flags and 0x40) != 0 && tagBytes.size >= 4) {
            val extendedHeaderSize = when (majorVersion) {
                4 -> synchsafeInt(tagBytes, 0)
                else -> tagBytes.intBe(0)
            }
            offset = if (majorVersion == 4) {
                extendedHeaderSize.coerceAtLeast(0)
            } else {
                (extendedHeaderSize + 4).coerceAtLeast(0)
            }
        }

        val tags = mutableListOf<AudioTag>()
        while (offset + Id3FrameHeaderBytes <= tagBytes.size) {
            val frameId = tagBytes.ascii(offset, 4)
            if (!frameId.all { it in 'A'..'Z' || it in '0'..'9' }) break

            val frameSize = when (majorVersion) {
                4 -> synchsafeInt(tagBytes, offset + 4)
                else -> tagBytes.intBe(offset + 4)
            }
            if (frameSize <= 0 || offset + Id3FrameHeaderBytes + frameSize > tagBytes.size) break

            val frameData = tagBytes.copyOfRange(
                offset + Id3FrameHeaderBytes,
                offset + Id3FrameHeaderBytes + frameSize,
            )
            parseId3Frame(frameId, frameData)?.let(tags::add)
            offset += Id3FrameHeaderBytes + frameSize
        }

        return tags.normalizedAndOrdered()
    }

    private fun parseId3Frame(frameId: String, frameData: ByteArray): AudioTag? {
        if (frameData.isEmpty()) return null
        return when {
            frameId == "TXXX" -> parseUserTextFrame(frameData)
            frameId.startsWith("T") -> {
                val key = Id3FrameNames[frameId] ?: frameId
                val value = decodeId3TextFrame(frameData)
                AudioTag(key, value)
            }
            frameId == "COMM" -> {
                val value = decodeCommentFrame(frameData)
                AudioTag("Comment", value)
            }
            frameId == "USLT" -> {
                val value = decodeCommentFrame(frameData)
                AudioTag("Lyrics", value)
            }
            frameId == "POPM" -> AudioTag("Popularimeter", frameData.toHexPreview())
            else -> null
        }?.takeIf { it.value.isNotBlank() }
    }

    private fun parseUserTextFrame(frameData: ByteArray): AudioTag? {
        val text = decodeId3RawTextFrame(frameData)
        if (text.isBlank()) return null
        val parts = text.split('\u0000').map { it.trim() }.filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> AudioTag(parts.first(), parts.drop(1).joinToString("; "))
            else -> AudioTag("User text", text)
        }
    }

    private fun decodeCommentFrame(frameData: ByteArray): String {
        if (frameData.size <= 4) return ""
        val encoding = frameData[0].toInt() and 0xFF
        val textBytes = frameData.copyOfRange(4, frameData.size)
        val text = decodeEncodedText(encoding, textBytes)
        val parts = text.split('\u0000').map { it.trim() }.filter { it.isNotBlank() }
        return parts.lastOrNull() ?: text.trim()
    }

    private fun decodeId3TextFrame(frameData: ByteArray): String {
        return decodeId3RawTextFrame(frameData)
            .replace('\u0000', ';')
            .replace(Regex(";+"), ";")
            .trim(' ', ';', '\uFEFF')
    }

    private fun decodeId3RawTextFrame(frameData: ByteArray): String {
        if (frameData.isEmpty()) return ""
        val encoding = frameData[0].toInt() and 0xFF
        return decodeEncodedText(encoding, frameData.copyOfRange(1, frameData.size))
            .trim(' ', '\uFEFF')
    }

    private fun decodeEncodedText(encoding: Int, bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val charset = when (encoding) {
            1 -> StandardCharsets.UTF_16
            2 -> StandardCharsets.UTF_16BE
            3 -> StandardCharsets.UTF_8
            else -> Charset.forName("ISO-8859-1")
        }
        return String(bytes, charset).trimEnd('\u0000')
    }

    private fun readFlacVorbisComments(path: Path): List<AudioTag> {
        Files.newInputStream(path).use { input ->
            val marker = input.readNBytes(4)
            if (!marker.startsWith("fLaC")) return emptyList()

            var isLastBlock = false
            while (!isLastBlock) {
                val blockHeader = input.readNBytes(4)
                if (blockHeader.size < 4) return emptyList()
                val blockTypeByte = blockHeader[0].toInt() and 0xFF
                isLastBlock = (blockTypeByte and 0x80) != 0
                val blockType = blockTypeByte and 0x7F
                val blockSize = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)
                if (blockSize < 0 || blockSize > MaxTagBytes) return emptyList()
                val blockData = input.readNBytes(blockSize)
                if (blockData.size < blockSize) return emptyList()
                if (blockType == FlacVorbisCommentBlock) {
                    return parseVorbisComments(blockData).normalizedAndOrdered()
                }
            }
        }
        return emptyList()
    }

    private fun parseVorbisComments(bytes: ByteArray): List<AudioTag> {
        var offset = 0
        if (bytes.size < 8) return emptyList()
        val vendorLength = bytes.intLe(offset)
        offset += 4 + vendorLength.coerceAtLeast(0)
        if (offset + 4 > bytes.size) return emptyList()
        val commentCount = bytes.intLe(offset).coerceAtLeast(0)
        offset += 4

        val tags = mutableListOf<AudioTag>()
        repeat(commentCount) {
            if (offset + 4 > bytes.size) return@repeat
            val length = bytes.intLe(offset)
            offset += 4
            if (length <= 0 || offset + length > bytes.size) return@repeat
            val comment = String(bytes, offset, length, StandardCharsets.UTF_8)
            offset += length
            val separator = comment.indexOf('=')
            if (separator > 0) {
                tags += AudioTag(
                    key = comment.take(separator).toDisplayTagName(),
                    value = comment.drop(separator + 1).trim(),
                )
            }
        }
        return tags
    }
}

private fun List<AudioTag>.normalizedAndOrdered(): List<AudioTag> =
    asSequence()
        .mapNotNull { tag ->
            val key = tag.key.toDisplayTagName()
            val value = tag.value.trim()
            if (key.isBlank() || value.isBlank()) null else AudioTag(key, value)
        }
        .groupBy { it.key }
        .map { (key, tags) ->
            AudioTag(
                key = key,
                value = tags.map { it.value }.distinct().joinToString("; "),
            )
        }
        .sortedWith(
            compareBy<AudioTag> { CommonTagOrder[it.key.normalizedTagKey()] ?: Int.MAX_VALUE }
                .thenBy { it.key.lowercase() },
        )

private fun String.toDisplayTagName(): String {
    val normalized = normalizedTagKey()
    return CommonTagNames[normalized] ?: trim()
        .lowercase()
        .split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}

private fun String.normalizedTagKey(): String =
    trim()
        .lowercase()
        .replace(" ", "")
        .replace("_", "")
        .replace("-", "")

private fun ByteArray.startsWith(prefix: String): Boolean =
    size >= prefix.length && ascii(0, prefix.length) == prefix

private fun ByteArray.ascii(offset: Int, length: Int): String =
    String(this, offset, length, StandardCharsets.ISO_8859_1)

private fun ByteArray.intBe(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 4)
        .order(ByteOrder.BIG_ENDIAN)
        .int

private fun ByteArray.intLe(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int

private fun synchsafeInt(bytes: ByteArray, offset: Int): Int {
    if (offset + 4 > bytes.size) return 0
    return ((bytes[offset].toInt() and 0x7F) shl 21) or
        ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
        ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
        (bytes[offset + 3].toInt() and 0x7F)
}

private fun removeUnsynchronization(bytes: ByteArray): ByteArray {
    val output = ArrayList<Byte>(bytes.size)
    var index = 0
    while (index < bytes.size) {
        val current = bytes[index]
        output += current
        if ((current.toInt() and 0xFF) == 0xFF && index + 1 < bytes.size && bytes[index + 1].toInt() == 0) {
            index += 2
        } else {
            index += 1
        }
    }
    return output.toByteArray()
}

private fun ByteArray.toHexPreview(): String =
    take(24).joinToString(" ") { byte -> "%02X".format(byte) }

private val Id3FrameNames = mapOf(
    "TIT2" to "Title",
    "TPE1" to "Artist",
    "TPE2" to "Album Artist",
    "TALB" to "Album",
    "TRCK" to "Track Number",
    "TPOS" to "Disc Number",
    "TDRC" to "Date",
    "TYER" to "Year",
    "TDOR" to "Original Date",
    "TCON" to "Genre",
    "TCOM" to "Composer",
    "TEXT" to "Lyricist",
    "TCOP" to "Copyright",
    "TPUB" to "Publisher",
    "TBPM" to "BPM",
    "TSRC" to "ISRC",
    "TSO2" to "Album Artist Sort",
    "TSOA" to "Album Sort",
    "TSOP" to "Artist Sort",
    "TSOT" to "Title Sort",
)

private val CommonTagNames = mapOf(
    "title" to "Title",
    "artist" to "Artist",
    "albumartist" to "Album Artist",
    "albumartists" to "Album Artists",
    "album" to "Album",
    "tracknumber" to "Track Number",
    "track" to "Track Number",
    "discnumber" to "Disc Number",
    "disc" to "Disc Number",
    "date" to "Date",
    "year" to "Year",
    "originaldate" to "Original Date",
    "genre" to "Genre",
    "composer" to "Composer",
    "lyricist" to "Lyricist",
    "comment" to "Comment",
    "bpm" to "BPM",
    "isrc" to "ISRC",
    "label" to "Label",
    "publisher" to "Publisher",
    "copyright" to "Copyright",
    "catalognumber" to "Catalog Number",
    "musicbrainzreleasegroupid" to "MusicBrainz Release Group ID",
    "albumsort" to "Album Sort",
    "albumartistsort" to "Album Artist Sort",
    "artistsort" to "Artist Sort",
    "titlesort" to "Title Sort",
)

private val CommonTagOrder = listOf(
    "title",
    "artist",
    "albumartist",
    "albumartists",
    "album",
    "tracknumber",
    "discnumber",
    "date",
    "year",
    "originaldate",
    "genre",
    "composer",
    "lyricist",
    "bpm",
    "isrc",
    "label",
    "publisher",
    "copyright",
    "catalognumber",
    "comment",
).withIndex().associate { (index, key) -> key to index }

private const val HeaderProbeBytes = 16
private const val Id3HeaderBytes = 10
private const val Id3FrameHeaderBytes = 10
private const val FlacVorbisCommentBlock = 4
private const val MaxTagBytes = 2 * 1024 * 1024

package app.naviamp.domain.audio

import app.naviamp.domain.ReplayGain
import app.naviamp.domain.lyrics.lyricsFromTaggedText

data class AudioTag(
    val key: String,
    val value: String,
)

fun audioTagsFromAudioBytes(bytes: ByteArray): List<AudioTag> =
    when {
        bytes.startsWith("ID3") -> readId3v2Tags(bytes)
        bytes.startsWith("fLaC") -> readFlacVorbisComments(bytes)
        else -> emptyList()
    }

fun lyricsFromAudioTags(tags: List<AudioTag>) =
    tags.firstNotNullOfOrNull { tag -> lyricsFromTaggedText(tag.key, tag.value) }

fun embeddedLyricsFromAudioBytes(bytes: ByteArray) =
    app.naviamp.domain.lyrics.embeddedLyricsFromId3v2(bytes)
        ?: lyricsFromAudioTags(audioTagsFromAudioBytes(bytes))

fun replayGainFromAudioTags(tags: List<AudioTag>): ReplayGain? {
    fun value(vararg keys: String): Double? {
        val wanted = keys.map { it.normalizedReplayGainKey() }.toSet()
        return tags.firstNotNullOfOrNull { tag ->
            tag.value.replayGainNumber()
                ?.takeIf { tag.key.normalizedReplayGainKey() in wanted }
        }
    }

    val replayGain = ReplayGain(
        trackGainDb = value("REPLAYGAIN_TRACK_GAIN", "Replaygain Track Gain", "Track Gain"),
        albumGainDb = value("REPLAYGAIN_ALBUM_GAIN", "Replaygain Album Gain", "Album Gain"),
        trackPeak = value("REPLAYGAIN_TRACK_PEAK", "Replaygain Track Peak", "Track Peak"),
        albumPeak = value("REPLAYGAIN_ALBUM_PEAK", "Replaygain Album Peak", "Album Peak"),
    )
    return replayGain.takeIf {
        it.trackGainDb != null || it.albumGainDb != null || it.trackPeak != null || it.albumPeak != null
    }
}

private fun readId3v2Tags(bytes: ByteArray): List<AudioTag> {
    if (bytes.size < Id3HeaderBytes) return emptyList()
    val majorVersion = bytes[3].toInt() and 0xFF
    val flags = bytes[5].toInt() and 0xFF
    val tagSize = synchsafeInt(bytes, 6)
    if (tagSize <= 0 || tagSize > MaxTagBytes) return emptyList()

    var tagBytes = bytes.copyOfRange(Id3HeaderBytes, (Id3HeaderBytes + tagSize).coerceAtMost(bytes.size))
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
        frameId == "COMM" -> AudioTag("Comment", decodeCommentFrame(frameData))
        frameId == "USLT" -> AudioTag("Lyrics", decodeCommentFrame(frameData))
        frameId == "SYLT" -> AudioTag("Synced Lyrics", decodeSynchronizedLyricsFrame(frameData))
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

private fun decodeSynchronizedLyricsFrame(frameData: ByteArray): String {
    if (frameData.size <= 6) return ""
    val encoding = frameData[0].toInt() and 0xFF
    val timestampFormat = frameData[4].toInt() and 0xFF
    if (timestampFormat != 2) return ""
    var offset = 6
    while (offset < frameData.size && frameData[offset].toInt() != 0) {
        offset += 1
    }
    offset += 1

    val lines = mutableListOf<String>()
    while (offset < frameData.size) {
        val textStart = offset
        while (offset < frameData.size && frameData[offset].toInt() != 0) {
            offset += 1
        }
        if (offset + 5 > frameData.size) break
        val textBytes = frameData.copyOfRange(textStart, offset)
        offset += 1
        val timeMillis = frameData.intBe(offset).toLong()
        offset += 4
        val text = decodeEncodedText(encoding, textBytes).trim()
        if (text.isNotBlank()) {
            lines += "${timeMillis.toLrcTimestamp()} $text"
        }
    }
    return lines.joinToString("\n")
}

private fun decodeId3TextFrame(frameData: ByteArray): String =
    decodeId3RawTextFrame(frameData)
        .replace('\u0000', ';')
        .replace(Regex(";+"), ";")
        .trim(' ', ';', '\uFEFF')

private fun decodeId3RawTextFrame(frameData: ByteArray): String {
    if (frameData.isEmpty()) return ""
    val encoding = frameData[0].toInt() and 0xFF
    return decodeEncodedText(encoding, frameData.copyOfRange(1, frameData.size))
        .trim(' ', '\uFEFF')
}

private fun decodeEncodedText(encoding: Int, bytes: ByteArray): String =
    when (encoding) {
        1 -> decodeUtf16(bytes, byteOrderFromBom = true)
        2 -> decodeUtf16(bytes, byteOrderFromBom = false)
        3 -> bytes.decodeToString().trimEnd('\u0000')
        else -> bytes.latin1String().trimEnd('\u0000')
    }

private fun decodeUtf16(bytes: ByteArray, byteOrderFromBom: Boolean): String {
    var index = 0
    var littleEndian = false
    if (byteOrderFromBom && index + 1 < bytes.size) {
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
        while (index + 1 < bytes.size) {
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
    }.trimEnd('\u0000')
}

private fun readFlacVorbisComments(bytes: ByteArray): List<AudioTag> {
    if (!bytes.startsWith("fLaC")) return emptyList()
    var offset = 4
    var isLastBlock = false
    while (!isLastBlock && offset + 4 <= bytes.size) {
        val blockTypeByte = bytes[offset].toInt() and 0xFF
        isLastBlock = (blockTypeByte and 0x80) != 0
        val blockType = blockTypeByte and 0x7F
        val blockSize = ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
        offset += 4
        if (blockSize < 0 || blockSize > MaxTagBytes || offset + blockSize > bytes.size) return emptyList()
        if (blockType == FlacVorbisCommentBlock) {
            return parseVorbisComments(bytes.copyOfRange(offset, offset + blockSize)).normalizedAndOrdered()
        }
        offset += blockSize
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
        val comment = bytes.copyOfRange(offset, offset + length).decodeToString()
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

private fun String.normalizedReplayGainKey(): String =
    lowercase().filter { it.isLetterOrDigit() }

private fun String.replayGainNumber(): Double? =
    ReplayGainNumberRegex.find(this)?.value?.toDoubleOrNull()

private fun ByteArray.startsWith(prefix: String): Boolean =
    size >= prefix.length && ascii(0, prefix.length) == prefix

private fun ByteArray.ascii(offset: Int, length: Int): String =
    buildString(length) {
        repeat(length) { index -> append(Char(this@ascii[offset + index].toInt() and 0xff)) }
    }

private fun ByteArray.latin1String(): String =
    buildString(size) {
        this@latin1String.forEach { byte -> append(Char(byte.toInt() and 0xff)) }
    }

private fun ByteArray.intBe(offset: Int): Int =
    ((this[offset].toInt() and 0xff) shl 24) or
        ((this[offset + 1].toInt() and 0xff) shl 16) or
        ((this[offset + 2].toInt() and 0xff) shl 8) or
        (this[offset + 3].toInt() and 0xff)

private fun ByteArray.intLe(offset: Int): Int =
    (this[offset].toInt() and 0xff) or
        ((this[offset + 1].toInt() and 0xff) shl 8) or
        ((this[offset + 2].toInt() and 0xff) shl 16) or
        ((this[offset + 3].toInt() and 0xff) shl 24)

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
    take(24).joinToString(" ") { byte -> byte.toUByte().toString(16).padStart(2, '0').uppercase() }

private fun Long.toLrcTimestamp(): String {
    val minutes = this / 60_000
    val seconds = (this % 60_000) / 1_000
    val centiseconds = (this % 1_000) / 10
    return "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${centiseconds.toString().padStart(2, '0')}]"
}

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
    "syncedlyrics" to "Synced Lyrics",
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

private const val Id3HeaderBytes = 10
private const val Id3FrameHeaderBytes = 10
private const val FlacVorbisCommentBlock = 4
private const val MaxTagBytes = 2 * 1024 * 1024
private val ReplayGainNumberRegex = Regex("""[-+]?\d+(?:\.\d+)?""")

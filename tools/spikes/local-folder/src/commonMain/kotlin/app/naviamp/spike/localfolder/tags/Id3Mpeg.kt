package app.naviamp.spike.localfolder.tags

internal class Id3v2Result(val tags: AudioTags, val endOffset: Long, val lengthMillis: Long?)

private val Id3v22FrameIds = mapOf(
    "TT2" to "TIT2", "TP1" to "TPE1", "TP2" to "TPE2", "TAL" to "TALB", "TRK" to "TRCK", "TPA" to "TPOS",
    "TYE" to "TYER", "TCO" to "TCON", "TXX" to "TXXX", "PIC" to "APIC", "TLE" to "TLEN", "TOR" to "TORY",
)

private const val MaxUnsynchronizedTagBytes = 16 * 1024 * 1024

/** Reads ID3v2.2, 2.3 and 2.4 frames without loading picture payloads. */
internal fun readId3v2(window: ByteWindow, start: Long = 0): Id3v2Result? {
    val header = window.bytes(start, 10)
    if (header.size < 10 || header.latin1(0, 3) != "ID3") return null
    val version = header.u8(3)
    if (version !in 2..4) return null
    val flags = header.u8(5)
    val tagSize = header.syncSafe(6)
    val bodyStart = start + 10
    val end = bodyStart + tagSize + if (version == 4 && flags and 0x10 != 0) 10 else 0
    val unsynchronized = flags and 0x80 != 0 && version < 4
    val read: (Long, Int) -> ByteArray = if (unsynchronized) {
        if (tagSize > MaxUnsynchronizedTagBytes) return Id3v2Result(AudioTags(AudioContainer.Mp3), end, null)
        val decoded = window.bytes(bodyStart, tagSize).deunsynchronize();
        { position, count -> decoded.copyOfRange(position.toInt().coerceIn(0, decoded.size), (position + count).toInt().coerceIn(0, decoded.size)) }
    } else {
        { position, count -> window.bytes(bodyStart + position, count) }
    }
    var position = 0L
    if (flags and 0x40 != 0 && version >= 3) {
        val ext = read(position, 4)
        position += if (version == 4) ext.syncSafe(0).toLong() else ext.u32be(0) + 4
    }
    val idLength = if (version == 2) 3 else 4
    val headerLength = if (version == 2) 6 else 10
    val fields = TagFields()
    var lengthMillis: Long? = null
    var artwork: ArtworkLocation? = null
    var artworkIsFront = false
    while (position + headerLength <= tagSize) {
        val frameHeader = read(position, headerLength)
        if (frameHeader.size < headerLength) break
        val rawId = frameHeader.latin1(0, idLength)
        if (!rawId.all { it in 'A'..'Z' || it in '0'..'9' }) break
        var size = when (version) {
            2 -> frameHeader.u24be(3).toLong()
            3 -> frameHeader.u32be(4)
            else -> frameHeader.syncSafe(4).toLong()
        }
        // Some 2.4 writers store plain integers; trust whichever size lands on a valid next frame.
        if (version == 4 && size >= 0x80) {
            val plain = frameHeader.u32be(4)
            fun landsOnFrame(next: Long) = next + headerLength > tagSize || read(next, 4).let { it.isFrameId() || it.all { b -> b == 0.toByte() } }
            if (!landsOnFrame(position + headerLength + size) && landsOnFrame(position + headerLength + plain)) size = plain
        }
        val frameFlags = if (version == 2) 0 else frameHeader.u16be(8)
        val id = if (version == 2) Id3v22FrameIds[rawId] ?: rawId else rawId
        var dataStart = position + headerLength
        var dataLength = size
        val skip = when (version) {
            3 -> frameFlags and 0x00C0 != 0
            4 -> frameFlags and 0x000C != 0
            else -> false
        }
        if (version == 3 && frameFlags and 0x0020 != 0) { dataStart++; dataLength-- }
        if (version == 4 && frameFlags and 0x0001 != 0) { dataStart += 4; dataLength -= 4 }
        val frameUnsync = version == 4 && frameFlags and 0x0002 != 0
        if (!skip && dataLength > 0) {
            when {
                id == "APIC" -> {
                    val head = read(dataStart, minOf(dataLength, 1024L).toInt()).let { if (frameUnsync) it.deunsynchronize() else it }
                    val picture = parsePictureHeader(head, version)
                    if (picture != null && (artwork == null || (!artworkIsFront && picture.second))) {
                        val encoding = if (unsynchronized || frameUnsync) ArtworkEncoding.Id3Unsynchronized else ArtworkEncoding.Raw
                        val offset = if (unsynchronized) -1L else bodyStart + dataStart + picture.first
                        artwork = ArtworkLocation(offset, dataLength - picture.first, picture.third, encoding)
                        artworkIsFront = picture.second
                    }
                }
                id.startsWith('T') && dataLength <= 64 * 1024 -> {
                    val body = read(dataStart, dataLength.toInt()).let { if (frameUnsync) it.deunsynchronize() else it }
                    val values = decodeTextValues(body)
                    when (id) {
                        "TIT2" -> values.firstOrNull()?.let { fields.put("TITLE", it) }
                        "TPE1" -> values.forEach { fields.put("ARTIST", it) }
                        "TPE2" -> values.firstOrNull()?.let { fields.put("ALBUMARTIST", it) }
                        "TALB" -> values.firstOrNull()?.let { fields.put("ALBUM", it) }
                        "TRCK" -> values.firstOrNull()?.let { fields.put("TRACKNUMBER", it) }
                        "TPOS" -> values.firstOrNull()?.let { fields.put("DISCNUMBER", it) }
                        "TDRC", "TYER" -> values.firstOrNull()?.let { fields.put("DATE", it) }
                        "TDOR", "TORY" -> values.firstOrNull()?.let { fields.put("ORIGINALDATE", it) }
                        "TCON" -> values.flatMap(::id3Genres).forEach { fields.put("GENRE", it) }
                        "TLEN" -> lengthMillis = values.firstOrNull()?.trim()?.toLongOrNull()
                        "TXXX" -> if (values.size >= 2) fields.put(values[0], values[1])
                    }
                }
            }
        }
        position += headerLength + size
    }
    return Id3v2Result(fields.toTags(AudioContainer.Mp3).copy(artwork = artwork), end, lengthMillis)
}

private fun ByteArray.isFrameId(): Boolean = size == 4 && all { it in 'A'.code.toByte()..'Z'.code.toByte() || it in '0'.code.toByte()..'9'.code.toByte() }

private fun ByteArray.deunsynchronize(): ByteArray {
    val output = ByteArray(size)
    var written = 0
    var index = 0
    while (index < size) {
        output[written++] = this[index]
        if (this[index] == 0xFF.toByte() && index + 1 < size && this[index + 1] == 0.toByte()) index++
        index++
    }
    return output.copyOf(written)
}

/** Returns (data offset within the frame, is front cover, MIME type). */
private fun parsePictureHeader(head: ByteArray, version: Int): Triple<Int, Boolean, String?>? {
    if (head.isEmpty()) return null
    val encoding = head.u8(0)
    var at = 1
    val mime: String?
    if (version == 2) {
        mime = when (head.latin1(1, 3).uppercase()) { "PNG" -> "image/png"; "JPG" -> "image/jpeg"; else -> null }
        at = 4
    } else {
        val end = head.indexOfZero(at, 1)
        if (end < 0) return null
        mime = head.latin1(at, end - at).ifBlank { null }
        at = end + 1
    }
    val type = head.u8(at)
    at++
    val width = if (encoding == 1 || encoding == 2) 2 else 1
    val descriptionEnd = head.indexOfZero(at, width)
    if (descriptionEnd < 0) return null
    return Triple(descriptionEnd + width, type == 3, mime)
}

private fun ByteArray.indexOfZero(from: Int, width: Int): Int {
    var at = from
    while (at + width - 1 < size) {
        if (width == 1 && this[at] == 0.toByte()) return at
        if (width == 2 && this[at] == 0.toByte() && this[at + 1] == 0.toByte()) return at
        at += width
    }
    return -1
}

private fun decodeTextValues(body: ByteArray): List<String> {
    if (body.isEmpty()) return emptyList()
    val encoding = body.u8(0)
    val width = if (encoding == 1 || encoding == 2) 2 else 1
    val values = mutableListOf<String>()
    var at = 1
    var bom: Boolean? = null
    while (at < body.size) {
        val end = body.indexOfZero(at, width).let { if (it < 0) body.size else it }
        val text = when (encoding) {
            0 -> body.latin1(at, end - at)
            1 -> {
                // Later UTF-16 values may omit their BOM and inherit the first value's byte order.
                if (end - at >= 2) when (body.u16be(at)) { 0xFEFF -> bom = true; 0xFFFE -> bom = false }
                body.utf16(at, end - at, bigEndianDefault = bom ?: false)
            }
            2 -> body.utf16(at, end - at, bigEndianDefault = true)
            else -> body.utf8(at, end - at)
        }
        values += text
        at = end + width
    }
    return values.map { it.trimEnd('\u0000') }.filter { it.isNotBlank() }
}

private fun id3Genres(value: String): List<String> {
    val results = mutableListOf<String>()
    var rest = value.trim()
    while (rest.startsWith("(")) {
        val close = rest.indexOf(')')
        if (close < 0) break
        rest.substring(1, close).toIntOrNull()?.let(Id3v1Genres::getOrNull)?.let(results::add)
        rest = rest.substring(close + 1)
    }
    rest.trim().takeIf(String::isNotEmpty)?.let { results += it.toIntOrNull()?.let(Id3v1Genres::getOrNull) ?: it }
    return results
}

internal fun readId3v1(window: ByteWindow): AudioTags? {
    if (window.length < 128) return null
    val tag = window.bytes(window.length - 128, 128)
    if (tag.latin1(0, 3) != "TAG") return null
    fun text(from: Int, count: Int) = tag.latin1(from, count).trimEnd('\u0000', ' ').ifBlank { null }
    return AudioTags(
        container = AudioContainer.Mp3,
        title = text(3, 30),
        artists = listOfNotNull(text(33, 30)),
        album = text(63, 30),
        year = text(93, 4)?.let(::parseYear),
        trackNumber = if (tag.u8(125) == 0 && tag.u8(126) != 0) tag.u8(126) else null,
        genres = listOfNotNull(Id3v1Genres.getOrNull(tag.u8(127))),
    )
}

internal class MpegInfo(val durationMillis: Long?, val sampleRateHz: Int, val channels: Int, val codec: String)

private val BitratesV1 = arrayOf(
    intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448),
    intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384),
    intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320),
)
private val BitratesV2 = arrayOf(
    intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256),
    intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
    intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
)

/** First valid frame, then Xing/Info or VBRI frame counts, else a constant-bitrate estimate. */
internal fun readMpegInfo(window: ByteWindow, audioStart: Long, audioEnd: Long): MpegInfo? {
    var span = 4096
    while (true) {
        readMpegInfo(window, audioStart, audioEnd, window.bytes(audioStart, span))?.let { return it }
        if (span >= 64 * 1024 || audioStart + span >= window.length) return null
        span *= 4
    }
}

private fun readMpegInfo(window: ByteWindow, audioStart: Long, audioEnd: Long, scan: ByteArray): MpegInfo? {
    var index = 0
    while (index + 4 <= scan.size) {
        if (scan.u8(index) == 0xFF && scan.u8(index + 1) and 0xE0 == 0xE0) {
            val frame = mpegFrame(scan, index)
            if (frame != null) {
                val headerAt = audioStart + index
                val header = window.bytes(headerAt, 4 + 32 + 26)
                val version1 = frame.version == 3
                val sideInfo = if (version1) (if (frame.channels == 1) 17 else 32) else (if (frame.channels == 1) 9 else 17)
                val xingAt = 4 + sideInfo
                val marker = header.latin1(xingAt, 4)
                val frames: Long? = when {
                    (marker == "Xing" || marker == "Info") && header.u32be(xingAt + 4) and 1L != 0L -> header.u32be(xingAt + 8)
                    header.latin1(36, 4) == "VBRI" -> header.u32be(36 + 14)
                    else -> null
                }
                val duration = if (frames != null) frames * frame.samplesPerFrame * 1000 / frame.sampleRate
                else (audioEnd - headerAt).coerceAtLeast(0) * 8 / frame.bitrateKbps
                return MpegInfo(duration, frame.sampleRate, frame.channels, "MP${frame.layer}")
            }
        }
        index++
    }
    return null
}

private class MpegFrame(val version: Int, val layer: Int, val bitrateKbps: Int, val sampleRate: Int, val channels: Int, val samplesPerFrame: Int)

private fun mpegFrame(bytes: ByteArray, at: Int): MpegFrame? {
    val b1 = bytes.u8(at + 1)
    val b2 = bytes.u8(at + 2)
    val version = (b1 shr 3) and 3
    val layerBits = (b1 shr 1) and 3
    val bitrateIndex = b2 shr 4
    val rateIndex = (b2 shr 2) and 3
    if (version == 1 || layerBits == 0 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null
    val layer = 4 - layerBits
    val bitrate = (if (version == 3) BitratesV1 else BitratesV2)[layer - 1][bitrateIndex]
    val baseRate = intArrayOf(44100, 48000, 32000)[rateIndex]
    val sampleRate = when (version) { 3 -> baseRate; 2 -> baseRate / 2; else -> baseRate / 4 }
    val samples = when { layer == 1 -> 384; layer == 2 || version == 3 -> 1152; else -> 576 }
    val channels = if (bytes.u8(at + 3) shr 6 == 3) 1 else 2
    return MpegFrame(version, layer, bitrate, sampleRate, channels, samples)
}

internal val Id3v1Genres = listOf(
    "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
    "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
    "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk",
    "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
    "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
    "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
    "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native US", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes",
    "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
)

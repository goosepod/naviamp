package app.naviamp.spike.localfolder.tags

private const val MaxCommentBytes = 16 * 1024 * 1024

internal fun parseVorbisComment(bytes: ByteArray, from: Int, fields: TagFields): Int? {
    var at = from
    val vendorLength = bytes.u32le(at).toInt()
    at += 4 + vendorLength
    val count = bytes.u32le(at)
    at += 4
    var pictureAt: Int? = null
    for (index in 0 until count) {
        if (at + 4 > bytes.size) break
        val length = bytes.u32le(at).toInt()
        at += 4
        if (length < 0 || at + length > bytes.size) break
        val equals = (at until at + length).firstOrNull { bytes[it] == '='.code.toByte() }
        if (equals != null) {
            val key = bytes.latin1(at, equals - at)
            if (key.equals("METADATA_BLOCK_PICTURE", ignoreCase = true)) {
                if (pictureAt == null) pictureAt = equals + 1
            } else {
                fields.put(key, bytes.utf8(equals + 1, at + length - equals - 1))
            }
        }
        at += length
    }
    return pictureAt
}

/** Native FLAC: STREAMINFO, VORBIS_COMMENT and the first PICTURE (front cover preferred). */
internal fun readFlac(window: ByteWindow, start: Long): AudioTags? {
    if (window.ascii(start, 4) != "fLaC") return null
    var position = start + 4
    val fields = TagFields()
    var info: AudioTags? = null
    var artwork: ArtworkLocation? = null
    var artworkIsFront = false
    while (position + 4 <= window.length) {
        val header = window.bytes(position, 4)
        val last = header.u8(0) and 0x80 != 0
        val type = header.u8(0) and 0x7F
        val length = header.u24be(1)
        val body = position + 4
        when (type) {
            0 -> {
                val streamInfo = window.bytes(body, 34)
                val rate = (streamInfo.u24be(10) shr 4)
                val channels = ((streamInfo.u8(12) shr 1) and 7) + 1
                val bits = (((streamInfo.u8(12) and 1) shl 4) or (streamInfo.u8(13) shr 4)) + 1
                val samples = ((streamInfo.u8(13) and 0x0F).toLong() shl 32) or streamInfo.u32be(14)
                info = AudioTags(AudioContainer.Flac, codec = "FLAC", sampleRateHz = rate, channels = channels, bitsPerSample = bits,
                    durationMillis = if (rate > 0 && samples > 0) samples * 1000 / rate else null)
            }
            4 -> if (length <= MaxCommentBytes) parseVorbisComment(window.bytes(body, length), 0, fields)
            6 -> {
                val head = window.bytes(body, minOf(length, 4096))
                val pictureType = head.u32be(0)
                val mimeLength = head.u32be(4).toInt()
                val descriptionLength = head.u32be(8 + mimeLength).toInt()
                val dataLengthAt = 8 + mimeLength + 4 + descriptionLength + 16
                if (dataLengthAt + 4 <= head.size && (artwork == null || (!artworkIsFront && pictureType == 3L))) {
                    artwork = ArtworkLocation(body + dataLengthAt + 4, head.u32be(dataLengthAt), head.latin1(8, mimeLength))
                    artworkIsFront = pictureType == 3L
                }
            }
        }
        position = body + length
        if (last) break
    }
    return (info ?: AudioTags(AudioContainer.Flac, codec = "FLAC")).let { base ->
        fields.toTags(AudioContainer.Flac).copy(codec = base.codec, sampleRateHz = base.sampleRateHz, channels = base.channels,
            bitsPerSample = base.bitsPerSample, durationMillis = base.durationMillis, artwork = artwork)
    }
}

private class OggPage(val headerType: Int, val granule: Long, val serial: Long, val segments: IntArray, val bodyAt: Long) {
    val bodyLength: Int get() = segments.sum()
}

private fun readOggPage(window: ByteWindow, at: Long): OggPage? {
    val header = window.bytes(at, 27)
    if (header.size < 27 || header.latin1(0, 4) != "OggS") return null
    val count = header.u8(26)
    val table = window.bytes(at + 27, count)
    if (table.size < count) return null
    return OggPage(header.u8(5), header.u64le(6), header.u32le(14), IntArray(count) { table.u8(it) }, at + 27 + count)
}

/** Reassembles the first two logical packets of the first stream: identification and comments. */
internal fun readOgg(window: ByteWindow): AudioTags? {
    var position = 0L
    val packets = mutableListOf<ByteArray>()
    var current = ByteArrayBuilder()
    var serial: Long? = null
    while (packets.size < 2) {
        val page = readOggPage(window, position) ?: break
        if (serial == null) serial = page.serial
        if (page.serial == serial) {
            var bodyAt = page.bodyAt
            for (segment in page.segments) {
                if (current.size + segment > MaxCommentBytes) return null
                current.append(window.bytes(bodyAt, segment))
                bodyAt += segment
                if (segment < 255) {
                    packets += current.toByteArray()
                    current = ByteArrayBuilder()
                    if (packets.size == 2) break
                }
            }
        }
        position = page.bodyAt + page.bodyLength
    }
    val identification = packets.getOrNull(0) ?: return null
    val fields = TagFields()
    val container: AudioContainer
    val rate: Int
    val channels: Int
    var preSkip = 0L
    var pictureAt: Int? = null
    val comments = packets.getOrNull(1)
    when {
        identification.latin1(0, 7) == "\u0001vorbis" -> {
            container = AudioContainer.OggVorbis
            channels = identification.u8(11)
            rate = identification.u32le(12).toInt()
            if (comments != null && comments.latin1(0, 7) == "\u0003vorbis") pictureAt = parseVorbisComment(comments, 7, fields)
        }
        identification.latin1(0, 8) == "OpusHead" -> {
            container = AudioContainer.OggOpus
            channels = identification.u8(9)
            preSkip = identification.u16le(10).toLong()
            rate = 48_000
            if (comments != null && comments.latin1(0, 8) == "OpusTags") pictureAt = parseVorbisComment(comments, 8, fields)
        }
        else -> return AudioTags(AudioContainer.Unknown)
    }
    val granule = lastGranule(window, serial ?: 0)
    val samples = granule?.minus(preSkip)?.takeIf { it > 0 }
    return fields.toTags(container).copy(
        codec = if (container == AudioContainer.OggOpus) "OPUS" else "VORBIS",
        sampleRateHz = if (container == AudioContainer.OggOpus) identification.u32le(12).toInt().takeIf { it > 0 } ?: rate else rate,
        channels = channels,
        durationMillis = samples?.let { it * 1000 / rate },
        // The picture is base64 inside the comment packet; its file offset is not contiguous across pages.
        artwork = pictureAt?.let { ArtworkLocation(-1, -1, null, ArtworkEncoding.Base64FlacPicture) },
    )
}

/** The last page's granule position gives the stream length; only the file tail is read. */
private fun lastGranule(window: ByteWindow, serial: Long): Long? {
    var span = 64 * 1024L
    while (true) {
        val from = (window.length - span).coerceAtLeast(0)
        val tail = window.bytes(from, (window.length - from).toInt())
        var index = tail.size - 27
        while (index >= 0) {
            if (tail[index] == 'O'.code.toByte() && tail.latin1(index, 4) == "OggS" && tail.u32le(index + 14) == serial) {
                val granule = tail.u64le(index + 6)
                if (granule >= 0) return granule
            }
            index--
        }
        if (from == 0L || span >= 1024 * 1024) return null
        span *= 4
    }
}

internal class ByteArrayBuilder {
    private var buffer = ByteArray(256)
    var size = 0
        private set

    fun append(bytes: ByteArray) {
        if (size + bytes.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + bytes.size))
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

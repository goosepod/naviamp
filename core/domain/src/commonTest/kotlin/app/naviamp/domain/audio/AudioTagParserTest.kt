package app.naviamp.domain.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioTagParserTest {
    @Test
    fun readsAndOrdersId3TextFrames() {
        val tags = audioTagsFromAudioBytes(
            id3Tag(
                textFrame("TCON", "Rock") +
                    textFrame("TALB", "The Album") +
                    textFrame("TPE2", "The Album Artist") +
                    textFrame("TIT2", "The Title") +
                    textFrame("TXXX", "CATALOGNUMBER\u0000ABC-123"),
            ),
        )

        assertEquals(
            listOf(
                AudioTag("Title", "The Title"),
                AudioTag("Album Artist", "The Album Artist"),
                AudioTag("Album", "The Album"),
                AudioTag("Genre", "Rock"),
                AudioTag("Catalog Number", "ABC-123"),
            ),
            tags,
        )
    }

    @Test
    fun skipsUnsupportedBinaryFramesAndContinuesReadingText() {
        val tags = audioTagsFromAudioBytes(
            id3Tag(
                binaryFrame("APIC", ByteArray(256 * 1024) { 1 }) +
                    textFrame("TIT2", "After the artwork"),
            ),
        )

        assertEquals(listOf(AudioTag("Title", "After the artwork")), tags)
    }

    @Test
    fun readsAndOrdersFlacVorbisComments() {
        val tags = audioTagsFromAudioBytes(
            flacVorbisCommentBlock(
                listOf(
                    "MUSICBRAINZ_RELEASEGROUPID=release-group",
                    "ALBUM=The Album",
                    "TRACKNUMBER=4",
                    "ARTIST=The Artist",
                    "TITLE=The Title",
                ),
            ),
        )

        assertEquals(
            listOf(
                AudioTag("Title", "The Title"),
                AudioTag("Artist", "The Artist"),
                AudioTag("Album", "The Album"),
                AudioTag("Track Number", "4"),
                AudioTag("MusicBrainz Release Group ID", "release-group"),
            ),
            tags,
        )
    }

    @Test
    fun readsAndOrdersOpusVorbisCommentsAcrossOggPages() {
        val tags = audioTagsFromAudioBytes(
            oggOpusTags(
                listOf(
                    "MUSICBRAINZ_TRACKID=15c45351-8ca8-42d7-b55f-d625d58cbcb",
                    "R128_TRACK_GAIN=-870",
                    "ALBUM=The Best of Big Band: Classic Swing Dance Songs of the 1940s and 1950s",
                    "ARTIST=The New Orleans Jazz Band",
                    "ALBUMARTIST=Various Artists",
                    "TITLE=Alexander's Ragtime Band",
                ),
                firstPagePacketBytes = 255,
            ),
        )

        assertEquals(
            listOf(
                AudioTag("Title", "Alexander's Ragtime Band"),
                AudioTag("Artist", "The New Orleans Jazz Band"),
                AudioTag("Album Artist", "Various Artists"),
                AudioTag("Album", "The Best of Big Band: Classic Swing Dance Songs of the 1940s and 1950s"),
                AudioTag("MusicBrainz Track ID", "15c45351-8ca8-42d7-b55f-d625d58cbcb"),
                AudioTag("R128 Track Gain", "-870"),
            ),
            tags,
        )
    }

    @Test
    fun extractsReplayGainFromTags() {
        val replayGain = replayGainFromAudioTags(
            listOf(
                AudioTag("REPLAYGAIN_TRACK_GAIN", "-7.25 dB"),
                AudioTag("REPLAYGAIN_ALBUM_PEAK", "0.982"),
            ),
        )

        assertEquals(-7.25, replayGain?.trackGainDb)
        assertEquals(0.982, replayGain?.albumPeak)
    }
}

private fun id3Tag(frames: ByteArray): ByteArray =
    "ID3".asciiBytes() +
        byteArrayOf(3, 0, 0) +
        frames.size.synchsafeBytes() +
        frames

private fun textFrame(id: String, value: String): ByteArray {
    val data = byteArrayOf(3) + value.encodeToByteArray()
    return id.asciiBytes() + data.size.intBeBytes() + byteArrayOf(0, 0) + data
}

private fun binaryFrame(id: String, data: ByteArray): ByteArray =
    id.asciiBytes() + data.size.intBeBytes() + byteArrayOf(0, 0) + data

private fun flacVorbisCommentBlock(comments: List<String>): ByteArray {
    val vendor = "Naviamp test".encodeToByteArray()
    val commentBytes = comments.map { it.encodeToByteArray() }
    val payload = vendor.size.intLeBytes() +
        vendor +
        commentBytes.size.intLeBytes() +
        commentBytes.fold(byteArrayOf()) { bytes, comment ->
            bytes + comment.size.intLeBytes() + comment
        }
    return "fLaC".asciiBytes() +
        byteArrayOf((0x80 or 4).toByte()) +
        payload.size.int24BeBytes() +
        payload
}

private fun oggOpusTags(comments: List<String>, firstPagePacketBytes: Int): ByteArray {
    val vendor = "opusenc from opus-tools".encodeToByteArray()
    val commentBytes = comments.map { it.encodeToByteArray() }
    val packet = "OpusTags".asciiBytes() +
        vendor.size.intLeBytes() +
        vendor +
        commentBytes.size.intLeBytes() +
        commentBytes.fold(byteArrayOf()) { bytes, comment ->
            bytes + comment.size.intLeBytes() + comment
        }
    val split = firstPagePacketBytes.coerceAtMost(packet.size)
    return oggPage("OpusHead".asciiBytes() + ByteArray(11), continued = false, sequence = 0, packetContinues = false) +
        oggPage(packet.copyOfRange(0, split), continued = false, sequence = 1, packetContinues = split < packet.size) +
        if (split < packet.size) {
            oggPage(packet.copyOfRange(split, packet.size), continued = true, sequence = 2, packetContinues = false)
        } else {
            byteArrayOf()
        }
}

private fun oggPage(data: ByteArray, continued: Boolean, sequence: Int, packetContinues: Boolean): ByteArray {
    val lacing = mutableListOf<Int>()
    var remaining = data.size
    while (remaining >= 255) {
        lacing += 255
        remaining -= 255
    }
    if (!packetContinues) lacing += remaining
    val header = "OggS".asciiBytes() +
        byteArrayOf(0, if (continued) 1 else 0) +
        ByteArray(8) +
        1.intLeBytes() +
        sequence.intLeBytes() +
        ByteArray(4) +
        byteArrayOf(lacing.size.toByte()) +
        lacing.map { it.toByte() }.toByteArray()
    return header + data
}

private fun String.asciiBytes(): ByteArray =
    ByteArray(length) { index -> this[index].code.toByte() }

private fun Int.synchsafeBytes(): ByteArray =
    byteArrayOf(
        ((this shr 21) and 0x7F).toByte(),
        ((this shr 14) and 0x7F).toByte(),
        ((this shr 7) and 0x7F).toByte(),
        (this and 0x7F).toByte(),
    )

private fun Int.intBeBytes(): ByteArray =
    byteArrayOf(
        ((this shr 24) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte(),
    )

private fun Int.intLeBytes(): ByteArray =
    byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte(),
    )

private fun Int.int24BeBytes(): ByteArray =
    byteArrayOf(
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        (this and 0xFF).toByte(),
    )

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

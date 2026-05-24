package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals

class EmbeddedLyricsParserTest {
    @Test
    fun parsesUnsynchronizedLyricsFrame() {
        val lyricsText = "[00:10.50]First line\n[00:12.00]Second line"
        val frame = byteArrayOf(3) + "eng".encodeToByteArray() + byteArrayOf(0) + lyricsText.encodeToByteArray()
        val lyrics = embeddedLyricsFromId3v2(id3Tag("USLT", frame))

        assertEquals(LyricsSource.Embedded, lyrics?.source)
        assertEquals(true, lyrics?.synced)
        assertEquals(listOf(10_500L, 12_000L), lyrics?.lines?.map { it.startMillis })
        assertEquals(listOf("First line", "Second line"), lyrics?.lines?.map { it.text })
    }

    @Test
    fun parsesLyricsTextUserFrame() {
        val frame = byteArrayOf(3) +
            "UNSYNCED LYRICS".encodeToByteArray() +
            byteArrayOf(0) +
            "Plain line one\nPlain line two".encodeToByteArray()
        val lyrics = embeddedLyricsFromId3v2(id3Tag("TXXX", frame))

        assertEquals(false, lyrics?.synced)
        assertEquals(listOf("Plain line one", "Plain line two"), lyrics?.lines?.map { it.text })
    }

    private fun id3Tag(frameId: String, frameContent: ByteArray): ByteArray {
        val frameHeader = frameId.encodeToByteArray() + intBytes(frameContent.size) + byteArrayOf(0, 0)
        val body = frameHeader + frameContent
        return "ID3".encodeToByteArray() +
            byteArrayOf(3, 0, 0) +
            syncSafeBytes(body.size) +
            body
    }

    private fun intBytes(value: Int): ByteArray =
        byteArrayOf(
            ((value ushr 24) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            (value and 0xff).toByte(),
        )

    private fun syncSafeBytes(value: Int): ByteArray =
        byteArrayOf(
            ((value ushr 21) and 0x7f).toByte(),
            ((value ushr 14) and 0x7f).toByte(),
            ((value ushr 7) and 0x7f).toByte(),
            (value and 0x7f).toByte(),
        )
}

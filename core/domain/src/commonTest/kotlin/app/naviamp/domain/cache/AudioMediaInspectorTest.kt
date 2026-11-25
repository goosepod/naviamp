package app.naviamp.domain.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioMediaInspectorTest {
    @Test
    fun recognizesContainersFromBytesRegardlessOfChunking() {
        val samples = listOf(
            ObservedAudioContainer.Mp3 to id3(64) + mp3Frames(),
            ObservedAudioContainer.Flac to "fLaC".encodeToByteArray() + ByteArray(80) { 1 },
            ObservedAudioContainer.Flac to id3(16) + "fLaC".encodeToByteArray() + ByteArray(40),
            ObservedAudioContainer.Ogg to oggPage(0x02, 30) + oggPage(0x04, 300),
            ObservedAudioContainer.Mp4 to mp4(mdatPayload = 200),
            ObservedAudioContainer.Wav to wav(dataBytes = 100),
            ObservedAudioContainer.Aac to byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte()) + ByteArray(60),
        )
        for ((expected, bytes) in samples) {
            for (chunkSize in listOf(1, 7, 4096)) {
                val media = inspect(bytes, chunkSize)
                assertEquals(expected, media.container, "$expected with chunk size $chunkSize")
                assertTrue(media.isAcceptable, "$expected with chunk size $chunkSize")
                assertEquals(bytes.size.toLong(), media.receivedBytes)
            }
        }
    }

    @Test
    fun unfamiliarBinaryAudioIsAcceptedWithTheNeutralExtension() {
        val media = inspect(ByteArray(64) { 9 })

        assertNull(media.container)
        assertTrue(media.isAcceptable)
        assertEquals(".audio", media.fileExtension)
        assertEquals("audio/x-ms-wma", media.savedContentType("audio/x-ms-wma"))
        // A type the probe recognizes but did not observe is not trusted.
        assertNull(media.savedContentType("audio/flac"))
    }

    @Test
    fun observedFormatOverridesTheRequestedContentType() {
        val media = inspect("fLaC".encodeToByteArray() + ByteArray(40))

        assertEquals("audio/flac", media.savedContentType("audio/aac"))
        assertEquals(".flac", media.fileExtension)
    }

    @Test
    fun errorDocumentsAndEmptyBodiesAreRejected() {
        for (body in listOf(
            "",
            "  <!DOCTYPE html><html>Error</html>",
            "﻿<html><head><title>Sign in</title></head></html>",
            "<?xml version=\"1.0\"?><subsonic-response status=\"failed\"/>",
            "<subsonic-response status=\"failed\"><error code=\"50\"/></subsonic-response>",
            "{\"subsonic-response\":{\"status\":\"failed\"}}",
            "[1]",
        )) {
            for (chunkSize in listOf(1, 4096)) {
                assertFalse(inspect(body.encodeToByteArray(), chunkSize).isAcceptable, body)
            }
        }
    }

    @Test
    fun structuralTruncationIsDetectedWhereTheContainerDeclaresItsLength() {
        val truncated = listOf(
            "mp4 box past end" to mp4(mdatPayload = 200).dropLast(20),
            "ogg page past end" to (oggPage(0x02, 30) + oggPage(0x04, 300)).dropLast(5),
            "ogg without end of stream" to oggPage(0x02, 30) + oggPage(0x00, 300),
            "wav riff past end" to wav(dataBytes = 100).dropLast(1),
            "id3 tag past end" to id3(400).copyOf(200),
        )
        for ((name, bytes) in truncated) {
            for (chunkSize in listOf(1, 13, 4096)) {
                val media = inspect(bytes, chunkSize)
                assertTrue(media.isTruncated, "$name with chunk size $chunkSize")
                assertFalse(media.isAcceptable, name)
            }
        }
    }

    @Test
    fun streamingWavPlaceholderLengthsAreNotTreatedAsTruncation() {
        val streaming = wav(dataBytes = 100).also { bytes ->
            for (index in 4 until 8) bytes[index] = 0xFF.toByte()
        }

        assertFalse(inspect(streaming).isTruncated)
    }

    @Test
    fun mp4LargeSizeAndToEndBoxesAreWalked() {
        val largeSize = mp4Box("ftyp", ByteArray(8)) +
            byteArrayOf(0, 0, 0, 1) + "mdat".encodeToByteArray() + longBytes(16L + 50) + ByteArray(50)
        val toEnd = mp4Box("ftyp", ByteArray(8)) + byteArrayOf(0, 0, 0, 0) + "mdat".encodeToByteArray() + ByteArray(30)

        assertTrue(inspect(largeSize).isAcceptable)
        assertTrue(inspect(largeSize.dropLast(1)).isTruncated)
        assertTrue(inspect(toEnd).isAcceptable)
    }

    private fun inspect(bytes: ByteArray, chunkSize: Int = 4096): ObservedAudioMedia {
        val inspector = AudioMediaInspector()
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(chunkSize, bytes.size - offset)
            // Reuse a larger buffer to mirror transport chunk arrays.
            val chunk = ByteArray(count + 3)
            bytes.copyInto(chunk, 0, offset, offset + count)
            inspector.accept(chunk, count)
            offset += count
        }
        return inspector.finish()
    }
}

internal fun id3(tagBodyBytes: Int): ByteArray {
    val header = byteArrayOf(
        'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
        ((tagBodyBytes shr 21) and 0x7F).toByte(),
        ((tagBodyBytes shr 14) and 0x7F).toByte(),
        ((tagBodyBytes shr 7) and 0x7F).toByte(),
        (tagBodyBytes and 0x7F).toByte(),
    )
    return header + ByteArray(tagBodyBytes)
}

internal fun mp3Frames(): ByteArray =
    byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00) + ByteArray(120) { 3 }

internal fun oggPage(flags: Int, payloadBytes: Int): ByteArray {
    val segments = mutableListOf<Int>()
    var remaining = payloadBytes
    while (remaining >= 255) { segments += 255; remaining -= 255 }
    segments += remaining
    val header = "OggS".encodeToByteArray() + byteArrayOf(0, flags.toByte()) + ByteArray(20) +
        byteArrayOf(segments.size.toByte())
    return header + ByteArray(segments.size) { segments[it].toByte() } + ByteArray(payloadBytes) { 5 }
}

internal fun mp4Box(type: String, payload: ByteArray): ByteArray =
    intBytes(8 + payload.size) + type.encodeToByteArray() + payload

internal fun mp4(mdatPayload: Int): ByteArray =
    mp4Box("ftyp", "M4A ".encodeToByteArray() + ByteArray(4)) +
        mp4Box("moov", ByteArray(24)) +
        mp4Box("mdat", ByteArray(mdatPayload) { 4 })

internal fun wav(dataBytes: Int): ByteArray {
    val fmt = "fmt ".encodeToByteArray() + littleEndian(16) + ByteArray(16)
    val data = "data".encodeToByteArray() + littleEndian(dataBytes) + ByteArray(dataBytes) { 2 }
    val body = "WAVE".encodeToByteArray() + fmt + data
    return "RIFF".encodeToByteArray() + littleEndian(body.size) + body
}

private fun intBytes(value: Int) = byteArrayOf(
    (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
)

private fun longBytes(value: Long) = ByteArray(8) { index -> (value ushr (56 - index * 8)).toByte() }

private fun littleEndian(value: Int) = byteArrayOf(
    value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte(),
)

private fun ByteArray.dropLast(count: Int): ByteArray = copyOf(size - count)

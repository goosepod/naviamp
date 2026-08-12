@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.naviamp.ios.playback

import app.naviamp.ios.bass.native.BASS_Init
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosBassDecodeIntegrationTest {
    @Test
    fun bundledBassDecodesSeeksAndReadsAudio() {
        val path = "${NSTemporaryDirectory().trimEnd('/')}/naviamp-bass-${NSUUID.UUID().UUIDString}.wav"
        val bytes = testWav()
        bytes.usePinned { pinned ->
            assertTrue(
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                    .writeToFile(path, atomically = true),
            )
        }

        val backend = IosBassAudioBackend()
        try {
            assertTrue(backend.version > 0)
            assertTrue(backend.mixerVersion > 0)
            assertTrue(BASS_Init(0, SampleRate.toUInt(), 0u, null, null) != 0, "BASS no-sound test device failed")
            assertTrue(backend.pluginDiagnostics.all { it.loaded }, backend.pluginDiagnostics.joinToString())
            val stream = backend.createFileDecodeStream(path).getOrThrow()
            try {
                assertTrue((backend.durationSeconds(stream) ?: 0.0) > 0.9)
                assertEquals(SampleRate, backend.channelInfo(stream).getOrThrow().frequency)
                backend.seek(stream, 0.5).getOrThrow()
                val samples = FloatArray(256)
                assertTrue(backend.readFloatData(stream, samples).getOrThrow() > 0)
                assertTrue(samples.any { it != 0f })
            } finally {
                backend.freeStream(stream).getOrThrow()
            }
        } finally {
            backend.free()
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }
}

private const val SampleRate = 44_100
private const val Channels = 2

private fun testWav(): ByteArray {
    val frames = SampleRate
    val dataBytes = frames * Channels * 2
    val bytes = ByteArray(44 + dataBytes)
    var offset = 0
    fun ascii(value: String) = value.encodeToByteArray().forEach { bytes[offset++] = it }
    fun le16(value: Int) {
        bytes[offset++] = value.toByte()
        bytes[offset++] = (value ushr 8).toByte()
    }
    fun le32(value: Int) {
        repeat(4) { shift -> bytes[offset++] = (value ushr (shift * 8)).toByte() }
    }
    ascii("RIFF")
    le32(36 + dataBytes)
    ascii("WAVEfmt ")
    le32(16)
    le16(1)
    le16(Channels)
    le32(SampleRate)
    le32(SampleRate * Channels * 2)
    le16(Channels * 2)
    le16(16)
    ascii("data")
    le32(dataBytes)
    repeat(frames) { frame ->
        val sample = (sin(2.0 * PI * 440.0 * frame / SampleRate) * Short.MAX_VALUE * 0.25).toInt()
        repeat(Channels) { le16(sample) }
    }
    return bytes
}

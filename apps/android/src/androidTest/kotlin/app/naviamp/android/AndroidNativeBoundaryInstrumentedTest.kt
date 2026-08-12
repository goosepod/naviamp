package app.naviamp.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.naviamp.android.playback.AndroidBassJni
import app.naviamp.android.security.AndroidKeystoreCredentialProtector
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNativeBoundaryInstrumentedTest {
    @Test
    fun androidKeystoreProtectsAndRevealsCredentials() {
        val protector = AndroidKeystoreCredentialProtector()
        val secret = "naviamp-device-test-${UUID.randomUUID()}"

        val first = requireNotNull(protector.protect(secret))
        val second = requireNotNull(protector.protect(secret))

        assertTrue(protector.isProtected(first))
        assertFalse(first.contains(secret))
        assertNotEquals(first, second, "Keystore encryption must use a fresh randomized IV")
        assertEquals(listOf(secret, secret), listOf(protector.reveal(first), protector.reveal(second)))
        assertTrue(protector.reveal("${first.dropLast(1)}x") == null, "Damaged ciphertext must fail closed")
    }

    @Test
    fun packagedBassLibrariesDecodeSeekAndReadAudio() {
        val bass = AndroidBassJni.load().getOrThrow()
        assertTrue(bass.version > 0)
        assertTrue(bass.mixerVersion > 0)
        assertTrue(bass.pluginDiagnostics.isNotEmpty())
        assertTrue(bass.pluginDiagnostics.all { it.loaded }, bass.pluginDiagnostics.joinToString())

        val wav = File.createTempFile("naviamp-bass-", ".wav")
        try {
            wav.writeBytes(testWav())
            val stream = bass.createFileDecodeStream(wav.absolutePath)
            assertTrue(stream != 0, "BASS failed to open WAV: error=${bass.lastErrorCode}")
            try {
                assertTrue((bass.durationSeconds(stream) ?: 0.0) > 0.9)
                assertTrue(bass.channelInfoFrequency(stream) == SampleRate)
                assertTrue(bass.channelInfoChannels(stream) == Channels)
                assertTrue(bass.seek(stream, 0.5), "BASS seek failed: error=${bass.lastErrorCode}")
                val samples = FloatArray(256)
                assertTrue(bass.readFloatData(stream, samples) > 0, "BASS read failed: error=${bass.lastErrorCode}")
                assertTrue(samples.any { it != 0f })
            } finally {
                assertTrue(bass.freeStream(stream))
            }
        } finally {
            wav.delete()
        }
    }
}

private const val SampleRate = 44_100
private const val Channels = 2

private fun testWav(): ByteArray {
    val frames = SampleRate
    val dataBytes = frames * Channels * 2
    return ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".encodeToByteArray())
        putInt(36 + dataBytes)
        put("WAVEfmt ".encodeToByteArray())
        putInt(16)
        putShort(1.toShort())
        putShort(Channels.toShort())
        putInt(SampleRate)
        putInt(SampleRate * Channels * 2)
        putShort((Channels * 2).toShort())
        putShort(16.toShort())
        put("data".encodeToByteArray())
        putInt(dataBytes)
        repeat(frames) { frame ->
            val sample = (sin(2.0 * PI * 440.0 * frame / SampleRate) * Short.MAX_VALUE * 0.25).toInt().toShort()
            repeat(Channels) { putShort(sample) }
        }
    }.array()
}

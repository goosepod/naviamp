package app.naviamp.desktop.playback.bass

import app.naviamp.domain.bass.BassActiveState
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopBassJniBindingIntegrationTest {
    @Test
    fun loadsBundledJniBindingWhenAvailable() {
        val libraryDirectory = DesktopBassLibraryResolver().resolve() ?: return
        val binding = DesktopBassJniBinding.loadFrom(libraryDirectory).getOrNull() ?: return

        assertTrue(binding.version > 0)
    }

    @Test
    fun controlsGeneratedWavThroughJniWhenBassIsAvailable() {
        val binding = loadBindingOrSkip() ?: return
        val wav = createSilentWavFile()
        try {
            if (!binding.init()) return
            val stream = binding.createFileStream(wav.absolutePath)
            assertTrue(stream != 0, "BASS stream should be created: ${binding.lastErrorCode}")

            assertTrue((binding.durationSeconds(stream) ?: 0.0) > 0.0)
            assertTrue(binding.channelInfoFrequency(stream) > 0)
            assertTrue(binding.channelInfoChannels(stream) > 0)
            assertTrue(binding.setVolume(stream, 0.25f))
            assertTrue(binding.seek(stream, 0.1))
            assertTrue((binding.positionSeconds(stream) ?: -1.0) >= 0.0)
            assertTrue(binding.play(stream))
            assertEquals(BassActiveState.Playing, binding.activeState(stream))
            assertTrue(binding.pause(stream))
            assertTrue(binding.stop(stream))
            assertTrue(binding.freeStream(stream))
        } finally {
            binding.free()
            wav.delete()
        }
    }

    @Test
    fun readsGeneratedWavDecodeDataThroughJniWhenBassIsAvailable() {
        val binding = loadBindingOrSkip() ?: return
        val wav = createSilentWavFile()
        try {
            val stream = binding.createFileDecodeStream(wav.absolutePath)
            assertTrue(stream != 0, "BASS decode stream should be created: ${binding.lastErrorCode}")

            assertNotNull(binding.lengthBytes(stream))
            val buffer = FloatArray(1024)
            assertTrue(binding.readFloatData(stream, buffer) >= 0)
            assertTrue(binding.freeStream(stream))
        } finally {
            wav.delete()
        }
    }

    @Test
    fun createsMixerAndReadsFftThroughJniWhenBassIsAvailable() {
        val binding = loadBindingOrSkip() ?: return
        if (!binding.init()) return
        try {
            val mixer = binding.createMixer(frequency = 44_100, channels = 2, queueSources = false)
            assertTrue(mixer != 0, "BASS mixer should be created: ${binding.lastErrorCode}")
            assertTrue(binding.fft(mixer, 64).isNotEmpty())
            assertTrue(binding.freeStream(mixer))
        } finally {
            binding.free()
        }
    }

    @Test
    fun receivesEndSyncCallbackThroughJniWhenBassIsAvailable() {
        val binding = loadBindingOrSkip() ?: return
        val wav = createSilentWavFile(seconds = 1)
        try {
            if (!binding.init()) return
            val stream = binding.createFileStream(wav.absolutePath)
            assertTrue(stream != 0, "BASS stream should be created: ${binding.lastErrorCode}")

            val ended = CountDownLatch(1)
            val sync = binding.setEndSync(stream) { channel ->
                if (channel == stream) ended.countDown()
            }
            assertTrue(sync != 0, "BASS end sync should be registered: ${binding.lastErrorCode}")
            assertTrue(binding.play(stream))
            assertTrue(ended.await(3, TimeUnit.SECONDS), "BASS end sync should fire")
            assertTrue(binding.freeStream(stream))
        } finally {
            binding.free()
            wav.delete()
        }
    }

    @Test
    fun desktopBackendLoadsJniBinding() {
        val backend = loadDesktopBassAudioBackend().getOrNull() ?: return

        assertTrue((backend.version ?: 0) > 0)
    }

    private fun loadBindingOrSkip(): DesktopBassJniBinding? {
        val libraryDirectory = DesktopBassLibraryResolver().resolve() ?: return null
        return DesktopBassJniBinding.loadFrom(libraryDirectory).getOrNull()
    }

    private fun createSilentWavFile(seconds: Int = 1): File =
        File.createTempFile("naviamp-jni-test", ".wav").also { file ->
            file.writeBytes(silentWavBytes(seconds = seconds))
        }

    private fun silentWavBytes(
        sampleRate: Int = 44_100,
        channels: Int = 2,
        seconds: Int = 1,
    ): ByteArray {
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val dataSize = sampleRate * channels * bytesPerSample * seconds
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = channels * bytesPerSample
        return ByteBuffer.allocate(44 + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putAscii("RIFF")
            .putInt(36 + dataSize)
            .putAscii("WAVE")
            .putAscii("fmt ")
            .putInt(16)
            .putShort(1)
            .putShort(channels.toShort())
            .putInt(sampleRate)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(bitsPerSample.toShort())
            .putAscii("data")
            .putInt(dataSize)
            .put(ByteArray(dataSize))
            .array()
    }

    private fun ByteBuffer.putAscii(value: String): ByteBuffer =
        put(value.toByteArray(Charsets.US_ASCII))
}

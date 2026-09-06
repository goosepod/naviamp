package app.naviamp.desktop.playback.bass

import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.playback.planStereoDownmix
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
    fun loadsBundledJniBinding() {
        val binding = requireBinding()

        assertTrue(binding.version > 0)
    }

    @Test
    fun controlsGeneratedWavThroughJni() {
        val binding = requireBinding()
        val wav = createSilentWavFile()
        try {
            assertTrue(binding.initForIntegrationTest(), "BASS should initialize: ${binding.lastErrorCode}")
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
    fun readsGeneratedWavDecodeDataThroughJni() {
        val binding = requireBinding()
        val wav = createSilentWavFile()
        try {
            assertTrue(binding.initForIntegrationTest(), "BASS should initialize: ${binding.lastErrorCode}")
            val stream = binding.createFileDecodeStream(wav.absolutePath)
            assertTrue(stream != 0, "BASS decode stream should be created: ${binding.lastErrorCode}")

            if (!usesNoSoundIntegrationDevice()) {
                assertTrue(
                    binding.applyEqualizer(stream, FloatArray(10) { index -> index - 5.0f }),
                    "BASS equalizer should process all shared bands on a decode stream: ${binding.lastErrorCode}",
                )
            }
            assertNotNull(binding.lengthBytes(stream))
            val buffer = FloatArray(1024)
            assertTrue(binding.readFloatData(stream, buffer) >= 0)
            assertTrue(binding.freeStream(stream))
        } finally {
            binding.free()
            wav.delete()
        }
    }

    @Test
    fun createsMixerAndReadsFftThroughJni() {
        val binding = requireBinding()
        assertTrue(binding.initForIntegrationTest(), "BASS should initialize: ${binding.lastErrorCode}")
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
    fun equalizerProcessesLowAndHighBandsAndClearsReplacedEffects() {
        if (usesNoSoundIntegrationDevice()) return
        val binding = requireBinding()
        assertTrue(binding.initForIntegrationTest())
        try {
            for ((index, frequency) in listOf(0 to 31, 1 to 62, 9 to 16_000)) {
                val baseline = decodedToneRms(binding, frequency, emptyList())
                val gains = FloatArray(10).also { it[index] = 6f }
                val boosted = decodedToneRms(binding, frequency, listOf(gains))
                assertTrue(boosted / baseline in 1.8..2.2, "$frequency Hz should gain 6 dB: ${boosted / baseline}")
                val replaced = decodedToneRms(binding, frequency, listOf(gains, gains))
                assertTrue(replaced / baseline in 1.8..2.2, "$frequency Hz must not accumulate old effects")
                val cleared = decodedToneRms(binding, frequency, listOf(gains, FloatArray(10)))
                assertEquals(baseline, cleared, 0.00001, "$frequency Hz should return to flat")
            }
        } finally {
            binding.free()
        }
    }

    private fun decodedToneRms(binding: DesktopBassJniBinding, frequency: Int, changes: List<FloatArray>): Double {
        val wav = createSilentWavFile()
        try {
            val bytes = wav.readBytes()
            val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (frame in 0 until 44_100) {
                val sample = (kotlin.math.sin(2 * Math.PI * frequency * frame / 44_100) * 1600).toInt().toShort()
                pcm.putShort(44 + frame * 4, sample)
                pcm.putShort(46 + frame * 4, sample)
            }
            wav.writeBytes(bytes)
            val stream = binding.createFileDecodeStream(wav.absolutePath)
            assertTrue(stream != 0)
            try {
                changes.forEach { assertTrue(binding.applyEqualizer(stream, it), "EQ failed: ${binding.lastErrorCode}") }
                val samples = FloatArray(44_100 * 2)
                assertEquals(samples.size, binding.readFloatData(stream, samples))
                // Ignore the initial filter transient and measure the settled second half.
                return kotlin.math.sqrt(samples.drop(samples.size / 2).sumOf { it.toDouble() * it } / (samples.size / 2))
            } finally {
                binding.freeStream(stream)
            }
        } finally {
            wav.delete()
        }
    }

    @Test
    fun appliesCoreStereoDownmixMatrixThroughJni() {
        val binding = requireBinding()
        val wav = createSilentWavFile(channels = 6)
        assertTrue(binding.initForIntegrationTest(), "BASS should initialize: ${binding.lastErrorCode}")
        try {
            val source = binding.createFileDecodeStream(wav.absolutePath)
            assertTrue(source != 0, "BASS should create a 5.1 decode stream: ${binding.lastErrorCode}")
            assertEquals(6, binding.channelInfoChannels(source))
            val mixer = binding.createMixer(frequency = 44_100, channels = 2, queueSources = false)
            assertTrue(mixer != 0, "BASS should create a stereo mixer: ${binding.lastErrorCode}")
            val matrix = requireNotNull(planStereoDownmix(6).matrix)
            assertTrue(
                binding.addMixerChannelWithMatrix(mixer, source, matrix.coefficients.toFloatArray()),
                "BASS should accept Core's 5.1 stereo matrix: ${binding.lastErrorCode}",
            )
            assertEquals(2, binding.channelInfoChannels(mixer))
            assertTrue(binding.freeStream(mixer))
            assertTrue(binding.freeStream(source))
        } finally {
            binding.free()
            wav.delete()
        }
    }

    @Test
    fun receivesEndSyncCallbackThroughJni() {
        val binding = requireBinding()
        val wav = createSilentWavFile(seconds = 1)
        try {
            assertTrue(binding.initForIntegrationTest(), "BASS should initialize: ${binding.lastErrorCode}")
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
        val backend = loadDesktopBassAudioBackend().getOrElse { failure ->
            throw AssertionError("Desktop BASS backend should load", failure)
        }

        assertTrue((backend.version ?: 0) > 0)
        assertTrue(
            backend.pluginDiagnostics.all { it.loaded },
            "Bundled Desktop codec plugins should register: ${backend.pluginDiagnostics.filterNot { it.loaded }}",
        )
    }

    private fun requireBinding(): DesktopBassJniBinding {
        val libraryDirectory = assertNotNull(
            DesktopBassLibraryResolver().resolveWithLibraries("bass", "naviamp_bass"),
            "Bundled Desktop BASS libraries should resolve",
        )
        return DesktopBassJniBinding.loadFrom(libraryDirectory).getOrElse { failure ->
            throw AssertionError("Bundled Desktop BASS JNI binding should load from $libraryDirectory", failure)
        }
    }

    private fun DesktopBassJniBinding.initForIntegrationTest(): Boolean =
        System.getProperty(TestOutputDeviceProperty)
            ?.let(::init)
            ?: init()

    private fun usesNoSoundIntegrationDevice(): Boolean =
        System.getProperty(TestOutputDeviceProperty) == NoSoundDeviceId

    private fun createSilentWavFile(seconds: Int = 1, channels: Int = 2): File =
        File.createTempFile("naviamp-jni-test", ".wav").also { file ->
            file.writeBytes(silentWavBytes(seconds = seconds, channels = channels))
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

    private companion object {
        const val TestOutputDeviceProperty = "naviamp.bass.test.outputDevice"
        const val NoSoundDeviceId = "0"
    }
}

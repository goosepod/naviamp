@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.naviamp.tvos.playback

import app.naviamp.apple.bass.native.BASS_GetConfig
import app.naviamp.apple.bass.native.BASS_Init
import app.naviamp.domain.bass.BassInternetStreamPolicy
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRandomAccessSource
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvosBassIntegrationTest {
    @Test
    fun directOutputClockMatchesTheNativeCursorAcrossPauseAndResume() = withWav { path, _ ->
        runBlocking {
            val backend = TvosBassAudioBackend()
            try {
                backend.init().getOrThrow()
                val stream = backend.createFileStream(path).getOrThrow()
                try {
                    backend.setVolume(stream, 0f).getOrThrow()
                    backend.play(stream).getOrThrow()
                    withTimeout(5_000) {
                        while ((backend.positionSeconds(stream) ?: 0.0) < 0.2) delay(20)
                    }
                    assertTrue((backend.bufferedOutputSeconds(stream) ?: 0.0) > 0.02,
                        "The clock check must exercise a populated output buffer")
                    val native = requireNotNull(backend.positionSeconds(stream))
                    val playing = requireNotNull(backend.audiblePositionSeconds(stream, stream))
                    assertTrue(kotlin.math.abs(native - playing) < 0.05, "Native=$native reported=$playing")
                    backend.pause(stream).getOrThrow()
                    // Read the paused cursor after the native output buffer has settled.
                    delay(200)
                    val paused = requireNotNull(backend.positionSeconds(stream))
                    assertEquals(paused, backend.audiblePositionSeconds(stream, stream))
                    delay(200)
                    assertEquals(paused, backend.positionSeconds(stream), "The paused output cursor must stay put")
                    backend.play(stream).getOrThrow()
                    val resumed = requireNotNull(backend.audiblePositionSeconds(stream, stream))
                    assertTrue(resumed >= paused, "Resuming must not subtract the queued audio twice")
                } finally { backend.freeStream(stream).getOrThrow() }
            } finally { backend.free().getOrThrow() }
        }
    }

    @Test
    fun publicNativeDecoderUsesSharedWaveformAndNetworkPolicy() = withWav { path, _ ->
        val backend = TvosBassAudioBackend()
        try {
            assertTrue(BASS_Init(0, 44_100u, 0u, null, null) != 0)
            assertFalse(backend.supportsMixer)
            assertNull(backend.mixerVersion)
            val plugins = backend.pluginDiagnostics
            assertEquals(setOf("bass", "bassflac", "bassopus"), plugins.map { it.stem }.toSet())
            assertTrue(plugins.all { it.loaded }, plugins.toString())
            val policy = BassInternetStreamPolicy(false, false, 3, 4_321, 40, 12_345, 23_456)
            try {
                backend.configureInternetStreams(policy).getOrThrow()
                for ((option, expected) in mapOf(21u to 0u, 71u to 0u, 59u to 3u, 12u to 4_321u,
                    15u to 40u, 11u to 12_345u, 37u to 23_456u)) {
                    assertEquals(expected, BASS_GetConfig(option))
                }
            } finally { backend.configureInternetStreams().getOrThrow() }
            val stream = backend.createFileDecodeStream(path).getOrThrow()
            try {
                backend.seek(stream, 0.5).getOrThrow()
                val samples = FloatArray(256)
                assertTrue(backend.readFloatData(stream, samples).getOrThrow() > 0)
                assertTrue(samples.any { it != 0f })
                val levels = backend.waveformLevels(stream, 8).getOrThrow()
                assertEquals(8, levels.size)
                assertTrue(levels.all { it in 0.17f..0.18f }, levels.toList().toString())
                backend.applyEqualizer(stream, listOf(1f, -1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)).getOrThrow()
            } finally { backend.freeStream(stream).getOrThrow() }
            for ((extension, bytes) in tvosCodecFixtures()) {
                val codecPath = "$path.$extension"
                try {
                    bytes.usePinned { pinned ->
                        check(NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                            .writeToFile(codecPath, true))
                    }
                    val decoded = backend.createFileDecodeStream(codecPath).getOrThrow()
                    try {
                        assertEquals(48_000, backend.channelInfo(decoded).getOrThrow().frequency)
                        assertTrue((backend.durationSeconds(decoded) ?: 0.0) in 0.24..0.27)
                        backend.seek(decoded, 0.1).getOrThrow()
                        val samples = FloatArray(256)
                        assertTrue(backend.readFloatData(decoded, samples).getOrThrow() > 0)
                        assertTrue(samples.any { it.isFinite() && it != 0f }, extension)
                    } finally { backend.freeStream(decoded).getOrThrow() }
                } finally { NSFileManager.defaultManager.removeItemAtPath(codecPath, null) }
            }
        } finally { backend.free().getOrThrow() }
    }

    @Test
    fun publicNativeFileCallbacksReadAndSeekCoreRandomAccessSource() = withWav { _, bytes ->
        val backend = TvosBassAudioBackend()
        try {
            assertTrue(BASS_Init(0, 44_100u, 0u, null, null) != 0)
            val source = object : PlaybackRandomAccessSource {
                override val lengthBytes = bytes.size.toLong()
                override suspend fun read(offset: Long, count: Int): ByteArray =
                    bytes.copyOfRange(offset.toInt(), minOf(bytes.size.toLong(), offset + count).toInt())
            }
            val stream = backend.createRandomAccessStream(source, decode = true).getOrThrow()
            try {
                backend.seek(stream, 0.5).getOrThrow()
                val samples = FloatArray(256)
                assertTrue(backend.readFloatData(stream, samples).getOrThrow() > 0)
                assertTrue(samples.any { it != 0f })
            } finally { backend.freeStream(stream).getOrThrow() }
        } finally { backend.free().getOrThrow() }
    }

    @Test
    fun coreEnginePlaysPausesSeeksAndFinishesThroughDirectPublicBass() = withWav { path, _ ->
        runBlocking {
            val engine = createTvosBassPlaybackEngine()
            val states = Channel<PlaybackState>(Channel.UNLIMITED)
            val previewStates = Channel<PlaybackState>(Channel.UNLIMITED)
            val progress = Channel<PlaybackProgress>(Channel.CONFLATED)
            suspend fun awaitState(expected: PlaybackState, source: ReceiveChannel<PlaybackState> = states) = withTimeout(15_000) {
                while (true) {
                    val actual = source.receive()
                    println("tvOS native state expected=$expected actual=$actual preview=${source === previewStates}")
                    check(actual !is PlaybackState.Error) { actual.toString() }
                    if (actual == expected) break
                }
            }
            suspend fun awaitPosition(minimum: Double): PlaybackProgress = withTimeout(10_000) {
                var current = progress.receive()
                while ((current.positionSeconds ?: 0.0) < minimum) current = progress.receive()
                println("tvOS native position minimum=$minimum actual=$current")
                current
            }
            try {
                assertFalse(engine.supportsGapless)
                assertFalse(engine.supportsCrossfade)
                assertFalse(engine.supportsStereoDownmix)
                assertTrue(engine.supportsEqualizer)
                assertTrue(engine.supportsReplayGain)
                engine.setVolume(0)
                engine.setCrossfadeDuration(5)
                engine.setStereoDownmixEnabled(true)
                val url = requireNotNull(NSURL.fileURLWithPath(path).absoluteString)
                engine.play(this, PlaybackRequest(url), { states.trySend(it) }, { progress.trySend(it) })
                awaitState(PlaybackState.Playing)
                val audioSession = AVAudioSession.sharedInstance()
                println("tvOS native session category=${audioSession.category} options=${audioSession.categoryOptions} bassFlags=${BASS_GetConfig(34u)}")
                assertEquals(AVAudioSessionCategoryPlayback, audioSession.category)
                assertEquals(0uL, audioSession.categoryOptions and AVAudioSessionCategoryOptionMixWithOthers,
                    "The native session must remain eligible for system Now Playing")
                val beforePreview = requireNotNull(awaitPosition(0.1).positionSeconds)
                engine.playPreview(this, PlaybackRequest(url), true, 0, { previewStates.trySend(it) })
                awaitState(PlaybackState.Playing, previewStates)
                awaitPosition(beforePreview + 0.1)
                engine.stopPreview()
                awaitState(PlaybackState.Stopped, previewStates)
                engine.pause()
                awaitState(PlaybackState.Paused)
                engine.seek(6.0)
                assertTrue((awaitPosition(5.9).positionSeconds ?: 0.0) >= 5.9)
                engine.resume()
                awaitState(PlaybackState.Playing)
                assertEquals(0uL, audioSession.categoryOptions and AVAudioSessionCategoryOptionMixWithOthers)
                awaitState(PlaybackState.Finished)
            } finally {
                engine.release()
                states.close()
                previewStates.close()
                progress.close()
            }
        }
    }
}

private fun withWav(block: (String, ByteArray) -> Unit) {
    val rate = 44_100
    // Allow the unchanged Core polling/startup policy to observe both channels before natural end.
    val frames = rate * 8
    val bytes = ByteArray(44 + frames * 4)
    var cursor = 0
    fun ascii(text: String) { text.encodeToByteArray().forEach { bytes[cursor++] = it } }
    fun le16(value: Int) { repeat(2) { bytes[cursor++] = (value ushr (it * 8)).toByte() } }
    fun le32(value: Int) { repeat(4) { bytes[cursor++] = (value ushr (it * 8)).toByte() } }
    ascii("RIFF"); le32(bytes.size - 8); ascii("WAVEfmt "); le32(16)
    le16(1); le16(2); le32(rate); le32(rate * 4); le16(4); le16(16)
    ascii("data"); le32(frames * 4)
    repeat(frames) { frame ->
        val sample = (sin(2.0 * PI * 440.0 * frame / rate) * Short.MAX_VALUE * 0.25).toInt()
        le16(sample); le16(sample)
    }
    val path = "${NSTemporaryDirectory().trimEnd('/')}/naviamp-tvos-${NSUUID.UUID().UUIDString}.wav"
    try {
        bytes.usePinned { pinned ->
            check(NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()).writeToFile(path, true))
        }
        block(path, bytes)
    } finally { NSFileManager.defaultManager.removeItemAtPath(path, null) }
}

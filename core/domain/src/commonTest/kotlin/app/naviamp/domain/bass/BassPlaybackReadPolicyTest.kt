package app.naviamp.domain.bass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class BassPlaybackReadPolicyTest {
    private val playback = BassStreamHandle(7)
    private val source = BassStreamHandle(8)

    @Test
    fun directOutputUsesItsAlreadyCompensatedNativePlaybackPosition() {
        val backend = ReadPolicyBackend(decoded = 10.0, buffered = 2.0)
        assertEquals(10.0, backend.audiblePositionSeconds(playback, BassStreamHandle(0)))
        assertEquals(listOf("decoded:7"), backend.calls)
    }

    @Test
    fun sameSourceAndPlaybackHandleUsesTheNativePlaybackPosition() {
        assertEquals(10.0, ReadPolicyBackend(decoded = 10.0, buffered = 2.0).audiblePositionSeconds(playback, playback))
    }

    @Test
    fun mixerHistoryWinsWithoutUsingItsDecodeCursorOrTheMixedOutputBuffer() {
        val backend = ReadPolicyBackend(decoded = 12.0, buffered = 5.0, mixerPosition = 9.0)
        assertEquals(9.0, backend.audiblePositionSeconds(playback, source))
        assertEquals(listOf("mixer:8"), backend.calls)
    }

    @Test
    fun absentOrInvalidMixerHistoryFallsBackToSourceWithoutSubtractingTheWholeMixerBuffer() {
        for (history in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val backend = ReadPolicyBackend(decoded = 12.0, buffered = 5.0, mixerPosition = history)
            assertEquals(12.0, backend.audiblePositionSeconds(playback, source))
            assertEquals(listOf("mixer:8", "decoded:8"), backend.calls)
        }
    }

    @Test
    fun absentOrInvalidDirectBufferFallsBackToDecodedPosition() {
        for (buffer in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(12.0, ReadPolicyBackend(decoded = 12.0, buffered = buffer).audiblePositionSeconds(playback, playback))
        }
    }

    @Test
    fun absentOrInvalidDecodePositionDoesNotProduceAnInvalidProgressValue() {
        for (decoded in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val backend = ReadPolicyBackend(decoded = decoded, buffered = 2.0)
            assertNull(backend.audiblePositionSeconds(playback, playback))
            assertEquals(listOf("decoded:7"), backend.calls)
        }
    }

    @Test
    fun queuedAudioCannotChangeTheNativePlaybackPosition() {
        assertEquals(1.0, ReadPolicyBackend(decoded = 1.0, buffered = 2.0).audiblePositionSeconds(playback, playback))
        assertEquals(0.0, ReadPolicyBackend(decoded = 0.0, buffered = 0.0).audiblePositionSeconds(playback, playback))
    }

    @Test
    fun directClockDoesNotJumpWhenPausedOrWhenItsOutputBufferRefillsAfterResume() {
        val backend = ReadPolicyBackend(decoded = 10.0, buffered = 2.0)
        assertEquals(10.0, backend.bassPlaybackSnapshot(7, 0).progress.positionSeconds)
        backend.active = BassActiveState.Paused
        assertEquals(10.0, backend.bassPlaybackSnapshot(7, 0).progress.positionSeconds)
        backend.active = BassActiveState.Playing
        backend.buffered = 3.0
        assertEquals(10.0, backend.bassPlaybackSnapshot(7, 0).progress.positionSeconds)
        backend.active = BassActiveState.Paused
        backend.decoded = 30.0
        backend.buffered = 0.0
        assertEquals(30.0, backend.bassPlaybackSnapshot(7, 0).progress.positionSeconds)
    }

    @Test
    fun defaultInternetSetupDeliversTheSharedPolicyToTheNativeEffect() {
        val backend = ReadPolicyBackend()
        backend.configureInternetStreams().getOrThrow()
        assertEquals(BassInternetStreamPolicy(), backend.networkPolicy)
        assertEquals(5_000, backend.networkPolicy?.bufferMillis)
        assertEquals(75, backend.networkPolicy?.prebufferPercent)
        assertEquals(15_000, backend.networkPolicy?.connectionTimeoutMillis)
    }

    @Test
    fun explicitInternetSetupDeliversTheSelectedPolicyWithoutReplacingItsValues() {
        val policy = BassInternetStreamPolicy(false, false, 0, 0, 0, 0, 0)
        val backend = ReadPolicyBackend()
        backend.configureInternetStreams(policy).getOrThrow()
        assertSame(policy, backend.networkPolicy)
    }

    @Test
    fun invalidNetworkValuesFailBeforeCrossingTheNativeBoundary() {
        for (invalid in listOf<() -> BassInternetStreamPolicy>(
            { BassInternetStreamPolicy(playlistDepth = -1) },
            { BassInternetStreamPolicy(bufferMillis = -1) },
            { BassInternetStreamPolicy(prebufferPercent = -1) },
            { BassInternetStreamPolicy(prebufferPercent = 101) },
            { BassInternetStreamPolicy(connectionTimeoutMillis = -1) },
            { BassInternetStreamPolicy(readTimeoutMillis = -1) },
        )) assertFailsWith<IllegalArgumentException> { invalid() }
    }
}

private class ReadPolicyBackend(
    var decoded: Double? = null,
    var buffered: Double? = null,
    val mixerPosition: Double? = null,
) : BassAudioBackend {
    var active = BassActiveState.Playing
    override fun activeState(stream: BassStreamHandle) = active
    val calls = mutableListOf<String>()
    var networkPolicy: BassInternetStreamPolicy? = null
    override fun positionSeconds(stream: BassStreamHandle): Double? {
        calls += "decoded:${stream.value}"
        return decoded
    }
    override fun bufferedOutputSeconds(stream: BassStreamHandle): Double? {
        calls += "buffered:${stream.value}"
        return buffered
    }
    override fun mixerSourcePositionSeconds(stream: BassStreamHandle): Double? {
        calls += "mixer:${stream.value}"
        return mixerPosition
    }
    override fun configureInternetStreams(policy: BassInternetStreamPolicy): Result<Unit> {
        networkPolicy = policy
        return Result.success(Unit)
    }
    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> = error("Unexpected create")
    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> = error("Unexpected create")
    override fun lengthBytes(stream: BassStreamHandle): Long? = error("Unexpected length")
    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> = error("Unexpected PCM")
    override fun freeStream(stream: BassStreamHandle): Result<Unit> = error("Unexpected release")
}

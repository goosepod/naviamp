package app.naviamp.domain.bass

import app.naviamp.domain.playback.planPreparedMixerTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BassAudioBackendTest {
    @Test
    fun unsupportedPlaybackOperationsReturnFailures() {
        val backend = RecordingBassAudioBackend()

        val result = backend.play(BassStreamHandle(1))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("BASS play"))
    }

    @Test
    fun exposesStableBassActiveStateValues() {
        assertEquals(0, BassActiveState.Stopped)
        assertEquals(1, BassActiveState.Playing)
        assertEquals(2, BassActiveState.Stalled)
        assertEquals(3, BassActiveState.Paused)
    }

    @Test
    fun formatsBassVersionIntegers() {
        assertEquals("2.4.17.0", bassVersionLabel(0x02041100))
    }

    @Test
    fun formatsBassErrorCodes() {
        assertEquals("no error", bassErrorMessage(0))
        assertEquals("connection timed out", bassErrorMessage(40))
        assertEquals("unknown BASS error", bassErrorMessage(-1))
        assertEquals("BASS error 999", bassErrorMessage(999))
    }

    @Test
    fun formatsBackendFailureMessages() {
        assertEquals(
            "BASS play failed: unknown BASS error",
            RecordingBassAudioBackend(lastErrorCode = null).bassFailureMessage("BASS play failed"),
        )
        assertEquals(
            "BASS play failed: invalid handle",
            RecordingBassAudioBackend(lastErrorCode = 5).bassFailureMessage("BASS play failed"),
        )
    }

    @Test
    fun formatsBassStreamActiveStateLabelWithFallback() {
        val backend = RecordingBassAudioBackend()

        assertEquals("No stream", backend.bassStreamActiveStateLabel(0, "No stream"))
        assertEquals("Playing", backend.bassStreamActiveStateLabel(7, "No stream"))
        assertEquals(listOf("active:7"), backend.calls)
    }

    @Test
    fun plansDistinctNonZeroHandlesForRelease() {
        assertEquals(
            listOf(BassStreamHandle(10), BassStreamHandle(20)),
            bassStreamHandlesForRelease(0, 10, 20, 10, 0),
        )
    }

    @Test
    fun releaseRemovesMixerChannelBeforeFreeingStream() {
        val backend = RecordingBassAudioBackend()

        val result = backend.releaseBassStream(BassStreamHandle(42))

        assertTrue(result.isSuccess)
        assertEquals(listOf("remove:42", "free:42"), backend.calls)
    }

    @Test
    fun releaseStillFreesWhenMixerRemovalFails() {
        val backend = RecordingBassAudioBackend(removeSucceeds = false)

        val result = backend.releaseBassStream(BassStreamHandle(42))

        assertTrue(result.isSuccess)
        assertEquals(listOf("remove:42", "free:42"), backend.calls)
    }

    @Test
    fun intReleaseHelperDelegatesToStreamHandleRelease() {
        val backend = RecordingBassAudioBackend()

        val result = backend.releaseBassStream(42)

        assertTrue(result.isSuccess)
        assertEquals(listOf("remove:42", "free:42"), backend.calls)
    }

    @Test
    fun stopsAndReleasesBassPlaybackHandles() {
        val backend = RecordingBassAudioBackend()

        val results = backend.stopAndReleaseBassPlayback(
            playbackHandle = 7,
            sourceHandle = 8,
            preparedHandle = 8,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(
            listOf(
                "stop:7",
                "remove:7",
                "free:7",
                "remove:8",
                "free:8",
            ),
            backend.calls,
        )
    }

    @Test
    fun releasesReplacedBassSourceWhenDifferentFromNextSource() {
        val backend = RecordingBassAudioBackend()

        val result = backend.releaseReplacedBassSource(
            currentSourceHandle = 7,
            nextSourceHandle = 8,
        )

        assertTrue(requireNotNull(result).isSuccess)
        assertEquals(listOf("remove:7", "free:7"), backend.calls)
    }

    @Test
    fun skipsReplacedBassSourceReleaseWhenCurrentSourceIsNextSource() {
        val backend = RecordingBassAudioBackend()

        val result = backend.releaseReplacedBassSource(
            currentSourceHandle = 7,
            nextSourceHandle = 7,
        )

        assertEquals(null, result)
        assertEquals(emptyList(), backend.calls)
    }

    @Test
    fun skipsReplacedBassSourceReleaseWhenNoCurrentSourceExists() {
        val backend = RecordingBassAudioBackend()

        val result = backend.releaseReplacedBassSource(
            currentSourceHandle = 0,
            nextSourceHandle = 7,
        )

        assertEquals(null, result)
        assertEquals(emptyList(), backend.calls)
    }

    @Test
    fun adoptsPreparedBassSourceByReleasingReplacedSourceAndRestoringVolume() {
        val backend = RecordingBassAudioBackend()

        val results = backend.adoptPreparedBassSource(
            playbackHandle = 7,
            currentSourceHandle = 8,
            nextSourceHandle = 9,
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.75f,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(
            listOf(
                "remove:8",
                "free:8",
                "volume:7:0.5",
                "volume:9:0.75",
            ),
            backend.calls,
        )
    }

    @Test
    fun adoptsPreparedBassSourceWithoutDuplicateReleaseForSameSource() {
        val backend = RecordingBassAudioBackend()

        val results = backend.adoptPreparedBassSource(
            playbackHandle = 7,
            currentSourceHandle = 9,
            nextSourceHandle = 9,
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.75f,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(listOf("volume:7:0.5", "volume:9:0.75"), backend.calls)
    }

    @Test
    fun intHandleHelpersDelegateToStreamHandleOperations() {
        val backend = RecordingBassAudioBackend()

        val volumeResult = backend.setVolume(7, 0.5f)
        val activeState = backend.activeState(7)

        assertTrue(volumeResult.isSuccess)
        assertEquals(BassActiveState.Playing, activeState)
        assertEquals(listOf("volume:7:0.5", "active:7"), backend.calls)
    }

    @Test
    fun appliesDirectBassPlaybackVolume() {
        val backend = RecordingBassAudioBackend()

        val results = backend.applyBassPlaybackVolume(
            outputStream = 7,
            sourceStream = 7,
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.8f,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(listOf("volume:7:0.4"), backend.calls)
    }

    @Test
    fun appliesSeparateOutputAndSourceBassPlaybackVolume() {
        val backend = RecordingBassAudioBackend()

        val results = backend.applyBassPlaybackVolume(
            outputStream = 7,
            sourceStream = 8,
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.8f,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(listOf("volume:7:0.5", "volume:8:0.8"), backend.calls)
    }

    @Test
    fun createsBassPlaybackVisualizerFrameFromFft() {
        val backend = RecordingBassAudioBackend()

        val frame = requireNotNull(backend.bassPlaybackVisualizerFrame(
            stream = 7,
            bins = 4,
            timestampMillis = 123L,
        ).getOrThrow())

        assertEquals(123L, frame.timestampMillis)
        assertTrue(frame.bands.isNotEmpty())
        assertEquals(listOf("fft:7:4"), backend.calls)
    }

    @Test
    fun createsBassPlaybackSnapshotFromPlaybackAndSourceHandles() {
        val backend = RecordingBassAudioBackend()

        val snapshot = backend.bassPlaybackSnapshot(
            playbackHandle = 7,
            sourceHandle = 8,
        )

        assertEquals(BassActiveState.Playing, snapshot.activeState)
        assertEquals(BassActiveState.Playing, snapshot.sourceActiveState)
        assertEquals(11.0, snapshot.progress.positionSeconds)
        assertEquals(12.5, snapshot.progress.decodedPositionSeconds)
        assertEquals(60.0, snapshot.progress.durationSeconds)
        assertEquals("Radio Title", snapshot.metadata.title)
        assertEquals(
            listOf(
                "positionSeconds:8",
                "active:7",
                "active:8",
                "audiblePositionSeconds:7:8",
                "durationSeconds:8",
                "metadata:8",
            ),
            backend.calls,
        )
    }

    @Test
    fun seeksBassPlaybackSourceHandleWhenPresent() {
        val backend = RecordingBassAudioBackend()

        val result = backend.seekBassPlaybackSource(
            playbackHandle = 7,
            sourceHandle = 8,
            seconds = 12.5,
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("seek:8:12.5"), backend.calls)
    }

    @Test
    fun seekBassPlaybackSourceFailsWhenNoHandleExists() {
        val backend = RecordingBassAudioBackend()

        val result = backend.seekBassPlaybackSource(
            playbackHandle = 0,
            sourceHandle = 0,
            seconds = 12.5,
        )

        assertTrue(result.isFailure)
        assertEquals(emptyList(), backend.calls)
    }

    @Test
    fun mutesDistinctBassPlaybackStreams() {
        val backend = RecordingBassAudioBackend()

        val results = backend.setBassPlaybackMuted(
            outputStream = 7,
            sourceStream = 8,
            muted = true,
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.8f,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(listOf("volume:7:0.0", "volume:8:0.0"), backend.calls)
    }

    @Test
    fun restoresMutedBassPlaybackVolume() {
        val backend = RecordingBassAudioBackend()

        val results = backend.setBassPlaybackMuted(
            outputStream = 7,
            sourceStream = 8,
            muted = false,
            userVolumeFactor = 0.5f,
            replayGainFactor = 0.8f,
        )

        assertTrue(results.all { it.isSuccess })
        assertEquals(listOf("volume:7:0.5", "volume:8:0.8"), backend.calls)
    }

    @Test
    fun createsLocalPlaybackDecodeStreamWhenRequested() {
        val backend = RecordingBassAudioBackend()

        val result = backend.createPlaybackStream(
            localPath = "/tmp/song.flac",
            url = "file:///tmp/song.flac",
            decode = true,
            playbackDecode = true,
        )

        assertEquals(BassStreamHandle(13), result.getOrThrow())
        assertEquals(listOf("filePlaybackDecode:/tmp/song.flac"), backend.calls)
    }

    @Test
    fun createsQueuedBassSourceThroughSharedDecodeSelection() {
        val backend = RecordingBassAudioBackend()

        val result = backend.createQueuedBassSource(
            localPath = "/tmp/song.flac",
            url = "file:///tmp/song.flac",
            playbackDecode = false,
        )

        assertEquals(12, result.getOrThrow())
        assertEquals(listOf("fileDecode:/tmp/song.flac"), backend.calls)
    }

    @Test
    fun createsUrlPlaybackStreamWhenLocalPathIsAbsent() {
        val backend = RecordingBassAudioBackend()

        val result = backend.createPlaybackStream(
            localPath = null,
            url = "https://example.test/song.flac",
            decode = false,
        )

        assertEquals(BassStreamHandle(11), result.getOrThrow())
        assertEquals(listOf("url:https://example.test/song.flac"), backend.calls)
    }

    @Test
    fun createsDirectBassPlaybackFromSharedStreamSelection() {
        val backend = RecordingBassAudioBackend()

        val result = backend.createDirectBassPlayback(
            localPath = "/tmp/song.flac",
            url = "file:///tmp/song.flac",
            replayGainFactor = 0.75f,
        )

        assertEquals(
            BassCreatedPlayback(
                playbackHandle = 10,
                sourceHandle = 10,
                replayGainFactor = 0.75f,
            ),
            result.getOrThrow(),
        )
        assertEquals(listOf("file:/tmp/song.flac"), backend.calls)
    }

    @Test
    fun directBassPlaybackPropagatesStreamCreationFailuresWithoutRelease() {
        val backend = RecordingBassAudioBackend(createFileStreamSucceeds = false)

        val result = backend.createDirectBassPlayback(
            localPath = "/tmp/missing.flac",
            url = "file:///tmp/missing.flac",
            replayGainFactor = 0.75f,
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("file:/tmp/missing.flac"), backend.calls)
    }

    @Test
    fun createsMixerBassPlaybackFromSharedBackendPrimitives() {
        val backend = RecordingBassAudioBackend()

        val result = backend.createMixerBassPlayback(
            localPath = "/tmp/song.flac",
            url = "file:///tmp/song.flac",
            crossfadeDurationSeconds = 3,
            replayGainFactor = 0.75f,
            playbackDecode = true,
        )

        assertEquals(
            BassCreatedPlayback(
                playbackHandle = 30,
                sourceHandle = 13,
                replayGainFactor = 0.75f,
            ),
            result.getOrThrow(),
        )
        assertEquals(
            listOf(
                "filePlaybackDecode:/tmp/song.flac",
                "info:13",
                "mixer:48000:2:false",
                "volume:13:0.75",
                "add:30:13",
            ),
            backend.calls,
        )
    }

    @Test
    fun mixerBassPlaybackPropagatesSourceCreationFailuresWithoutRelease() {
        val backend = RecordingBassAudioBackend(createPlaybackDecodeSucceeds = false)

        val result = backend.createMixerBassPlayback(
            localPath = "/tmp/unsupported.dsf",
            url = "file:///tmp/unsupported.dsf",
            crossfadeDurationSeconds = 3,
            replayGainFactor = 0.75f,
            playbackDecode = true,
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("filePlaybackDecode:/tmp/unsupported.dsf"), backend.calls)
    }

    @Test
    fun mixerBassPlaybackReleasesSourceWhenMixerCreationFails() {
        val backend = RecordingBassAudioBackend(createMixerSucceeds = false)

        val result = backend.createMixerBassPlayback(
            localPath = "/tmp/song.flac",
            url = "file:///tmp/song.flac",
            crossfadeDurationSeconds = 3,
            replayGainFactor = 0.75f,
            playbackDecode = true,
        )

        assertTrue(result.isFailure)
        assertEquals(
            listOf(
                "filePlaybackDecode:/tmp/song.flac",
                "info:13",
                "mixer:48000:2:false",
                "remove:13",
                "free:13",
            ),
            backend.calls,
        )
    }

    @Test
    fun mixerBassPlaybackReleasesMixerAndSourceWhenAddFails() {
        val backend = RecordingBassAudioBackend(addSucceeds = false)

        val result = backend.createMixerBassPlayback(
            localPath = "/tmp/song.flac",
            url = "file:///tmp/song.flac",
            crossfadeDurationSeconds = 3,
            replayGainFactor = 0.75f,
            playbackDecode = true,
        )

        assertTrue(result.isFailure)
        assertEquals(
            listOf(
                "filePlaybackDecode:/tmp/song.flac",
                "info:13",
                "mixer:48000:2:false",
                "volume:13:0.75",
                "add:30:13",
                "remove:30",
                "free:30",
                "remove:13",
                "free:13",
            ),
            backend.calls,
        )
    }

    @Test
    fun createsBassPlaybackThroughSharedDirectOrMixerSelector() {
        val directBackend = RecordingBassAudioBackend()
        val mixerBackend = RecordingBassAudioBackend()

        val direct = directBackend.createBassPlayback(
            localPath = "/tmp/song.flac",
            url = "file:///tmp/song.flac",
            useMixer = false,
            crossfadeDurationSeconds = 5,
            replayGainFactor = 0.5f,
        )
        val mixer = mixerBackend.createBassPlayback(
            localPath = "/tmp/song.flac",
            url = "file:///tmp/song.flac",
            useMixer = true,
            crossfadeDurationSeconds = 5,
            replayGainFactor = 0.5f,
            playbackDecode = true,
        )

        assertEquals(10, direct.getOrThrow().playbackHandle)
        assertEquals(listOf("file:/tmp/song.flac"), directBackend.calls)
        assertEquals(30, mixer.getOrThrow().playbackHandle)
        assertEquals(
            listOf(
                "filePlaybackDecode:/tmp/song.flac",
                "info:13",
                "mixer:48000:2:false",
                "volume:13:0.5",
                "add:30:13",
            ),
            mixerBackend.calls,
        )
    }

    @Test
    fun preparesNextBassMixerSourceThroughSharedTransitionPath() {
        val backend = RecordingBassAudioBackend()

        val result = backend.prepareNextBassMixerSource(
            localPath = "/tmp/next.flac",
            url = "file:///tmp/next.flac",
            mixer = 1,
            currentSource = 2,
            currentSourceVolumeFactor = 0.8f,
            crossfadeDurationSeconds = 5,
            replayGainFactor = 0.7f,
            playbackDecode = true,
        )

        assertEquals(
            BassPreparedSource(
                sourceHandle = 13,
                crossfadeActive = true,
            ),
            result.getOrThrow(),
        )
        assertEquals(
            listOf(
                "filePlaybackDecode:/tmp/next.flac",
                "volume:13:0.0",
                "add:1:13",
                "volume:13:0.0",
                "slide:13:0.7:5000",
                "volume:2:0.8",
                "slide:2:0.0:5000",
            ),
            backend.calls,
        )
    }

    @Test
    fun preparedNextBassMixerSourcePropagatesSourceCreationFailuresWithoutRelease() {
        val backend = RecordingBassAudioBackend(createPlaybackDecodeSucceeds = false)

        val result = backend.prepareNextBassMixerSource(
            localPath = "/tmp/unsupported.dsf",
            url = "file:///tmp/unsupported.dsf",
            mixer = 1,
            currentSource = 2,
            currentSourceVolumeFactor = 0.8f,
            crossfadeDurationSeconds = 5,
            replayGainFactor = 0.7f,
            playbackDecode = true,
        )

        assertTrue(result.isFailure)
        assertEquals(listOf("filePlaybackDecode:/tmp/unsupported.dsf"), backend.calls)
    }

    @Test
    fun preparedNextBassMixerSourceReleasesSourceWhenTransitionFails() {
        val backend = RecordingBassAudioBackend(slideSucceeds = false)

        val result = backend.prepareNextBassMixerSource(
            localPath = "/tmp/next.flac",
            url = "file:///tmp/next.flac",
            mixer = 1,
            currentSource = 2,
            currentSourceVolumeFactor = 0.8f,
            crossfadeDurationSeconds = 5,
            replayGainFactor = 0.7f,
            playbackDecode = true,
        )

        assertTrue(result.isFailure)
        assertEquals(
            listOf(
                "filePlaybackDecode:/tmp/next.flac",
                "volume:13:0.0",
                "add:1:13",
                "volume:13:0.0",
                "slide:13:0.7:5000",
                "remove:13",
                "free:13",
            ),
            backend.calls,
        )
    }

    @Test
    fun preparedMixerTransitionAppliesVolumeSlidesForCrossfade() {
        val backend = RecordingBassAudioBackend()

        val result = backend.applyPreparedBassMixerTransition(
            mixer = BassStreamHandle(1),
            nextSource = BassStreamHandle(2),
            currentSource = BassStreamHandle(3),
            currentSourceVolumeFactor = 0.8f,
            transition = planPreparedMixerTransition(crossfadeDurationSeconds = 5, replayGainFactor = 0.7f),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(
                "volume:2:0.0",
                "add:1:2",
                "volume:2:0.0",
                "slide:2:0.7:5000",
                "volume:3:0.8",
                "slide:3:0.0:5000",
            ),
            backend.calls,
        )
    }

    @Test
    fun preparedMixerTransitionFailsWhenCrossfadeSlideFails() {
        val backend = RecordingBassAudioBackend(slideSucceeds = false)

        val result = backend.applyPreparedBassMixerTransition(
            mixer = BassStreamHandle(1),
            nextSource = BassStreamHandle(2),
            currentSource = BassStreamHandle(3),
            currentSourceVolumeFactor = 0.8f,
            transition = planPreparedMixerTransition(crossfadeDurationSeconds = 5, replayGainFactor = 0.7f),
        )

        assertTrue(result.isFailure)
        assertEquals(
            listOf(
                "volume:2:0.0",
                "add:1:2",
                "volume:2:0.0",
                "slide:2:0.7:5000",
            ),
            backend.calls,
        )
    }

    @Test
    fun preparedMixerTransitionIntOverloadTreatsZeroCurrentSourceAsAbsent() {
        val backend = RecordingBassAudioBackend()

        val result = backend.applyPreparedBassMixerTransition(
            mixer = 1,
            nextSource = 2,
            currentSource = 0,
            currentSourceVolumeFactor = 0.8f,
            transition = planPreparedMixerTransition(crossfadeDurationSeconds = 5, replayGainFactor = 0.7f),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf(
                "volume:2:0.0",
                "add:1:2",
                "volume:2:0.0",
                "slide:2:0.7:5000",
            ),
            backend.calls,
        )
    }
}

private class RecordingBassAudioBackend(
    private val removeSucceeds: Boolean = true,
    private val slideSucceeds: Boolean = true,
    private val createFileStreamSucceeds: Boolean = true,
    private val createPlaybackDecodeSucceeds: Boolean = true,
    private val createMixerSucceeds: Boolean = true,
    private val addSucceeds: Boolean = true,
    override val lastErrorCode: Int? = null,
) : BassAudioBackend {
    val calls = mutableListOf<String>()

    override fun createFileStream(path: String): Result<BassStreamHandle> {
        calls += "file:$path"
        return if (createFileStreamSucceeds) {
            Result.success(BassStreamHandle(10))
        } else {
            Result.failure(IllegalStateException("file stream failed"))
        }
    }

    override fun createUrlStream(url: String): Result<BassStreamHandle> {
        calls += "url:$url"
        return Result.success(BassStreamHandle(11))
    }

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> {
        calls += "fileDecode:$path"
        return Result.success(BassStreamHandle(12))
    }

    override fun createFilePlaybackDecodeStream(path: String): Result<BassStreamHandle> {
        calls += "filePlaybackDecode:$path"
        return if (createPlaybackDecodeSucceeds) {
            Result.success(BassStreamHandle(13))
        } else {
            Result.failure(IllegalStateException("playback decode failed"))
        }
    }

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> {
        calls += "urlDecode:$url"
        return Result.success(BassStreamHandle(14))
    }

    override fun channelInfo(stream: BassStreamHandle): Result<BassStreamInfo> {
        calls += "info:${stream.value}"
        return Result.success(BassStreamInfo(frequency = 48_000, channels = 2))
    }

    override fun createMixer(
        frequency: Int,
        channels: Int,
        queueSources: Boolean,
    ): Result<BassStreamHandle> {
        calls += "mixer:$frequency:$channels:$queueSources"
        return if (createMixerSucceeds) {
            Result.success(BassStreamHandle(30))
        } else {
            Result.failure(IllegalStateException("mixer failed"))
        }
    }

    override fun lengthBytes(stream: BassStreamHandle): Long? =
        error("Not used")

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> =
        error("Not used")

    override fun removeMixerChannel(stream: BassStreamHandle): Result<Unit> {
        calls += "remove:${stream.value}"
        return if (removeSucceeds) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("not attached"))
        }
    }

    override fun freeStream(stream: BassStreamHandle): Result<Unit> {
        calls += "free:${stream.value}"
        return Result.success(Unit)
    }

    override fun stop(stream: BassStreamHandle): Result<Unit> {
        calls += "stop:${stream.value}"
        return Result.success(Unit)
    }

    override fun addMixerChannel(
        mixer: BassStreamHandle,
        stream: BassStreamHandle,
    ): Result<Unit> {
        calls += "add:${mixer.value}:${stream.value}"
        return if (addSucceeds) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("add failed"))
        }
    }

    override fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> {
        calls += "volume:${stream.value}:$volume"
        return Result.success(Unit)
    }

    override fun activeState(stream: BassStreamHandle): Int {
        calls += "active:${stream.value}"
        return BassActiveState.Playing
    }

    override fun positionSeconds(stream: BassStreamHandle): Double {
        calls += "positionSeconds:${stream.value}"
        return 12.5
    }

    override fun audiblePositionSeconds(
        playbackStream: BassStreamHandle,
        sourceStream: BassStreamHandle,
    ): Double {
        calls += "audiblePositionSeconds:${playbackStream.value}:${sourceStream.value}"
        return 11.0
    }

    override fun durationSeconds(stream: BassStreamHandle): Double {
        calls += "durationSeconds:${stream.value}"
        return 60.0
    }

    override fun seek(stream: BassStreamHandle, seconds: Double): Result<Unit> {
        calls += "seek:${stream.value}:$seconds"
        return Result.success(Unit)
    }

    override fun streamMetadata(stream: BassStreamHandle): Map<String, String> {
        calls += "metadata:${stream.value}"
        return mapOf("StreamTitle" to "Radio Title")
    }

    override fun slideVolume(
        stream: BassStreamHandle,
        volume: Float,
        durationMillis: Int,
    ): Result<Unit> {
        calls += "slide:${stream.value}:$volume:$durationMillis"
        return if (slideSucceeds) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("slide failed"))
        }
    }

    override fun fft(stream: BassStreamHandle, bins: Int): Result<FloatArray> {
        calls += "fft:${stream.value}:$bins"
        return Result.success(FloatArray(bins) { index -> index / bins.toFloat() })
    }

}

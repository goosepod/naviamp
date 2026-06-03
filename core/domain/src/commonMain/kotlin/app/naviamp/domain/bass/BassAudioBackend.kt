package app.naviamp.domain.bass

import app.naviamp.domain.playback.PreparedMixerTransitionPlan
import app.naviamp.domain.playback.crossfadeFadeInEnvelopePoints
import app.naviamp.domain.playback.crossfadeFadeOutEnvelopePoints

@JvmInline
value class BassStreamHandle(val value: Int)

object BassActiveState {
    const val Stopped: Int = 0
    const val Playing: Int = 1
    const val Stalled: Int = 2
    const val Paused: Int = 3
}

fun bassActiveStateLabel(activeState: Int): String =
    when (activeState) {
        BassActiveState.Stopped -> "Stopped"
        BassActiveState.Playing -> "Playing"
        BassActiveState.Stalled -> "Stalled"
        BassActiveState.Paused -> "Paused"
        else -> "Unknown ($activeState)"
    }

data class BassStreamInfo(
    val frequency: Int,
    val channels: Int,
)

data class BassPreparedMixerTransitionResult(
    val fallbackErrors: List<Throwable> = emptyList(),
)

interface BassAudioBackend {
    val version: Int?
        get() = null

    val lastErrorCode: Int?
        get() = null

    val supportsMixer: Boolean
        get() = false

    fun init(): Result<Unit> = unsupportedBassOperation("BASS init")

    fun free(): Result<Unit> = unsupportedBassOperation("BASS free")

    fun setVerifyNet(verify: Boolean): Result<Unit> = Result.success(Unit)

    fun configureInternetStreams(): Result<Unit> = Result.success(Unit)

    fun createFileStream(path: String): Result<BassStreamHandle> =
        unsupportedBassOperation("BASS file stream creation")

    fun createUrlStream(url: String): Result<BassStreamHandle> =
        unsupportedBassOperation("BASS URL stream creation")

    fun createFileDecodeStream(path: String): Result<BassStreamHandle>

    fun createFilePlaybackDecodeStream(path: String): Result<BassStreamHandle> =
        createFileDecodeStream(path)

    fun createUrlDecodeStream(url: String): Result<BassStreamHandle>

    fun channelInfo(stream: BassStreamHandle): Result<BassStreamInfo> =
        unsupportedBassOperation("BASS channel info")

    fun createMixer(
        frequency: Int,
        channels: Int,
        queueSources: Boolean,
    ): Result<BassStreamHandle> = unsupportedBassOperation("BASS mixer creation")

    fun addMixerChannel(
        mixer: BassStreamHandle,
        stream: BassStreamHandle,
    ): Result<Unit> = unsupportedBassOperation("BASS mixer channel add")

    fun removeMixerChannel(stream: BassStreamHandle): Result<Unit> =
        unsupportedBassOperation("BASS mixer channel remove")

    fun setMixerVolumeEnvelope(
        stream: BassStreamHandle,
        points: List<Pair<Long, Float>>,
    ): Result<Unit> = unsupportedBassOperation("BASS mixer volume envelope")

    fun setEndSync(
        stream: BassStreamHandle,
        callback: (BassStreamHandle) -> Unit,
    ): Result<Int> = unsupportedBassOperation("BASS end sync")

    fun play(stream: BassStreamHandle): Result<Unit> = unsupportedBassOperation("BASS play")

    fun pause(stream: BassStreamHandle): Result<Unit> = unsupportedBassOperation("BASS pause")

    fun stop(stream: BassStreamHandle): Result<Unit> = unsupportedBassOperation("BASS stop")

    fun activeState(stream: BassStreamHandle): Int? = null

    fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> =
        unsupportedBassOperation("BASS volume")

    fun slideVolume(
        stream: BassStreamHandle,
        volume: Float,
        durationMillis: Int,
    ): Result<Unit> = unsupportedBassOperation("BASS volume slide")

    fun seek(stream: BassStreamHandle, seconds: Double): Result<Unit> =
        unsupportedBassOperation("BASS seek")

    fun positionSeconds(stream: BassStreamHandle): Double? = null

    fun durationSeconds(stream: BassStreamHandle): Double? = null

    fun positionBytes(stream: BassStreamHandle): Long? = null

    fun secondsToBytes(stream: BassStreamHandle, seconds: Double): Long? = null

    fun lengthBytes(stream: BassStreamHandle): Long?

    fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int>

    fun fft(stream: BassStreamHandle, bins: Int): Result<FloatArray> =
        unsupportedBassOperation("BASS FFT")

    fun streamMetadata(stream: BassStreamHandle): Map<String, String> = emptyMap()

    fun freeStream(stream: BassStreamHandle): Result<Unit>
}

fun bassStreamHandlesForRelease(vararg handles: Int): List<BassStreamHandle> =
    handles
        .filter { it != 0 }
        .distinct()
        .map(::BassStreamHandle)

fun BassAudioBackend.releaseBassStream(stream: BassStreamHandle): Result<Unit> {
    removeMixerChannel(stream)
    return freeStream(stream)
}

fun BassAudioBackend.releaseBassStreams(vararg handles: Int): List<Result<Unit>> =
    bassStreamHandlesForRelease(*handles).map(::releaseBassStream)

fun BassAudioBackend.applyPreparedBassMixerTransition(
    mixer: BassStreamHandle,
    nextSource: BassStreamHandle,
    currentSource: BassStreamHandle?,
    currentSourceVolumeFactor: Float,
    transition: PreparedMixerTransitionPlan,
): Result<BassPreparedMixerTransitionResult> =
    runCatching {
        val fallbackErrors = mutableListOf<Throwable>()
        setVolume(nextSource, transition.initialNextSourceVolume).getOrThrow()
        addMixerChannel(mixer, nextSource).getOrThrow()
        if (!transition.shouldCrossfade) {
            return@runCatching BassPreparedMixerTransitionResult()
        }

        applyPreparedBassFadeIn(nextSource, transition)
            .onFailure { fallbackErrors += it }
        if (currentSource != null && transition.shouldFadeCurrentSource) {
            applyPreparedBassFadeOut(currentSource, currentSourceVolumeFactor, transition)
                .onFailure { fallbackErrors += it }
        }
        BassPreparedMixerTransitionResult(fallbackErrors = fallbackErrors)
    }

private fun BassAudioBackend.applyPreparedBassFadeIn(
    nextSource: BassStreamHandle,
    transition: PreparedMixerTransitionPlan,
): Result<Unit> {
    val durationBytes = secondsToBytes(nextSource, transition.crossfadeDurationSeconds.toDouble())
    if (durationBytes == null) {
        setVolume(nextSource, 0f)
        slideVolume(nextSource, transition.finalNextSourceVolume, transition.durationMillis)
        return Result.success(Unit)
    }
    val envelopeResult = setMixerVolumeEnvelope(
        nextSource,
        crossfadeFadeInEnvelopePoints(
            durationBytes = durationBytes,
            volumeFactor = transition.finalNextSourceVolume,
        ),
    )
    if (envelopeResult.isSuccess) return Result.success(Unit)
    setVolume(nextSource, 0f)
    slideVolume(nextSource, transition.finalNextSourceVolume, transition.durationMillis)
    return Result.failure(requireNotNull(envelopeResult.exceptionOrNull()))
}

private fun BassAudioBackend.applyPreparedBassFadeOut(
    currentSource: BassStreamHandle,
    currentSourceVolumeFactor: Float,
    transition: PreparedMixerTransitionPlan,
): Result<Unit> {
    val startBytes = positionBytes(currentSource)
    val durationBytes = secondsToBytes(currentSource, transition.crossfadeDurationSeconds.toDouble())
    if (startBytes == null || durationBytes == null) {
        slideVolume(currentSource, 0f, transition.durationMillis)
        return Result.success(Unit)
    }
    val envelopeResult = setMixerVolumeEnvelope(
        currentSource,
        crossfadeFadeOutEnvelopePoints(
            startBytes = startBytes,
            durationBytes = durationBytes,
            volumeFactor = currentSourceVolumeFactor,
        ),
    )
    if (envelopeResult.isSuccess) return Result.success(Unit)
    slideVolume(currentSource, 0f, transition.durationMillis)
    return Result.failure(requireNotNull(envelopeResult.exceptionOrNull()))
}

private fun <T> unsupportedBassOperation(name: String): Result<T> =
    Result.failure(UnsupportedOperationException("$name is not supported by this BASS backend."))

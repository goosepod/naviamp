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

fun bassVersionLabel(version: Int): String {
    val major = version ushr 24 and 0xff
    val minor = version ushr 16 and 0xff
    val revision = version ushr 8 and 0xff
    val build = version and 0xff
    return "$major.$minor.$revision.$build"
}

fun bassErrorMessage(code: Int): String =
    when (code) {
        0 -> "no error"
        1 -> "memory error"
        2 -> "file open error"
        3 -> "driver error"
        4 -> "buffer lost"
        5 -> "invalid handle"
        6 -> "unsupported sample format"
        7 -> "invalid position"
        8 -> "BASS_Init has not been called"
        9 -> "BASS_Start has not been called"
        14 -> "already initialized"
        18 -> "no available channel"
        19 -> "illegal type"
        20 -> "illegal parameter"
        21 -> "no 3D support"
        22 -> "no EAX support"
        23 -> "illegal device"
        24 -> "not playing"
        25 -> "illegal sample rate"
        27 -> "not a file stream"
        29 -> "no hardware voices"
        31 -> "empty file"
        32 -> "non-internet file"
        33 -> "could not create file"
        34 -> "no effects available"
        37 -> "requested data/action is not available"
        38 -> "channel is decoding"
        39 -> "channel is not a decoding channel"
        40 -> "connection timed out"
        41 -> "unsupported file format"
        42 -> "speaker unavailable"
        43 -> "BASS version mismatch"
        44 -> "codec unavailable"
        45 -> "ended"
        46 -> "device busy"
        47 -> "unsupported protocol"
        48 -> "unsupported protocol"
        49 -> "access denied"
        50 -> "SSL unavailable"
        -1 -> "unknown BASS error"
        else -> "BASS error $code"
    }

fun BassAudioBackend.bassFailureMessage(prefix: String): String =
    "$prefix: ${lastErrorCode?.let(::bassErrorMessage) ?: "unknown BASS error"}"

data class BassStreamInfo(
    val frequency: Int,
    val channels: Int,
)

data class BassPreparedMixerTransitionResult(
    val fallbackErrors: List<Throwable> = emptyList(),
)

data class BassPluginDiagnostic(
    val stem: String,
    val loaded: Boolean,
    val errorCode: Int? = null,
)

interface BassAudioBackend {
    val version: Int?
        get() = null

    val mixerVersion: Int?
        get() = null

    val lastErrorCode: Int?
        get() = null

    val mixerError: String?
        get() = null

    val libraryDirectory: String?
        get() = null

    val pluginDiagnostics: List<BassPluginDiagnostic>
        get() = emptyList()

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

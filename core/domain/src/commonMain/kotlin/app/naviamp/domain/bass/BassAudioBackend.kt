package app.naviamp.domain.bass

import app.naviamp.domain.playback.PreparedMixerTransitionPlan
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.crossfadeFadeInEnvelopePoints
import app.naviamp.domain.playback.crossfadeFadeOutEnvelopePoints
import app.naviamp.domain.playback.planBassMixerCreation
import app.naviamp.domain.playback.planPreparedMixerTransition
import app.naviamp.domain.playback.playbackSourceHandle
import app.naviamp.domain.playback.playbackVisualizerFrameFromFft
import app.naviamp.domain.playback.playbackVolumeApplicationPlan

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

fun BassAudioBackend.bassStreamActiveStateLabel(
    stream: Int,
    noStreamLabel: String,
): String =
    stream
        .takeIf { it != 0 }
        ?.let { bassActiveStateLabel(activeState(it)) }
        ?: noStreamLabel

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

data class BassCreatedPlayback(
    val playbackHandle: Int,
    val sourceHandle: Int,
    val replayGainFactor: Float,
)

data class BassPlaybackSnapshot(
    val activeState: Int,
    val sourceActiveState: Int?,
    val progress: PlaybackProgress,
    val metadata: PlaybackStreamMetadata,
)

data class BassPreparedSource(
    val sourceHandle: Int,
    val replayGainFactor: Float,
    val crossfadeActive: Boolean,
    val fallbackErrors: List<Throwable> = emptyList(),
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

fun BassAudioBackend.releaseBassStream(stream: Int): Result<Unit> =
    releaseBassStream(BassStreamHandle(stream))

fun BassAudioBackend.releaseBassStreams(vararg handles: Int): List<Result<Unit>> =
    bassStreamHandlesForRelease(*handles).map(::releaseBassStream)

fun BassAudioBackend.stopAndReleaseBassPlayback(
    playbackHandle: Int,
    sourceHandle: Int,
    preparedHandle: Int,
): List<Result<Unit>> {
    val results = mutableListOf<Result<Unit>>()
    if (playbackHandle != 0) {
        results += stop(playbackHandle)
    }
    results += releaseBassStreams(playbackHandle, sourceHandle, preparedHandle)
    return results
}

fun BassAudioBackend.play(stream: Int): Result<Unit> =
    play(BassStreamHandle(stream))

fun BassAudioBackend.pause(stream: Int): Result<Unit> =
    pause(BassStreamHandle(stream))

fun BassAudioBackend.stop(stream: Int): Result<Unit> =
    stop(BassStreamHandle(stream))

fun BassAudioBackend.activeState(stream: Int): Int =
    activeState(BassStreamHandle(stream)) ?: BassActiveState.Stopped

fun BassAudioBackend.addMixerChannel(mixer: Int, stream: Int): Result<Unit> =
    addMixerChannel(BassStreamHandle(mixer), BassStreamHandle(stream))

fun BassAudioBackend.setEndSync(
    stream: Int,
    callback: (BassStreamHandle) -> Unit,
): Result<Int> =
    setEndSync(BassStreamHandle(stream), callback)

fun BassAudioBackend.setVolume(stream: Int, volume: Float): Result<Unit> =
    setVolume(BassStreamHandle(stream), volume)

fun BassAudioBackend.slideVolume(stream: Int, volume: Float, durationMillis: Int): Result<Unit> =
    slideVolume(BassStreamHandle(stream), volume, durationMillis)

fun BassAudioBackend.seek(stream: Int, seconds: Double): Result<Unit> =
    seek(BassStreamHandle(stream), seconds)

fun BassAudioBackend.positionSeconds(stream: Int): Double? =
    positionSeconds(BassStreamHandle(stream))

fun BassAudioBackend.durationSeconds(stream: Int): Double? =
    durationSeconds(BassStreamHandle(stream))

fun BassAudioBackend.channelInfo(stream: Int): Result<BassStreamInfo> =
    channelInfo(BassStreamHandle(stream))

fun BassAudioBackend.fft(stream: Int, bins: Int): Result<FloatArray> =
    fft(BassStreamHandle(stream), bins)

fun BassAudioBackend.streamMetadata(stream: Int): Map<String, String> =
    streamMetadata(BassStreamHandle(stream))

fun BassAudioBackend.createPlaybackStream(
    localPath: String?,
    url: String,
    decode: Boolean,
    playbackDecode: Boolean = false,
): Result<BassStreamHandle> =
    if (localPath != null) {
        when {
            decode && playbackDecode -> createFilePlaybackDecodeStream(localPath)
            decode -> createFileDecodeStream(localPath)
            else -> createFileStream(localPath)
        }
    } else {
        if (decode) createUrlDecodeStream(url) else createUrlStream(url)
    }

fun BassAudioBackend.createQueuedBassSource(
    localPath: String?,
    url: String,
    playbackDecode: Boolean = false,
): Result<Int> =
    createPlaybackStream(
        localPath = localPath,
        url = url,
        decode = true,
        playbackDecode = playbackDecode,
    ).map { it.value }

fun BassAudioBackend.createDirectBassPlayback(
    localPath: String?,
    url: String,
    replayGainFactor: Float,
): Result<BassCreatedPlayback> =
    createPlaybackStream(
        localPath = localPath,
        url = url,
        decode = false,
    ).map { handle ->
        BassCreatedPlayback(
            playbackHandle = handle.value,
            sourceHandle = handle.value,
            replayGainFactor = replayGainFactor,
        )
    }

fun BassAudioBackend.createMixerBassPlayback(
    localPath: String?,
    url: String,
    crossfadeDurationSeconds: Int,
    replayGainFactor: Float,
    playbackDecode: Boolean = false,
): Result<BassCreatedPlayback> =
    runCatching {
        val source = createPlaybackStream(
            localPath = localPath,
            url = url,
            decode = true,
            playbackDecode = playbackDecode,
        ).getOrThrow()
        val mixerPlan = planBassMixerCreation(
            sourceInfo = channelInfo(source).getOrNull(),
            crossfadeDurationSeconds = crossfadeDurationSeconds,
        )
        val mixer = createMixer(
            frequency = mixerPlan.frequency,
            channels = mixerPlan.channels,
            queueSources = mixerPlan.queueSources,
        ).getOrThrow()
        setVolume(source, replayGainFactor)
        addMixerChannel(mixer, source).getOrThrow()
        BassCreatedPlayback(
            playbackHandle = mixer.value,
            sourceHandle = source.value,
            replayGainFactor = replayGainFactor,
        )
    }

fun BassAudioBackend.createBassPlayback(
    localPath: String?,
    url: String,
    useMixer: Boolean,
    crossfadeDurationSeconds: Int,
    replayGainFactor: Float,
    playbackDecode: Boolean = false,
): Result<BassCreatedPlayback> =
    if (useMixer) {
        createMixerBassPlayback(
            localPath = localPath,
            url = url,
            crossfadeDurationSeconds = crossfadeDurationSeconds,
            replayGainFactor = replayGainFactor,
            playbackDecode = playbackDecode,
        )
    } else {
        createDirectBassPlayback(
            localPath = localPath,
            url = url,
            replayGainFactor = replayGainFactor,
        )
    }

fun BassAudioBackend.prepareNextBassMixerSource(
    localPath: String?,
    url: String,
    mixer: Int,
    currentSource: Int,
    currentSourceVolumeFactor: Float,
    crossfadeDurationSeconds: Int,
    replayGainFactor: Float,
    playbackDecode: Boolean = false,
): Result<BassPreparedSource> =
    runCatching {
        val source = createQueuedBassSource(
            localPath = localPath,
            url = url,
            playbackDecode = playbackDecode,
        ).getOrThrow()
        val transition = planPreparedMixerTransition(crossfadeDurationSeconds, replayGainFactor)
        val transitionResult = applyPreparedBassMixerTransition(
            mixer = mixer,
            nextSource = source,
            currentSource = currentSource,
            currentSourceVolumeFactor = currentSourceVolumeFactor,
            transition = transition,
        ).getOrThrow()
        BassPreparedSource(
            sourceHandle = source,
            replayGainFactor = replayGainFactor,
            crossfadeActive = transition.shouldCrossfade,
            fallbackErrors = transitionResult.fallbackErrors,
        )
    }

fun BassAudioBackend.applyBassPlaybackVolume(
    outputStream: Int,
    sourceStream: Int,
    userVolumeFactor: Float,
    replayGainFactor: Float,
): List<Result<Unit>> {
    if (outputStream == 0) return emptyList()
    val hasSeparateSourceStream = sourceStream != 0 && outputStream != sourceStream
    val plan = playbackVolumeApplicationPlan(
        userVolumeFactor = userVolumeFactor,
        replayGainFactor = replayGainFactor,
        hasSeparateSourceStream = hasSeparateSourceStream,
    )
    return if (hasSeparateSourceStream) {
        listOf(
            setVolume(outputStream, plan.outputVolumeFactor),
            setVolume(sourceStream, plan.sourceReplayGainFactor ?: 1f),
        )
    } else {
        listOf(setVolume(outputStream, plan.directVolumeFactor))
    }
}

fun BassAudioBackend.setBassPlaybackMuted(
    outputStream: Int,
    sourceStream: Int,
    muted: Boolean,
    userVolumeFactor: Float,
    replayGainFactor: Float,
): List<Result<Unit>> {
    if (!muted) {
        return applyBassPlaybackVolume(
            outputStream = outputStream,
            sourceStream = sourceStream,
            userVolumeFactor = userVolumeFactor,
            replayGainFactor = replayGainFactor,
        )
    }
    return bassStreamHandlesForRelease(outputStream, sourceStream)
        .map { stream -> setVolume(stream, 0f) }
}

fun BassAudioBackend.bassPlaybackVisualizerFrame(
    stream: Int,
    bins: Int,
    timestampMillis: Long,
): Result<PlaybackVisualizerFrame?> =
    fft(stream, bins).map { fft ->
        playbackVisualizerFrameFromFft(
            fft = fft,
            timestampMillis = timestampMillis,
        )
    }

fun BassAudioBackend.bassPlaybackSnapshot(
    playbackHandle: Int,
    sourceHandle: Int,
): BassPlaybackSnapshot {
    val progressHandle = playbackSourceHandle(playbackHandle, sourceHandle)
    return BassPlaybackSnapshot(
        activeState = activeState(playbackHandle),
        sourceActiveState = sourceHandle.takeIf { it != 0 }?.let(::activeState),
        progress = PlaybackProgress(
            positionSeconds = positionSeconds(progressHandle),
            durationSeconds = durationSeconds(progressHandle),
        ),
        metadata = PlaybackStreamMetadata.fromProperties(streamMetadata(progressHandle)),
    )
}

fun BassAudioBackend.seekBassPlaybackSource(
    playbackHandle: Int,
    sourceHandle: Int,
    seconds: Double,
): Result<Unit> {
    val target = playbackSourceHandle(playbackHandle, sourceHandle)
    return if (target != 0) {
        seek(target, seconds)
    } else {
        Result.failure(IllegalStateException("No BASS stream available to seek."))
    }
}

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

fun BassAudioBackend.applyPreparedBassMixerTransition(
    mixer: Int,
    nextSource: Int,
    currentSource: Int,
    currentSourceVolumeFactor: Float,
    transition: PreparedMixerTransitionPlan,
): Result<BassPreparedMixerTransitionResult> =
    applyPreparedBassMixerTransition(
        mixer = BassStreamHandle(mixer),
        nextSource = BassStreamHandle(nextSource),
        currentSource = currentSource.takeIf { it != 0 }?.let(::BassStreamHandle),
        currentSourceVolumeFactor = currentSourceVolumeFactor,
        transition = transition,
    )

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

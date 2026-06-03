package app.naviamp.domain.bass

@JvmInline
value class BassStreamHandle(val value: Int)

object BassActiveState {
    const val Stopped: Int = 0
    const val Playing: Int = 1
    const val Stalled: Int = 2
    const val Paused: Int = 3
}

data class BassStreamInfo(
    val frequency: Int,
    val channels: Int,
)

interface BassAudioBackend {
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

private fun <T> unsupportedBassOperation(name: String): Result<T> =
    Result.failure(UnsupportedOperationException("$name is not supported by this BASS backend."))

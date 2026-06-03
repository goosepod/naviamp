package app.naviamp.desktop.playback.bass

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamInfo
import app.naviamp.domain.bass.BassStreamHandle
import java.io.File

class DesktopBassAudioBackend(
    private val native: BassNative,
) : BassAudioBackend {
    private val endSyncCallbacks: MutableMap<Int, BassSyncCallback> = mutableMapOf()

    override val version: Int
        get() = native.version

    override val mixerVersion: Int?
        get() = native.mixerVersion

    override val lastErrorCode: Int
        get() = native.errorCode()

    override val mixerError: String?
        get() = native.mixerError

    override val libraryDirectory: String
        get() = native.libraryDirectory.absolutePath

    override val supportsMixer: Boolean
        get() = native.supportsMixer

    override fun init(): Result<Unit> =
        native.init()

    override fun free(): Result<Unit> =
        native.free()

    override fun configureInternetStreams(): Result<Unit> =
        native.configureInternetStreams()

    override fun createFileStream(path: String): Result<BassStreamHandle> =
        native.createFileStream(File(path)).map(::BassStreamHandle)

    override fun createUrlStream(url: String): Result<BassStreamHandle> =
        native.createUrlStream(url).map(::BassStreamHandle)

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> {
        native.loadAvailablePlugins()
        return native.createFileDecodeStream(File(path)).map(::BassStreamHandle)
    }

    override fun createFilePlaybackDecodeStream(path: String): Result<BassStreamHandle> {
        native.loadAvailablePlugins()
        return native.createFilePlaybackDecodeStream(File(path)).map(::BassStreamHandle)
    }

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> {
        native.loadAvailablePlugins()
        return native.createUrlDecodeStream(url).map(::BassStreamHandle)
    }

    override fun channelInfo(stream: BassStreamHandle): Result<BassStreamInfo> =
        native.channelInfo(stream.value).map { info ->
            BassStreamInfo(
                frequency = info.freq,
                channels = info.chans,
            )
        }

    override fun createMixer(
        frequency: Int,
        channels: Int,
        queueSources: Boolean,
    ): Result<BassStreamHandle> =
        native.createMixer(frequency, channels, queueSources).map(::BassStreamHandle)

    override fun addMixerChannel(
        mixer: BassStreamHandle,
        stream: BassStreamHandle,
    ): Result<Unit> =
        native.addMixerChannel(mixer.value, stream.value)

    override fun removeMixerChannel(stream: BassStreamHandle): Result<Unit> =
        native.removeMixerChannel(stream.value)

    override fun setMixerVolumeEnvelope(
        stream: BassStreamHandle,
        points: List<Pair<Long, Float>>,
    ): Result<Unit> =
        native.setMixerVolumeEnvelope(stream.value, points)

    override fun setEndSync(
        stream: BassStreamHandle,
        callback: (BassStreamHandle) -> Unit,
    ): Result<Int> {
        val nativeCallback = BassSyncCallback { _, channel, _, _ ->
            callback(BassStreamHandle(channel))
        }
        return native.setEndSync(stream.value, nativeCallback)
            .onSuccess { endSyncCallbacks[stream.value] = nativeCallback }
    }

    override fun play(stream: BassStreamHandle): Result<Unit> =
        native.play(stream.value)

    override fun pause(stream: BassStreamHandle): Result<Unit> =
        native.pause(stream.value)

    override fun stop(stream: BassStreamHandle): Result<Unit> =
        native.stop(stream.value)

    override fun activeState(stream: BassStreamHandle): Int =
        native.activeState(stream.value)

    override fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> =
        native.setVolume(stream.value, volume)

    override fun slideVolume(
        stream: BassStreamHandle,
        volume: Float,
        durationMillis: Int,
    ): Result<Unit> =
        native.slideVolume(stream.value, volume, durationMillis)

    override fun seek(stream: BassStreamHandle, seconds: Double): Result<Unit> =
        native.seek(stream.value, seconds)

    override fun positionSeconds(stream: BassStreamHandle): Double? =
        native.positionSeconds(stream.value)

    override fun durationSeconds(stream: BassStreamHandle): Double? =
        native.durationSeconds(stream.value)

    override fun positionBytes(stream: BassStreamHandle): Long? =
        native.positionBytes(stream.value)

    override fun secondsToBytes(stream: BassStreamHandle, seconds: Double): Long? =
        native.secondsToBytes(stream.value, seconds)

    override fun lengthBytes(stream: BassStreamHandle): Long? =
        native.lengthBytes(stream.value)

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> =
        native.readFloatData(stream.value, buffer)

    override fun fft(stream: BassStreamHandle, bins: Int): Result<FloatArray> =
        native.fft(stream.value, bins)

    override fun streamMetadata(stream: BassStreamHandle): Map<String, String> =
        native.streamMetadata(stream.value)

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        native.freeStream(stream.value)
}

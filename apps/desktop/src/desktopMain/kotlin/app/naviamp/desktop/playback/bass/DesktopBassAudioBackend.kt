package app.naviamp.desktop.playback.bass

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import java.io.File

class DesktopBassAudioBackend(
    private val native: BassNative,
) : BassAudioBackend {
    override val lastErrorCode: Int
        get() = native.errorCode()

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

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> {
        native.loadAvailablePlugins()
        return native.createUrlDecodeStream(url).map(::BassStreamHandle)
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

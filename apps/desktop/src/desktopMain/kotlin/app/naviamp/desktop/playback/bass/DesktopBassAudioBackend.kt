package app.naviamp.desktop.playback.bass

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import java.io.File

class DesktopBassAudioBackend(
    private val native: BassNative,
) : BassAudioBackend {
    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> {
        native.loadAvailablePlugins()
        return native.createFileDecodeStream(File(path)).map(::BassStreamHandle)
    }

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> {
        native.loadAvailablePlugins()
        return native.createUrlDecodeStream(url).map(::BassStreamHandle)
    }

    override fun lengthBytes(stream: BassStreamHandle): Long? =
        native.lengthBytes(stream.value)

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> =
        native.readFloatData(stream.value, buffer)

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        native.freeStream(stream.value)
}

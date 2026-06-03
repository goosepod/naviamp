package app.naviamp.android.playback

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle

class AndroidBassAudioBackend(
    private val bass: AndroidBassJni,
) : BassAudioBackend {
    override fun setVerifyNet(verify: Boolean): Result<Unit> =
        if (bass.setVerifyNet(verify)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_SetConfig VERIFY_NET failed")))
        }

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> =
        bass.createFileDecodeStream(path)
            .takeIf { it != 0 }
            ?.let { Result.success(BassStreamHandle(it)) }
            ?: Result.failure(IllegalStateException(errorMessage("BASS_StreamCreateFile decode failed")))

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> =
        bass.createUrlDecodeStream(url)
            .takeIf { it != 0 }
            ?.let { Result.success(BassStreamHandle(it)) }
            ?: Result.failure(IllegalStateException(errorMessage("BASS_StreamCreateURL decode failed")))

    override fun lengthBytes(stream: BassStreamHandle): Long? =
        bass.lengthBytes(stream.value)

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> {
        val read = bass.readFloatData(stream.value, buffer)
        return if (read >= 0) {
            Result.success(read)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_ChannelGetData failed")))
        }
    }

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        if (bass.freeStream(stream.value)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_StreamFree failed")))
        }

    private fun errorMessage(prefix: String): String =
        "$prefix: BASS error ${bass.lastErrorCode}"
}

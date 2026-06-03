package app.naviamp.domain.bass

@JvmInline
value class BassStreamHandle(val value: Int)

interface BassAudioBackend {
    fun setVerifyNet(verify: Boolean): Result<Unit> = Result.success(Unit)

    fun createFileDecodeStream(path: String): Result<BassStreamHandle>

    fun createUrlDecodeStream(url: String): Result<BassStreamHandle>

    fun lengthBytes(stream: BassStreamHandle): Long?

    fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int>

    fun freeStream(stream: BassStreamHandle): Result<Unit>
}

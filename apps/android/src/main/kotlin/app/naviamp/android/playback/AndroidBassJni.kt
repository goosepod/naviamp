package app.naviamp.android.playback

object AndroidBassJni {
    fun load(): Result<AndroidBassJni> =
        runCatching {
            AndroidBassNativeLoader.loadBundledLibraries().also { report ->
                check(report.available) { "BASS core library is not loaded." }
            }
            System.loadLibrary("naviamp_bass")
            this
        }

    val version: Int
        get() = nativeBassVersion()

    val lastErrorCode: Int
        get() = nativeLastErrorCode()

    fun init(): Boolean = nativeInit()

    fun free() = nativeFree()

    fun setVerifyNet(verify: Boolean): Boolean = nativeSetVerifyNet(verify)

    fun createUrlStream(url: String): Int = nativeCreateUrlStream(url)

    fun createFileStream(path: String): Int = nativeCreateFileStream(path)

    fun play(stream: Int): Boolean = nativePlay(stream)

    fun pause(stream: Int): Boolean = nativePause(stream)

    fun stop(stream: Int): Boolean = nativeStop(stream)

    fun freeStream(stream: Int): Boolean = nativeFreeStream(stream)

    fun activeState(stream: Int): Int = nativeActiveState(stream)

    fun setVolume(stream: Int, volume: Float): Boolean = nativeSetVolume(stream, volume)

    fun seek(stream: Int, seconds: Double): Boolean = nativeSeek(stream, seconds)

    fun positionSeconds(stream: Int): Double? = nativePositionSeconds(stream).takeIf { it >= 0.0 }

    fun durationSeconds(stream: Int): Double? = nativeDurationSeconds(stream).takeIf { it > 0.0 }

    fun streamTags(stream: Int): Array<String> = nativeStreamTags(stream)

    fun fft(stream: Int, bins: Int): FloatArray = nativeFft(stream, bins)

    private external fun nativeBassVersion(): Int
    private external fun nativeLastErrorCode(): Int
    private external fun nativeInit(): Boolean
    private external fun nativeFree()
    private external fun nativeSetVerifyNet(verify: Boolean): Boolean
    private external fun nativeCreateUrlStream(url: String): Int
    private external fun nativeCreateFileStream(path: String): Int
    private external fun nativePlay(stream: Int): Boolean
    private external fun nativePause(stream: Int): Boolean
    private external fun nativeStop(stream: Int): Boolean
    private external fun nativeFreeStream(stream: Int): Boolean
    private external fun nativeActiveState(stream: Int): Int
    private external fun nativeSetVolume(stream: Int, volume: Float): Boolean
    private external fun nativeSeek(stream: Int, seconds: Double): Boolean
    private external fun nativePositionSeconds(stream: Int): Double
    private external fun nativeDurationSeconds(stream: Int): Double
    private external fun nativeStreamTags(stream: Int): Array<String>
    private external fun nativeFft(stream: Int, bins: Int): FloatArray
}

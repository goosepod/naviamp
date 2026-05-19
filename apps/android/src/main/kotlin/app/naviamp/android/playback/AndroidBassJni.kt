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

    private external fun nativeBassVersion(): Int
    private external fun nativeLastErrorCode(): Int
}


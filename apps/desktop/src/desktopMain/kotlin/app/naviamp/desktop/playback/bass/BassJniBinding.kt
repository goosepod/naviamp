package app.naviamp.desktop.playback.bass

import java.io.File

class BassJniBinding private constructor() {
    val version: Int
        get() = nativeBassVersion()

    val lastErrorCode: Int
        get() = nativeLastErrorCode()

    private external fun nativeBassVersion(): Int
    private external fun nativeLastErrorCode(): Int

    companion object {
        fun load(libraryName: String = "naviamp_bass"): Result<BassJniBinding> =
            runCatching {
                System.loadLibrary(libraryName)
                BassJniBinding()
            }

        fun loadFrom(
            directory: File,
            platform: BassPlatform = BassPlatform.current(),
        ): Result<BassJniBinding> =
            runCatching {
                System.load(File(directory, platform.libraryName("naviamp_bass")).absolutePath)
                BassJniBinding()
            }
    }
}

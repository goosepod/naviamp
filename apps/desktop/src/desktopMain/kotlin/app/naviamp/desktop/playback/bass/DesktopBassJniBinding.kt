package app.naviamp.desktop.playback.bass

import java.io.File

class DesktopBassJniBinding private constructor() {
    val version: Int
        get() = nativeBassVersion()

    val lastErrorCode: Int
        get() = nativeLastErrorCode()

    private external fun nativeBassVersion(): Int
    private external fun nativeLastErrorCode(): Int

    companion object {
        fun load(libraryName: String = "naviamp_bass"): Result<DesktopBassJniBinding> =
            runCatching {
                System.loadLibrary(libraryName)
                DesktopBassJniBinding()
            }

        fun loadFrom(
            directory: File,
            platform: BassPlatform = BassPlatform.current(),
        ): Result<DesktopBassJniBinding> =
            runCatching {
                System.load(File(directory, platform.libraryName("naviamp_bass")).absolutePath)
                DesktopBassJniBinding()
            }
    }
}

package app.naviamp.desktop.playback.bass

import java.io.File

class DesktopBassJniBinding private constructor(
    val libraryDirectory: File,
    private val platform: BassPlatform,
) {
    private val endSyncCallbacks: MutableMap<Int, (Int) -> Unit> = mutableMapOf()
    private val loadedPlugins: MutableMap<String, DesktopBassJniPlugin> = mutableMapOf()

    val version: Int
        get() = nativeBassVersion()

    val mixerVersion: Int
        get() = nativeMixerVersion()

    val lastErrorCode: Int
        get() = nativeLastErrorCode()

    fun init(): Boolean = nativeInit()

    fun free() = nativeFree()

    fun configureInternetStreams(): Boolean = nativeConfigureInternetStreams()

    fun createUrlStream(url: String): Int = nativeCreateUrlStream(url)

    fun createFileStream(path: String): Int = nativeCreateFileStream(path)

    fun createUrlDecodeStream(url: String): Int = nativeCreateUrlDecodeStream(url)

    fun createFileDecodeStream(path: String): Int = nativeCreateFileDecodeStream(path)

    fun createMixer(frequency: Int, channels: Int, queueSources: Boolean): Int =
        nativeCreateMixer(frequency, channels, queueSources)

    fun channelInfoFrequency(stream: Int): Int = nativeChannelInfoFrequency(stream)

    fun channelInfoChannels(stream: Int): Int = nativeChannelInfoChannels(stream)

    fun addMixerChannel(mixer: Int, stream: Int): Boolean = nativeAddMixerChannel(mixer, stream)

    fun removeMixerChannel(stream: Int): Boolean = nativeRemoveMixerChannel(stream)

    fun setEndSync(stream: Int, callback: (Int) -> Unit): Int {
        endSyncCallbacks[stream] = callback
        val sync = nativeSetEndSync(stream)
        if (sync == 0) {
            endSyncCallbacks.remove(stream)
        }
        return sync
    }

    @Suppress("unused")
    private fun onEndSync(stream: Int) {
        endSyncCallbacks.remove(stream)?.invoke(stream)
    }

    fun play(stream: Int): Boolean = nativePlay(stream)

    fun pause(stream: Int): Boolean = nativePause(stream)

    fun stop(stream: Int): Boolean = nativeStop(stream)

    fun freeStream(stream: Int): Boolean = nativeFreeStream(stream)

    fun activeState(stream: Int): Int = nativeActiveState(stream)

    fun setVolume(stream: Int, volume: Float): Boolean = nativeSetVolume(stream, volume)

    fun slideVolume(stream: Int, volume: Float, millis: Int): Boolean = nativeSlideVolume(stream, volume, millis)

    fun seek(stream: Int, seconds: Double): Boolean = nativeSeek(stream, seconds)

    fun positionSeconds(stream: Int): Double? = nativePositionSeconds(stream).takeIf { it >= 0.0 }

    fun durationSeconds(stream: Int): Double? = nativeDurationSeconds(stream).takeIf { it > 0.0 }

    fun lengthBytes(stream: Int): Long? = nativeLengthBytes(stream).takeIf { it > 0L }

    fun streamTags(stream: Int): Array<String> = nativeStreamTags(stream)

    fun fft(stream: Int, bins: Int): FloatArray = nativeFft(stream, bins)

    fun readFloatData(stream: Int, buffer: FloatArray): Int = nativeReadFloatData(stream, buffer)

    fun loadAvailablePlugins(): List<DesktopBassJniPlugin> =
        DesktopBassJniPlugins.names.mapNotNull { stem ->
            val cached = loadedPlugins[stem]
            if (cached != null) return@mapNotNull cached

            val file = File(libraryDirectory, platform.libraryName(stem))
            if (!file.isFile) return@mapNotNull null

            val handle = nativeLoadPlugin(file.absolutePath)
            DesktopBassJniPlugin(
                stem = stem,
                file = file,
                handle = handle,
                loaded = handle != 0,
                errorCode = if (handle == 0) lastErrorCode else null,
            ).also { loadedPlugins[stem] = it }
        }

    private external fun nativeBassVersion(): Int
    private external fun nativeMixerVersion(): Int
    private external fun nativeLastErrorCode(): Int
    private external fun nativeInit(): Boolean
    private external fun nativeFree()
    private external fun nativeConfigureInternetStreams(): Boolean
    private external fun nativeCreateUrlStream(url: String): Int
    private external fun nativeCreateFileStream(path: String): Int
    private external fun nativeCreateUrlDecodeStream(url: String): Int
    private external fun nativeCreateFileDecodeStream(path: String): Int
    private external fun nativeCreateMixer(frequency: Int, channels: Int, queueSources: Boolean): Int
    private external fun nativeChannelInfoFrequency(stream: Int): Int
    private external fun nativeChannelInfoChannels(stream: Int): Int
    private external fun nativeAddMixerChannel(mixer: Int, stream: Int): Boolean
    private external fun nativeRemoveMixerChannel(stream: Int): Boolean
    private external fun nativeSetEndSync(stream: Int): Int
    private external fun nativePlay(stream: Int): Boolean
    private external fun nativePause(stream: Int): Boolean
    private external fun nativeStop(stream: Int): Boolean
    private external fun nativeFreeStream(stream: Int): Boolean
    private external fun nativeActiveState(stream: Int): Int
    private external fun nativeSetVolume(stream: Int, volume: Float): Boolean
    private external fun nativeSlideVolume(stream: Int, volume: Float, millis: Int): Boolean
    private external fun nativeSeek(stream: Int, seconds: Double): Boolean
    private external fun nativePositionSeconds(stream: Int): Double
    private external fun nativeDurationSeconds(stream: Int): Double
    private external fun nativeLengthBytes(stream: Int): Long
    private external fun nativeStreamTags(stream: Int): Array<String>
    private external fun nativeFft(stream: Int, bins: Int): FloatArray
    private external fun nativeReadFloatData(stream: Int, buffer: FloatArray): Int
    private external fun nativeLoadPlugin(path: String): Int

    companion object {
        fun load(libraryName: String = "naviamp_bass"): Result<DesktopBassJniBinding> =
            runCatching {
                val directory = DesktopBassLibraryResolver().resolve()
                    ?: error("Could not find BASS library directory.")
                System.loadLibrary(libraryName)
                DesktopBassJniBinding(directory, BassPlatform.current())
            }

        fun loadFrom(
            directory: File,
            platform: BassPlatform = BassPlatform.current(),
        ): Result<DesktopBassJniBinding> =
            runCatching {
                System.load(File(directory, platform.libraryName("naviamp_bass")).absolutePath)
                DesktopBassJniBinding(directory, platform)
            }
    }
}

data class DesktopBassJniPlugin(
    val stem: String,
    val file: File,
    val handle: Int,
    val loaded: Boolean,
    val errorCode: Int?,
)

private object DesktopBassJniPlugins {
    val names: List<String> = listOf(
        "bass_aac",
        "bassflac",
        "bassopus",
        "bassalac",
        "bassape",
        "bassdsd",
        "bass_mpc",
        "basshls",
        "basswebm",
        "bassmidi",
        "bassmix",
        "bass_fx",
        "basswv",
        "basswma",
    )
}

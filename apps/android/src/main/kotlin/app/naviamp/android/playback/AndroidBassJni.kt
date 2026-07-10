package app.naviamp.android.playback

import android.util.Log

object AndroidBassJni {
    private const val Tag = "NaviampBass"

    fun load(): Result<AndroidBassJni> =
        runCatching {
            AndroidBassNativeLoader.loadBundledLibraries().also { report ->
                check(report.available) { "BASS core library is not loaded." }
            }
            System.loadLibrary("naviamp_bass")
            Log.i(
                Tag,
                "Loaded naviamp_bass JNI: bass=${nativeBassVersion()}, " +
                    "bassmix=${nativeMixerVersion()}, error=${nativeLastErrorCode()}",
            )
            this
        }

    val version: Int
        get() = nativeBassVersion()

    val mixerVersion: Int
        get() = nativeMixerVersion()

    val lastErrorCode: Int
        get() = nativeLastErrorCode()

    fun init(): Boolean = nativeInit()
    fun init(sampleRateHz: Int): Boolean = nativeInitAtSampleRate(sampleRateHz)

    fun free() = nativeFree()

    fun setVerifyNet(verify: Boolean): Boolean = nativeSetVerifyNet(verify)

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

    fun play(stream: Int): Boolean = nativePlay(stream)

    fun pause(stream: Int): Boolean = nativePause(stream)

    fun stop(stream: Int): Boolean = nativeStop(stream)

    fun freeStream(stream: Int): Boolean = nativeFreeStream(stream)

    fun activeState(stream: Int): Int = nativeActiveState(stream)

    fun setVolume(stream: Int, volume: Float): Boolean = nativeSetVolume(stream, volume)

    fun slideVolume(stream: Int, volume: Float, millis: Int): Boolean = nativeSlideVolume(stream, volume, millis)

    fun applyEqualizer(stream: Int, bandsDb: FloatArray): Boolean = nativeApplyEqualizer(stream, bandsDb)

    fun seek(stream: Int, seconds: Double): Boolean = nativeSeek(stream, seconds)

    fun positionSeconds(stream: Int): Double? = nativePositionSeconds(stream).takeIf { it >= 0.0 }
    fun setSampleRateConverterQuality(quality: Int): Boolean = nativeSetSampleRateConverterQuality(quality)

    fun audiblePositionSeconds(playbackStream: Int, sourceStream: Int): Double? =
        nativeAudiblePositionSeconds(playbackStream, sourceStream).takeIf { it >= 0.0 }

    fun durationSeconds(stream: Int): Double? = nativeDurationSeconds(stream).takeIf { it > 0.0 }

    fun lengthBytes(stream: Int): Long? = nativeLengthBytes(stream).takeIf { it > 0L }

    fun streamTags(stream: Int): Array<String> = nativeStreamTags(stream)

    fun fft(stream: Int, bins: Int): FloatArray = nativeFft(stream, bins)

    fun waveformLevels(stream: Int, bucketCount: Int): FloatArray = nativeWaveformLevels(stream, bucketCount)

    fun readFloatData(stream: Int, buffer: FloatArray): Int = nativeReadFloatData(stream, buffer)

    private external fun nativeBassVersion(): Int
    private external fun nativeMixerVersion(): Int
    private external fun nativeLastErrorCode(): Int
    private external fun nativeInit(): Boolean
    private external fun nativeInitAtSampleRate(sampleRateHz: Int): Boolean
    private external fun nativeFree()
    private external fun nativeSetVerifyNet(verify: Boolean): Boolean
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
    private external fun nativePlay(stream: Int): Boolean
    private external fun nativePause(stream: Int): Boolean
    private external fun nativeStop(stream: Int): Boolean
    private external fun nativeFreeStream(stream: Int): Boolean
    private external fun nativeActiveState(stream: Int): Int
    private external fun nativeSetVolume(stream: Int, volume: Float): Boolean
    private external fun nativeSlideVolume(stream: Int, volume: Float, millis: Int): Boolean
    private external fun nativeApplyEqualizer(stream: Int, bandsDb: FloatArray): Boolean
    private external fun nativeSeek(stream: Int, seconds: Double): Boolean
    private external fun nativePositionSeconds(stream: Int): Double
    private external fun nativeSetSampleRateConverterQuality(quality: Int): Boolean
    private external fun nativeAudiblePositionSeconds(playbackStream: Int, sourceStream: Int): Double
    private external fun nativeDurationSeconds(stream: Int): Double
    private external fun nativeLengthBytes(stream: Int): Long
    private external fun nativeStreamTags(stream: Int): Array<String>
    private external fun nativeFft(stream: Int, bins: Int): FloatArray
    private external fun nativeWaveformLevels(stream: Int, bucketCount: Int): FloatArray
    private external fun nativeReadFloatData(stream: Int, buffer: FloatArray): Int
}

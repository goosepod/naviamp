package app.naviamp.desktop.playback.bass

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassPluginDiagnostic
import app.naviamp.domain.bass.BassStreamInfo
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.bass.bassFailureMessage

class DesktopBassAudioBackend(
    private val bass: DesktopBassJniBinding,
) : BassAudioBackend {
    override val version: Int
        get() = bass.version

    override val mixerVersion: Int?
        get() = bass.mixerVersion.takeIf { it > 0 }

    override val lastErrorCode: Int
        get() = bass.lastErrorCode

    override val mixerError: String?
        get() = null

    override val libraryDirectory: String
        get() = bass.libraryDirectory.absolutePath

    override val pluginDiagnostics: List<BassPluginDiagnostic> =
        bass.loadAvailablePlugins().map { plugin ->
            BassPluginDiagnostic(
                stem = plugin.stem,
                loaded = plugin.loaded,
                errorCode = plugin.errorCode,
            )
        }

    override val supportsMixer: Boolean
        get() = mixerVersion != null

    override fun init(): Result<Unit> =
        if (bass.init()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_Init failed")))
        }

    override fun free(): Result<Unit> {
        bass.free()
        return Result.success(Unit)
    }

    override fun configureInternetStreams(): Result<Unit> =
        if (bass.configureInternetStreams()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS internet stream config failed")))
        }

    override fun createFileStream(path: String): Result<BassStreamHandle> =
        bass.createFileStream(path)
            .toHandleResult("BASS_StreamCreateFile failed")

    override fun createUrlStream(url: String): Result<BassStreamHandle> =
        bass.createUrlStream(url)
            .toHandleResult("BASS_StreamCreateURL failed")

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> {
        bass.loadAvailablePlugins()
        return bass.createFileDecodeStream(path)
            .toHandleResult("BASS_StreamCreateFile decode failed")
    }

    override fun createFilePlaybackDecodeStream(path: String): Result<BassStreamHandle> {
        bass.loadAvailablePlugins()
        return bass.createFileDecodeStream(path)
            .toHandleResult("BASS_StreamCreateFile playback decode failed")
    }

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> {
        bass.loadAvailablePlugins()
        return bass.createUrlDecodeStream(url)
            .toHandleResult("BASS_StreamCreateURL decode failed")
    }

    override fun channelInfo(stream: BassStreamHandle): Result<BassStreamInfo> {
        val frequency = bass.channelInfoFrequency(stream.value)
        val channels = bass.channelInfoChannels(stream.value)
        return if (frequency > 0 && channels > 0) {
            Result.success(BassStreamInfo(frequency = frequency, channels = channels))
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_ChannelGetInfo failed")))
        }
    }

    override fun createMixer(
        frequency: Int,
        channels: Int,
        queueSources: Boolean,
    ): Result<BassStreamHandle> =
        bass.createMixer(frequency, channels, queueSources)
            .toHandleResult("BASS_Mixer_StreamCreate failed")

    override fun addMixerChannel(
        mixer: BassStreamHandle,
        stream: BassStreamHandle,
    ): Result<Unit> =
        if (bass.addMixerChannel(mixer.value, stream.value)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_Mixer_StreamAddChannel failed")))
        }

    override fun removeMixerChannel(stream: BassStreamHandle): Result<Unit> =
        if (bass.removeMixerChannel(stream.value)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_Mixer_ChannelRemove failed")))
        }

    override fun setEndSync(
        stream: BassStreamHandle,
        callback: (BassStreamHandle) -> Unit,
    ): Result<Int> {
        val sync = bass.setEndSync(stream.value) { channel ->
            callback(BassStreamHandle(channel))
        }
        return sync
            .takeIf { it != 0 }
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException(errorMessage("BASS_ChannelSetSync end failed")))
    }

    override fun play(stream: BassStreamHandle): Result<Unit> =
        if (bass.play(stream.value)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_ChannelPlay failed")))
        }

    override fun pause(stream: BassStreamHandle): Result<Unit> =
        if (bass.pause(stream.value)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_ChannelPause failed")))
        }

    override fun stop(stream: BassStreamHandle): Result<Unit> =
        if (bass.stop(stream.value)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_ChannelStop failed")))
        }

    override fun activeState(stream: BassStreamHandle): Int =
        bass.activeState(stream.value)

    override fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> =
        if (bass.setVolume(stream.value, volume)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_ChannelSetAttribute volume failed")))
        }

    override fun slideVolume(
        stream: BassStreamHandle,
        volume: Float,
        durationMillis: Int,
    ): Result<Unit> =
        if (bass.slideVolume(stream.value, volume, durationMillis)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_ChannelSlideAttribute volume failed")))
        }

    override fun applyEqualizer(stream: BassStreamHandle, bandsDb: List<Float>): Result<Unit> =
        if (bass.applyEqualizer(stream.value, bandsDb.toFloatArray())) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS equalizer failed")))
        }

    override fun seek(stream: BassStreamHandle, seconds: Double): Result<Unit> =
        if (bass.seek(stream.value, seconds)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_ChannelSetPosition failed")))
        }

    override fun positionSeconds(stream: BassStreamHandle): Double? =
        bass.positionSeconds(stream.value)

    override fun durationSeconds(stream: BassStreamHandle): Double? =
        bass.durationSeconds(stream.value)

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

    override fun fft(stream: BassStreamHandle, bins: Int): Result<FloatArray> =
        Result.success(bass.fft(stream.value, bins))

    override fun streamMetadata(stream: BassStreamHandle): Map<String, String> =
        bass.streamTags(stream.value).toStreamProperties()

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        if (bass.freeStream(stream.value)) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage("BASS_StreamFree failed")))
        }

    private fun errorMessage(prefix: String): String =
        bassFailureMessage(prefix)

    private fun Int.toHandleResult(prefix: String): Result<BassStreamHandle> =
        takeIf { it != 0 }
            ?.let { Result.success(BassStreamHandle(it)) }
            ?: Result.failure(IllegalStateException(errorMessage(prefix)))
}

fun loadDesktopBassAudioBackend(): Result<BassAudioBackend> =
    DesktopBassLibraryResolver().resolve()
        ?.let { directory -> DesktopBassJniBinding.loadFrom(directory).map(::DesktopBassAudioBackend) }
        ?: Result.failure(
            IllegalStateException(
                "Could not find BASS. Set naviamp.bass.dir or NAVIAMP_BASS_DIR, " +
                    "or bundle ${BassPlatform.current().libraryName("bass")} under playback/bass/${BassPlatform.current().id}.",
            ),
        )

private fun Array<String>.toStreamProperties(): Map<String, String> =
    buildMap {
        this@toStreamProperties.forEach { tag ->
            val equalsIndex = tag.indexOf('=').takeIf { it > 0 }
            val colonIndex = tag.indexOf(':').takeIf { it > 0 }
            val separator = equalsIndex ?: colonIndex ?: return@forEach
            val key = tag.take(separator).trim().trim('\'', '"')
            val value = tag.drop(separator + 1).trim().trim('\'', '"').icyStreamTitleValue()
            if (key.isNotBlank() && value.isNotBlank()) {
                put(key, value)
            }
        }
    }

private fun String.icyStreamTitleValue(): String {
    val key = "StreamTitle='"
    val start = indexOf(key)
    if (start < 0) return this
    val titleStart = start + key.length
    val titleEnd = indexOf("';", titleStart).takeIf { it >= 0 } ?: indexOf("'", titleStart)
    return if (titleEnd > titleStart) substring(titleStart, titleEnd).trim() else this
}

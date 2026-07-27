@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.playback

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassPlaybackBufferPolicy
import app.naviamp.domain.bass.BassPluginDiagnostic
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.bass.BassStreamInfo
import app.naviamp.domain.bass.bassFailureMessage
import app.naviamp.domain.playback.BassPlaybackEngineRuntime
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.ios.bass.native.BASS_ATTRIB_VOL
import app.naviamp.ios.bass.native.BASS_CHANNELINFO
import app.naviamp.ios.bass.native.BASS_CONFIG_BUFFER
import app.naviamp.ios.bass.native.BASS_CONFIG_NET_BUFFER
import app.naviamp.ios.bass.native.BASS_CONFIG_NET_META
import app.naviamp.ios.bass.native.BASS_CONFIG_NET_PLAYLIST
import app.naviamp.ios.bass.native.BASS_CONFIG_NET_PLAYLIST_DEPTH
import app.naviamp.ios.bass.native.BASS_CONFIG_NET_PREBUF
import app.naviamp.ios.bass.native.BASS_CONFIG_NET_READTIMEOUT
import app.naviamp.ios.bass.native.BASS_CONFIG_NET_TIMEOUT
import app.naviamp.ios.bass.native.BASS_CONFIG_SRC
import app.naviamp.ios.bass.native.BASS_CONFIG_UPDATEPERIOD
import app.naviamp.ios.bass.native.BASS_CONFIG_VERIFY_NET
import app.naviamp.ios.bass.native.BASS_DATA_AVAILABLE
import app.naviamp.ios.bass.native.BASS_DATA_FFT1024
import app.naviamp.ios.bass.native.BASS_DATA_FFT_REMOVEDC
import app.naviamp.ios.bass.native.BASS_DX8_PARAMEQ
import app.naviamp.ios.bass.native.BASS_ERROR_ALREADY
import app.naviamp.ios.bass.native.BASS_ERROR_INIT
import app.naviamp.ios.bass.native.BASS_ErrorGetCode
import app.naviamp.ios.bass.native.BASS_Free
import app.naviamp.ios.bass.native.BASS_FXSetParameters
import app.naviamp.ios.bass.native.BASS_FX_DX8_PARAMEQ
import app.naviamp.ios.bass.native.BASS_GetVersion
import app.naviamp.ios.bass.native.BASS_Init
import app.naviamp.ios.bass.native.BASS_LEVEL_MONO
import app.naviamp.ios.bass.native.BASS_LEVEL_RMS
import app.naviamp.ios.bass.native.BASS_MIXER_CHAN_NORAMPIN
import app.naviamp.ios.bass.native.BASS_MIXER_QUEUE
import app.naviamp.ios.bass.native.BASS_Mixer_ChannelRemove
import app.naviamp.ios.bass.native.BASS_Mixer_ChannelSetPosition
import app.naviamp.ios.bass.native.BASS_Mixer_GetVersion
import app.naviamp.ios.bass.native.BASS_Mixer_StreamAddChannel
import app.naviamp.ios.bass.native.BASS_Mixer_StreamCreate
import app.naviamp.ios.bass.native.BASS_POS_BYTE
import app.naviamp.ios.bass.native.BASS_POS_MIXER_RESET
import app.naviamp.ios.bass.native.BASS_SAMPLE_FLOAT
import app.naviamp.ios.bass.native.BASS_STREAM_DECODE
import app.naviamp.ios.bass.native.BASS_STREAM_PRESCAN
import app.naviamp.ios.bass.native.BASS_STREAM_STATUS
import app.naviamp.ios.bass.native.BASS_ChannelBytes2Seconds
import app.naviamp.ios.bass.native.BASS_ChannelGetData
import app.naviamp.ios.bass.native.BASS_ChannelGetInfo
import app.naviamp.ios.bass.native.BASS_ChannelGetLength
import app.naviamp.ios.bass.native.BASS_ChannelGetLevelEx
import app.naviamp.ios.bass.native.BASS_ChannelGetPosition
import app.naviamp.ios.bass.native.BASS_ChannelIsActive
import app.naviamp.ios.bass.native.BASS_ChannelPause
import app.naviamp.ios.bass.native.BASS_ChannelPlay
import app.naviamp.ios.bass.native.BASS_ChannelRemoveFX
import app.naviamp.ios.bass.native.BASS_ChannelSeconds2Bytes
import app.naviamp.ios.bass.native.BASS_ChannelSetAttribute
import app.naviamp.ios.bass.native.BASS_ChannelSetFX
import app.naviamp.ios.bass.native.BASS_ChannelSetPosition
import app.naviamp.ios.bass.native.BASS_ChannelSetSync
import app.naviamp.ios.bass.native.BASS_ChannelSlideAttribute
import app.naviamp.ios.bass.native.BASS_ChannelStop
import app.naviamp.ios.bass.native.BASS_PluginFree
import app.naviamp.ios.bass.native.BASS_PluginLoad
import app.naviamp.ios.bass.native.BASS_SetConfig
import app.naviamp.ios.bass.native.BASS_StreamCreateFile
import app.naviamp.ios.bass.native.BASS_StreamCreateURL
import app.naviamp.ios.bass.native.BASS_StreamFree
import app.naviamp.ios.bass.native.BASS_SYNC_END
import app.naviamp.ios.bass.native.BASS_SYNC_ONETIME
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredIOBufferDuration
import platform.Foundation.NSLock
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock

/** Direct BASS C ABI adapter. Core owns every playback and feature decision above these calls. */
class IosBassAudioBackend : BassAudioBackend {
    private val equalizerEffects = mutableMapOf<UInt, MutableList<UInt>>()
    private val endSyncs = mutableMapOf<UInt, IosBassEndSync>()
    private val retiredEndSyncs = mutableListOf<IosBassEndSync>()
    private val pluginHandles = mutableMapOf<String, UInt>()
    private val failedPlugins = mutableMapOf<String, Int>()
    private var pluginsAttempted = false
    private val audioSession = AVAudioSession.sharedInstance()

    override val version: Int
        get() = BASS_GetVersion().toInt()

    override val mixerVersion: Int
        get() = BASS_Mixer_GetVersion().toInt()

    override val lastErrorCode: Int
        get() = BASS_ErrorGetCode()

    override val supportsMixer: Boolean = true

    override val pluginDiagnostics: List<BassPluginDiagnostic>
        get() {
            ensureCodecPluginsLoaded()
            return IosBassFrameworks.map { framework ->
                when (framework) {
                    in IosBassCodecPlugins -> BassPluginDiagnostic(
                        stem = framework,
                        loaded = pluginHandles[framework] != null,
                        errorCode = failedPlugins[framework],
                    )
                    else -> BassPluginDiagnostic(stem = framework, loaded = true)
                }
            }
        }

    override fun configurePlaybackBuffers(policy: BassPlaybackBufferPolicy): Result<Unit> {
        val settings = listOf(
            "BASS_CONFIG_UPDATEPERIOD" to {
                BASS_SetConfig(
                    BASS_CONFIG_UPDATEPERIOD.toUInt(),
                    policy.updatePeriodMillis.coerceIn(5, 100).toUInt(),
                )
            },
            "BASS_CONFIG_BUFFER" to {
                BASS_SetConfig(BASS_CONFIG_BUFFER.toUInt(), max(1, policy.playbackBufferMillis).toUInt())
            },
        )
        settings.forEach { (name, apply) ->
            if (apply() == 0) return failure("$name failed")
        }
        if (!audioSession.setPreferredIOBufferDuration(
                duration = max(1, policy.deviceBufferMillis) / 1_000.0,
                error = null,
            )
        ) {
            return Result.failure(IllegalStateException("AVAudioSession preferred I/O buffer configuration failed"))
        }
        return Result.success(Unit)
    }

    override fun init(): Result<Unit> = init(deviceId = null, sampleRateHz = 44_100)

    override fun init(deviceId: String?, sampleRateHz: Int): Result<Unit> {
        if (!audioSession.setCategory(
                category = AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeDefault,
                options = 0uL,
                error = null,
            ) || !audioSession.setActive(true, error = null)
        ) {
            return Result.failure(IllegalStateException("iOS playback audio session activation failed"))
        }

        return bassResult("BASS_Init at $sampleRateHz Hz failed") {
            BASS_Init(-1, sampleRateHz.coerceIn(8_000, 768_000).toUInt(), 0u, null, null) != 0 ||
                BASS_ErrorGetCode() == BASS_ERROR_ALREADY
        }.onSuccess { ensureCodecPluginsLoaded() }
            .onFailure { deactivateAudioSession() }
    }

    override fun free(): Result<Unit> {
        pluginHandles.values.forEach { handle -> BASS_PluginFree(handle) }
        pluginHandles.clear()
        failedPlugins.clear()
        pluginsAttempted = false
        val bassStopped = BASS_Free() != 0 || BASS_ErrorGetCode() == BASS_ERROR_INIT
        val bassResult = if (bassStopped) {
            Result.success(Unit)
        } else {
            failure("BASS_Free failed")
        }
        // BASS may deliver a sync callback from its update thread while a stream is being
        // released. Keep every StableRef alive until BASS_Free has stopped those native
        // threads; disposing it earlier leaves an authenticated pointer that the callback can
        // still observe and causes an EXC_BAD_ACCESS.
        if (bassStopped) {
            endSyncs.values.forEach(IosBassEndSync::dispose)
            endSyncs.clear()
            retiredEndSyncs.forEach(IosBassEndSync::dispose)
            retiredEndSyncs.clear()
        }
        val sessionResult = if (deactivateAudioSession()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("iOS playback audio session deactivation failed"))
        }
        return bassResult.fold(onSuccess = { sessionResult }, onFailure = { bassResult })
    }

    override fun setVerifyNet(verify: Boolean): Result<Unit> =
        bassResult("BASS_SetConfig VERIFY_NET failed") {
            BASS_SetConfig(BASS_CONFIG_VERIFY_NET.toUInt(), if (verify) 1u else 0u) != 0
        }

    override fun configureInternetStreams(): Result<Unit> {
        val settings = listOf(
            Triple("BASS_CONFIG_NET_PLAYLIST", BASS_CONFIG_NET_PLAYLIST, 1u),
            Triple("BASS_CONFIG_NET_META", BASS_CONFIG_NET_META, 1u),
            Triple("BASS_CONFIG_NET_PLAYLIST_DEPTH", BASS_CONFIG_NET_PLAYLIST_DEPTH, 5u),
            Triple("BASS_CONFIG_NET_BUFFER", BASS_CONFIG_NET_BUFFER, 5_000u),
            Triple("BASS_CONFIG_NET_PREBUF", BASS_CONFIG_NET_PREBUF, 75u),
            Triple("BASS_CONFIG_NET_TIMEOUT", BASS_CONFIG_NET_TIMEOUT, 15_000u),
            Triple("BASS_CONFIG_NET_READTIMEOUT", BASS_CONFIG_NET_READTIMEOUT, 15_000u),
        )
        settings.forEach { (name, option, value) ->
            if (BASS_SetConfig(option.toUInt(), value) == 0) return failure("$name failed")
        }
        return Result.success(Unit)
    }

    override fun setSampleRateConverterQuality(quality: Int): Result<Unit> =
        bassResult("BASS_CONFIG_SRC failed") {
            BASS_SetConfig(BASS_CONFIG_SRC.toUInt(), quality.coerceIn(0, 4).toUInt()) != 0
        }

    override fun createFileStream(path: String): Result<BassStreamHandle> =
        createFile(path, (BASS_STREAM_PRESCAN or BASS_SAMPLE_FLOAT).toUInt(), "BASS_StreamCreateFile failed")

    override fun createUrlStream(url: String): Result<BassStreamHandle> =
        createUrl(url, (BASS_STREAM_STATUS or BASS_SAMPLE_FLOAT).toUInt(), "BASS_StreamCreateURL failed")

    override fun createFileDecodeStream(path: String): Result<BassStreamHandle> =
        createFile(
            path,
            (BASS_STREAM_PRESCAN or BASS_SAMPLE_FLOAT or BASS_STREAM_DECODE).toUInt(),
            "BASS_StreamCreateFile decode failed",
        )

    override fun createUrlDecodeStream(url: String): Result<BassStreamHandle> =
        createUrl(
            url,
            (BASS_STREAM_STATUS or BASS_SAMPLE_FLOAT or BASS_STREAM_DECODE).toUInt(),
            "BASS_StreamCreateURL decode failed",
        )

    override fun channelInfo(stream: BassStreamHandle): Result<BassStreamInfo> = memScoped {
        val info = alloc<BASS_CHANNELINFO>()
        if (BASS_ChannelGetInfo(stream.uint, info.ptr) != 0) {
            Result.success(BassStreamInfo(frequency = info.freq.toInt(), channels = info.chans.toInt()))
        } else {
            failure("BASS_ChannelGetInfo failed")
        }
    }

    override fun createMixer(frequency: Int, channels: Int, queueSources: Boolean): Result<BassStreamHandle> {
        val flags = (BASS_SAMPLE_FLOAT or if (queueSources) BASS_MIXER_QUEUE else 0).toUInt()
        return BASS_Mixer_StreamCreate(max(1, frequency).toUInt(), max(1, channels).toUInt(), flags)
            .handleResult("BASS_Mixer_StreamCreate failed")
    }

    override fun addMixerChannel(mixer: BassStreamHandle, stream: BassStreamHandle): Result<Unit> =
        bassResult("BASS_Mixer_StreamAddChannel failed") {
            BASS_Mixer_StreamAddChannel(mixer.uint, stream.uint, BASS_MIXER_CHAN_NORAMPIN.toUInt()) != 0
        }

    override fun removeMixerChannel(stream: BassStreamHandle): Result<Unit> =
        bassResult("BASS_Mixer_ChannelRemove failed") { BASS_Mixer_ChannelRemove(stream.uint) != 0 }

    override fun setEndSync(
        stream: BassStreamHandle,
        callback: (BassStreamHandle) -> Unit,
    ): Result<Int> {
        endSyncs.remove(stream.uint)?.let(retiredEndSyncs::add)
        val sync = IosBassEndSync(callback)
        val handle = BASS_ChannelSetSync(
            stream.uint,
            BASS_SYNC_END.toUInt() or BASS_SYNC_ONETIME,
            0uL,
            IosBassEndSyncProc,
            sync.pointer,
        )
        if (handle == 0u) {
            sync.dispose()
            return failure("BASS_ChannelSetSync failed")
        }
        endSyncs[stream.uint] = sync
        return Result.success(handle.toInt())
    }

    override fun play(stream: BassStreamHandle): Result<Unit> =
        bassResult("BASS_ChannelPlay failed") { BASS_ChannelPlay(stream.uint, 0) != 0 }

    override fun pause(stream: BassStreamHandle): Result<Unit> =
        bassResult("BASS_ChannelPause failed") { BASS_ChannelPause(stream.uint) != 0 }

    override fun stop(stream: BassStreamHandle): Result<Unit> =
        bassResult("BASS_ChannelStop failed") { BASS_ChannelStop(stream.uint) != 0 }

    override fun activeState(stream: BassStreamHandle): Int = BASS_ChannelIsActive(stream.uint).toInt()

    override fun setVolume(stream: BassStreamHandle, volume: Float): Result<Unit> =
        bassResult("BASS_ChannelSetAttribute volume failed") {
            BASS_ChannelSetAttribute(stream.uint, BASS_ATTRIB_VOL.toUInt(), volume.coerceIn(0f, 4f)) != 0
        }

    override fun slideVolume(stream: BassStreamHandle, volume: Float, durationMillis: Int): Result<Unit> =
        bassResult("BASS_ChannelSlideAttribute volume failed") {
            BASS_ChannelSlideAttribute(
                stream.uint,
                BASS_ATTRIB_VOL.toUInt(),
                volume.coerceIn(0f, 4f),
                max(0, durationMillis).toUInt(),
            ) != 0
        }

    override fun applyEqualizer(stream: BassStreamHandle, bandsDb: List<Float>): Result<Unit> {
        clearEqualizer(stream.uint)
        val effects = mutableListOf<UInt>()
        bandsDb.take(EqualizerFrequencies.size).forEachIndexed { index, requestedGain ->
            val gain = requestedGain.coerceIn(-15f, 15f)
            if (kotlin.math.abs(gain) < 0.05f) return@forEachIndexed
            val effect = BASS_ChannelSetFX(stream.uint, BASS_FX_DX8_PARAMEQ.toUInt(), 0)
            if (effect == 0u) {
                effects.forEach { BASS_ChannelRemoveFX(stream.uint, it) }
                return failure("BASS_ChannelSetFX equalizer failed")
            }
            val configured = memScoped {
                val parameters = alloc<BASS_DX8_PARAMEQ>()
                parameters.fCenter = EqualizerFrequencies[index]
                parameters.fBandwidth = 18f
                parameters.fGain = gain
                BASS_FXSetParameters(effect, parameters.ptr) != 0
            }
            if (!configured) {
                BASS_ChannelRemoveFX(stream.uint, effect)
                effects.forEach { BASS_ChannelRemoveFX(stream.uint, it) }
                return failure("BASS_FXSetParameters equalizer failed")
            }
            effects += effect
        }
        if (effects.isNotEmpty()) equalizerEffects[stream.uint] = effects
        return Result.success(Unit)
    }

    override fun seek(stream: BassStreamHandle, seconds: Double): Result<Unit> {
        val bytes = BASS_ChannelSeconds2Bytes(stream.uint, seconds)
        if (bytes == ULong.MAX_VALUE) return failure("BASS_ChannelSeconds2Bytes failed")
        return bassResult("BASS_ChannelSetPosition failed") {
            BASS_Mixer_ChannelSetPosition(
                stream.uint,
                bytes,
                (BASS_POS_BYTE or BASS_POS_MIXER_RESET).toUInt(),
            ) != 0 || BASS_ChannelSetPosition(stream.uint, bytes, BASS_POS_BYTE.toUInt()) != 0
        }
    }

    override fun positionSeconds(stream: BassStreamHandle): Double? =
        BASS_ChannelGetPosition(stream.uint, BASS_POS_BYTE.toUInt())
            .takeUnless { it == ULong.MAX_VALUE }
            ?.let { BASS_ChannelBytes2Seconds(stream.uint, it) }
            ?.takeIf { it >= 0.0 }

    override fun audiblePositionSeconds(playbackStream: BassStreamHandle, sourceStream: BassStreamHandle): Double? {
        val progressStream = sourceStream.takeIf { it.value != 0 } ?: playbackStream
        val decoded = positionSeconds(progressStream) ?: return null
        val bufferedBytes = BASS_ChannelGetData(playbackStream.uint, null, BASS_DATA_AVAILABLE.toUInt())
        if (bufferedBytes == UInt.MAX_VALUE) return decoded
        val buffered = BASS_ChannelBytes2Seconds(playbackStream.uint, bufferedBytes.toULong())
        return if (buffered >= 0.0) max(0.0, decoded - buffered) else decoded
    }

    override fun durationSeconds(stream: BassStreamHandle): Double? =
        lengthBytes(stream)?.let { BASS_ChannelBytes2Seconds(stream.uint, it.toULong()) }?.takeIf { it >= 0.0 }

    override fun lengthBytes(stream: BassStreamHandle): Long? =
        BASS_ChannelGetLength(stream.uint, BASS_POS_BYTE.toUInt())
            .takeUnless { it == ULong.MAX_VALUE }
            ?.toLong()

    override fun readFloatData(stream: BassStreamHandle, buffer: FloatArray): Result<Int> {
        if (buffer.isEmpty()) return Result.success(0)
        val read = buffer.usePinned { pinned ->
            BASS_ChannelGetData(stream.uint, pinned.addressOf(0), (buffer.size * Float.SIZE_BYTES).toUInt())
        }
        return if (read != UInt.MAX_VALUE) {
            Result.success(read.toInt() / Float.SIZE_BYTES)
        } else {
            failure("BASS_ChannelGetData failed")
        }
    }

    override fun fft(stream: BassStreamHandle, bins: Int): Result<FloatArray> {
        val output = FloatArray(512)
        val read = output.usePinned { pinned ->
            BASS_ChannelGetData(
                stream.uint,
                pinned.addressOf(0),
                BASS_DATA_FFT1024 or BASS_DATA_FFT_REMOVEDC.toUInt(),
            )
        }
        return if (read != UInt.MAX_VALUE) {
            Result.success(output.copyOf(bins.coerceIn(1, 256)))
        } else {
            failure("BASS FFT failed")
        }
    }

    override fun waveformLevels(stream: BassStreamHandle, bucketCount: Int): Result<FloatArray> {
        val duration = durationSeconds(stream) ?: return failure("BASS waveform duration failed")
        val count = bucketCount.coerceIn(1, 4_096)
        val bucketSeconds = duration / count
        val output = FloatArray(count)
        BASS_ChannelSetPosition(stream.uint, 0uL, BASS_POS_BYTE.toUInt())
        for (bucket in 0 until count) {
            var remaining = bucketSeconds
            var squareSum = 0.0
            var measured = 0.0
            while (remaining > 0.0001) {
                val window = min(remaining, 1.0).toFloat()
                val level = FloatArray(1)
                val success = level.usePinned { pinned ->
                    BASS_ChannelGetLevelEx(
                        stream.uint,
                        pinned.addressOf(0),
                        window,
                        (BASS_LEVEL_MONO or BASS_LEVEL_RMS).toUInt(),
                    )
                }
                if (success == 0) return failure("BASS_ChannelGetLevelEx failed")
                val sample = level[0].coerceIn(0f, 1f)
                squareSum += sample * sample * window
                measured += window
                remaining -= window
            }
            output[bucket] = if (measured > 0.0) kotlin.math.sqrt(squareSum / measured).toFloat() else 0f
        }
        return Result.success(output)
    }

    override fun freeStream(stream: BassStreamHandle): Result<Unit> =
        bassResult("BASS_StreamFree failed") {
            endSyncs.remove(stream.uint)?.let(retiredEndSyncs::add)
            clearEqualizer(stream.uint)
            BASS_StreamFree(stream.uint) != 0
        }

    private fun clearEqualizer(stream: UInt) {
        equalizerEffects.remove(stream)?.forEach { effect -> BASS_ChannelRemoveFX(stream, effect) }
    }

    private fun deactivateAudioSession(): Boolean = audioSession.setActive(
        active = false,
        withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
        error = null,
    )

    private fun ensureCodecPluginsLoaded() {
        if (pluginsAttempted) return
        pluginsAttempted = true
        val frameworksDirectory = NSBundle.mainBundle.privateFrameworksPath ?: return
        IosBassCodecPlugins.forEach { framework ->
            val path = "$frameworksDirectory/$framework.framework/$framework"
            val handle = BASS_PluginLoad(path, 0u)
            if (handle != 0u) {
                pluginHandles[framework] = handle
            } else {
                failedPlugins[framework] = BASS_ErrorGetCode()
            }
        }
    }

    private fun createFile(path: String, flags: UInt, message: String): Result<BassStreamHandle> = memScoped {
        BASS_StreamCreateFile(0u, path.cstr.ptr, 0uL, 0uL, flags).handleResult(message)
    }

    private fun createUrl(url: String, flags: UInt, message: String): Result<BassStreamHandle> = memScoped {
        BASS_StreamCreateURL(url, 0u, flags, null, null).handleResult(message)
    }

    private inline fun bassResult(message: String, block: () -> Boolean): Result<Unit> =
        if (block()) Result.success(Unit) else failure(message)

    private fun UInt.handleResult(message: String): Result<BassStreamHandle> =
        if (this != 0u) Result.success(BassStreamHandle(toInt())) else failure(message)

    private fun <T> failure(message: String): Result<T> {
        val detail = bassFailureMessage(message)
        NSLog("Naviamp BASS: $detail")
        return Result.failure(IllegalStateException(detail))
    }

    private val BassStreamHandle.uint: UInt
        get() = value.toUInt()
}

class IosBassPlaybackEngineRuntime : BassPlaybackEngineRuntime {
    private val preparedLock = NSLock()

    override val workContext = Dispatchers.Default

    override fun localFilePath(url: String): String? = when {
        url.startsWith("file:") -> NSURL.URLWithString(url)?.path
        url.startsWith("/") -> url
        else -> null
    }

    override fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

    override fun <T> withPreparedPlaybackLock(block: () -> T): T {
        preparedLock.lock()
        return try {
            block()
        } finally {
            preparedLock.unlock()
        }
    }
}

fun createIosBassPlaybackEngine(): CoreBassPlaybackEngine = CoreBassPlaybackEngine(
    backendResult = Result.success(IosBassAudioBackend()),
    runtime = IosBassPlaybackEngineRuntime(),
)

private val IosBassFrameworks = listOf(
    "bass",
    "bassmix",
    "bassflac",
    "bassopus",
    "bassmidi",
    "basswv",
    "bassdsd",
    "basswebm",
    "basshls",
    "bassape",
    "bassloud",
    "bass_fx",
    "bass_mpc",
    "bass_tta",
)

private val IosBassCodecPlugins = setOf(
    "bassflac",
    "bassopus",
    "bassmidi",
    "basswv",
    "bassdsd",
    "basswebm",
    "basshls",
    "bassape",
    "bass_mpc",
    "bass_tta",
)

private class IosBassEndSync(callback: (BassStreamHandle) -> Unit) {
    private val lock = NSLock()
    private var callback: ((BassStreamHandle) -> Unit)? = callback
    private var reference: StableRef<IosBassEndSync>? = StableRef.create(this)

    val pointer: COpaquePointer?
        get() = reference?.asCPointer()

    fun fire(channel: UInt) {
        val action = locked { callback.also { callback = null } }
        action?.invoke(BassStreamHandle(channel.toInt()))
    }

    fun dispose() {
        val ownedReference = locked {
            callback = null
            reference.also { reference = null }
        }
        ownedReference?.dispose()
    }

    private fun <T> locked(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}

private val IosBassEndSyncProc = staticCFunction {
        _: UInt,
        channel: UInt,
        _: UInt,
        user: COpaquePointer?,
    ->
    if (user != null) {
        user.asStableRef<IosBassEndSync>().get().fire(channel)
    }
}

private val EqualizerFrequencies = floatArrayOf(
    31f,
    62f,
    125f,
    250f,
    500f,
    1_000f,
    2_000f,
    4_000f,
    8_000f,
    16_000f,
)

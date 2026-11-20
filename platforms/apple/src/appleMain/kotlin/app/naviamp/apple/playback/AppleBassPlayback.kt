@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package app.naviamp.apple.playback

import app.naviamp.domain.bass.bassPluginLoadDiagnostic
import app.naviamp.domain.bass.BassFilePosition
import app.naviamp.domain.bass.BassInternetStreamPolicy
import app.naviamp.apple.bass.native.BASS_StreamGetFilePosition
import app.naviamp.domain.bass.bassStreamProperties
import app.naviamp.apple.bass.native.BASS_ChannelGetTags
import app.naviamp.apple.bass.native.BASS_TAG_ICY
import app.naviamp.apple.bass.native.BASS_TAG_HTTP
import app.naviamp.apple.bass.native.BASS_TAG_OGG
import app.naviamp.apple.bass.native.BASS_TAG_APE
import app.naviamp.apple.bass.native.BASS_TAG_MP4
import app.naviamp.apple.bass.native.BASS_TAG_META
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassPlaybackBufferPolicy
import app.naviamp.domain.bass.BassPluginDiagnostic
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.bass.BassStreamInfo
import app.naviamp.domain.playback.planEqualizer
import app.naviamp.domain.bass.bassFailureMessage
import app.naviamp.domain.playback.BassPlaybackEngineRuntime
import app.naviamp.apple.bass.native.BASS_ATTRIB_VOL
import app.naviamp.apple.bass.native.BASS_CHANNELINFO
import app.naviamp.apple.bass.native.BASS_CONFIG_BUFFER
import app.naviamp.apple.bass.native.BASS_CONFIG_NET_BUFFER
import app.naviamp.apple.bass.native.BASS_CONFIG_NET_META
import app.naviamp.apple.bass.native.BASS_CONFIG_NET_PLAYLIST
import app.naviamp.apple.bass.native.BASS_CONFIG_NET_PLAYLIST_DEPTH
import app.naviamp.apple.bass.native.BASS_CONFIG_NET_PREBUF
import app.naviamp.apple.bass.native.BASS_CONFIG_NET_READTIMEOUT
import app.naviamp.apple.bass.native.BASS_CONFIG_NET_TIMEOUT
import app.naviamp.apple.bass.native.BASS_CONFIG_SRC
import app.naviamp.apple.bass.native.BASS_CONFIG_UPDATEPERIOD
import app.naviamp.apple.bass.native.BASS_CONFIG_VERIFY_NET
import app.naviamp.apple.bass.native.BASS_DATA_AVAILABLE
import app.naviamp.apple.bass.native.BASS_DATA_FFT1024
import app.naviamp.apple.bass.native.BASS_DATA_FFT_REMOVEDC
import app.naviamp.apple.bass.native.BASS_DX8_PARAMEQ
import app.naviamp.apple.bass.native.BASS_ERROR_ALREADY
import app.naviamp.apple.bass.native.BASS_ERROR_INIT
import app.naviamp.apple.bass.native.BASS_ErrorGetCode
import app.naviamp.apple.bass.native.BASS_Free
import app.naviamp.apple.bass.native.BASS_FXSetParameters
import app.naviamp.apple.bass.native.BASS_FX_DX8_PARAMEQ
import app.naviamp.apple.bass.native.BASS_GetVersion
import app.naviamp.apple.bass.native.BASS_GetDevice
import app.naviamp.apple.bass.native.BASS_Init
import app.naviamp.apple.bass.native.BASS_INFO
import app.naviamp.apple.bass.native.BASS_GetInfo
import app.naviamp.apple.bass.native.BASS_ChannelGetDevice
import app.naviamp.apple.bass.native.BASS_SetDevice
import app.naviamp.apple.bass.native.BASS_DEVICE_REINIT
import app.naviamp.apple.bass.native.BASS_LEVEL_MONO
import app.naviamp.apple.bass.native.BASS_LEVEL_RMS
import app.naviamp.apple.bass.native.BASS_POS_BYTE
import app.naviamp.apple.bass.native.BASS_SAMPLE_FLOAT
import app.naviamp.apple.bass.native.BASS_STREAM_BLOCK
import app.naviamp.apple.bass.native.BASS_STREAM_DECODE
import app.naviamp.apple.bass.native.BASS_STREAM_PRESCAN
import app.naviamp.apple.bass.native.BASS_STREAM_STATUS
import app.naviamp.apple.bass.native.BASS_ChannelBytes2Seconds
import app.naviamp.apple.bass.native.BASS_ChannelGetData
import app.naviamp.apple.bass.native.BASS_ChannelGetInfo
import app.naviamp.apple.bass.native.BASS_ChannelGetLength
import app.naviamp.apple.bass.native.BASS_ChannelGetLevelEx
import app.naviamp.apple.bass.native.BASS_ChannelGetPosition
import app.naviamp.apple.bass.native.BASS_ChannelIsActive
import app.naviamp.apple.bass.native.BASS_ChannelPause
import app.naviamp.apple.bass.native.BASS_ChannelPlay
import app.naviamp.apple.bass.native.BASS_ChannelRemoveFX
import app.naviamp.apple.bass.native.BASS_ChannelSeconds2Bytes
import app.naviamp.apple.bass.native.BASS_ChannelSetAttribute
import app.naviamp.apple.bass.native.BASS_ChannelSetFX
import app.naviamp.apple.bass.native.BASS_ChannelSetPosition
import app.naviamp.apple.bass.native.BASS_ChannelSetSync
import app.naviamp.apple.bass.native.BASS_ChannelSlideAttribute
import app.naviamp.apple.bass.native.BASS_ChannelStop
import app.naviamp.apple.bass.native.BASS_PluginFree
import app.naviamp.apple.bass.native.BASS_PluginLoad
import app.naviamp.apple.bass.native.BASS_SetConfig
import app.naviamp.apple.bass.native.BASS_CONFIG_IOS_SESSION
import app.naviamp.apple.bass.native.BASS_IOS_SESSION_DISABLE
import app.naviamp.apple.bass.native.BASS_StreamCreateFile
import app.naviamp.apple.bass.native.BASS_StreamCreateFileUser
import app.naviamp.apple.bass.native.BASS_FILEPROCS
import app.naviamp.apple.bass.native.BASS_ASYNCFILE
import app.naviamp.domain.playback.BassFileCallbacks
import app.naviamp.domain.playback.PlaybackRandomAccessSource
import kotlin.concurrent.atomics.AtomicBoolean
import platform.posix.memcpy
import app.naviamp.apple.bass.native.BASS_StreamCreateURL
import app.naviamp.apple.bass.native.BASS_StreamFree
import app.naviamp.apple.bass.native.BASS_SYNC_END
import app.naviamp.apple.bass.native.BASS_SYNC_ONETIME
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.plus
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
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

// Playback and waveform adapters use the same native default device. BASS can report
// BASS_ERROR_ALREADY while another thread is still inside BASS_Init without a usable device.
private val appleBassInitializationLock = NSLock()

/** Direct BASS C ABI adapter. Core owns every playback and feature decision above these calls. */
open class AppleBassAudioBackend(
    private val bundledFrameworks: List<String>,
    private val codecPlugins: Set<String>,
) : BassAudioBackend {
    private val equalizerEffects = mutableMapOf<UInt, MutableList<UInt>>()
    private val endSyncs = mutableMapOf<UInt, AppleBassEndSync>()
    private val retiredEndSyncs = mutableListOf<AppleBassEndSync>()
    private val pluginHandles = mutableMapOf<String, UInt>()
    private val failedPlugins = mutableMapOf<String, Int>()
    private var pluginsAttempted = false
    private val audioSession = AVAudioSession.sharedInstance()

    override val version: Int
        get() = BASS_GetVersion().toInt()

    override val lastErrorCode: Int
        get() = BASS_ErrorGetCode()

    override fun reinitializeOutput(stream: BassStreamHandle): Result<Unit> = bassResult("BASS_Init failed") {
        memScoped {
            val device = BASS_ChannelGetDevice(stream.uint)
            if (device == UInt.MAX_VALUE || BASS_SetDevice(device) == 0) return@memScoped false
            val info = alloc<BASS_INFO>()
            if (BASS_GetInfo(info.ptr) == 0) return@memScoped false
            BASS_Init(device.toInt(), info.freq, info.initflags or BASS_DEVICE_REINIT.toUInt(), null, null) != 0
        }
    }

    override fun bufferedOutputSeconds(stream: BassStreamHandle): Double? {
        val bytes = BASS_ChannelGetData(stream.uint, null, BASS_DATA_AVAILABLE.toUInt())
        if (bytes == UInt.MAX_VALUE) return null
        return BASS_ChannelBytes2Seconds(stream.uint, bytes.toULong()).takeIf { it.isFinite() && it >= 0.0 }
    }

    override val pluginDiagnostics: List<BassPluginDiagnostic>
        get() {
            ensureCodecPluginsLoaded()
            return bundledFrameworks.map { framework ->
                when (framework) {
                    in codecPlugins -> bassPluginLoadDiagnostic(
                        framework, pluginHandles[framework] != null, failedPlugins[framework],
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
        appleBassInitializationLock.lock()
        return try {
            initializeOutput(sampleRateHz)
        } finally {
            appleBassInitializationLock.unlock()
        }
    }

    private fun initializeOutput(sampleRateHz: Int): Result<Unit> {
        // BASS otherwise overwrites this AVAudioSession configuration with its default
        // MixWithOthers option, making music playback ineligible for system Now Playing.
        // BASS still handles activation; category/options and buffer duration use Apple APIs.
        if (BASS_SetConfig(BASS_CONFIG_IOS_SESSION.toUInt(), BASS_IOS_SESSION_DISABLE.toUInt()) == 0) {
            return failure("Apple playback audio session activation failed")
        }
        if (!audioSession.setCategory(
                category = AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeDefault,
                options = 0uL,
                error = null,
            ) || !audioSession.setActive(true, error = null)
        ) {
            return Result.failure(IllegalStateException("Apple playback audio session activation failed"))
        }

        return bassResult("BASS_Init at $sampleRateHz Hz failed") {
            BASS_Init(-1, sampleRateHz.coerceIn(8_000, 768_000).toUInt(), 0u, null, null) != 0 ||
                (BASS_ErrorGetCode() == BASS_ERROR_ALREADY && BASS_GetDevice() != UInt.MAX_VALUE)
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
            endSyncs.values.forEach(AppleBassEndSync::dispose)
            endSyncs.clear()
            retiredEndSyncs.forEach(AppleBassEndSync::dispose)
            retiredEndSyncs.clear()
        }
        val sessionResult = if (deactivateAudioSession()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Apple playback audio session deactivation failed"))
        }
        return bassResult.fold(onSuccess = { sessionResult }, onFailure = { bassResult })
    }

    override fun setVerifyNet(verify: Boolean): Result<Unit> =
        bassResult("BASS_SetConfig VERIFY_NET failed") {
            BASS_SetConfig(BASS_CONFIG_VERIFY_NET.toUInt(), if (verify) 1u else 0u) != 0
        }

    override fun configureInternetStreams(policy: BassInternetStreamPolicy): Result<Unit> {
        val settings = listOf(
            Triple("BASS_CONFIG_NET_PLAYLIST", BASS_CONFIG_NET_PLAYLIST, if (policy.playlistsEnabled) 1u else 0u),
            Triple("BASS_CONFIG_NET_META", BASS_CONFIG_NET_META, if (policy.metadataEnabled) 1u else 0u),
            Triple("BASS_CONFIG_NET_PLAYLIST_DEPTH", BASS_CONFIG_NET_PLAYLIST_DEPTH, policy.playlistDepth.toUInt()),
            Triple("BASS_CONFIG_NET_BUFFER", BASS_CONFIG_NET_BUFFER, policy.bufferMillis.toUInt()),
            Triple("BASS_CONFIG_NET_PREBUF", BASS_CONFIG_NET_PREBUF, policy.prebufferPercent.toUInt()),
            Triple("BASS_CONFIG_NET_TIMEOUT", BASS_CONFIG_NET_TIMEOUT, policy.connectionTimeoutMillis.toUInt()),
            Triple("BASS_CONFIG_NET_READTIMEOUT", BASS_CONFIG_NET_READTIMEOUT, policy.readTimeoutMillis.toUInt()),
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

    override fun createRandomAccessStream(source: PlaybackRandomAccessSource, decode: Boolean): Result<BassStreamHandle> {
        ensureCodecPluginsLoaded()
        val state = AppleBassFileRegistration(BassFileCallbacks(source))
        val reference = StableRef.create(state)
        return memScoped {
            val procs = alloc<BASS_FILEPROCS> {
                close = AppleBassFileClose
                length = AppleBassFileLength
                read = AppleBassFileRead
                seek = AppleBassFileSeek
            }
            val flags = BASS_SAMPLE_FLOAT or BASS_ASYNCFILE or if (decode) BASS_STREAM_DECODE else 0
            val stream = BASS_StreamCreateFileUser(0u, flags.toUInt(), procs.ptr, reference.asCPointer())
            if (stream == 0u && state.closed.compareAndSet(false, true)) {
                state.callbacks.close()
                reference.dispose()
            }
            stream.handleResult("BASS_StreamCreateFile failed")
        }
    }

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

    override fun createBoundedUrlDecodeStream(url: String): Result<BassStreamHandle> =
        createUrl(
            url,
            (BASS_STREAM_STATUS or BASS_SAMPLE_FLOAT or BASS_STREAM_DECODE or BASS_STREAM_BLOCK).toUInt(),
            "BASS_StreamCreateURL bounded decode failed",
        )

    override fun channelInfo(stream: BassStreamHandle): Result<BassStreamInfo> = memScoped {
        val info = alloc<BASS_CHANNELINFO>()
        if (BASS_ChannelGetInfo(stream.uint, info.ptr) != 0) {
            Result.success(BassStreamInfo(frequency = info.freq.toInt(), channels = info.chans.toInt()))
        } else {
            failure("BASS_ChannelGetInfo failed")
        }
    }

    override fun setEndSync(
        stream: BassStreamHandle,
        callback: (BassStreamHandle) -> Unit,
    ): Result<Int> {
        endSyncs.remove(stream.uint)?.let(retiredEndSyncs::add)
        val sync = AppleBassEndSync(callback)
        val handle = BASS_ChannelSetSync(
            stream.uint,
            BASS_SYNC_END.toUInt() or BASS_SYNC_ONETIME,
            0uL,
            AppleBassEndSyncProc,
            sync.pointer,
        )
        if (handle == 0u) {
            sync.dispose()
            return failure("BASS_ChannelSetSync failed")
        }
        endSyncs[stream.uint] = sync
        return Result.success(handle.toInt())
    }

    override fun play(stream: BassStreamHandle): Result<Unit> {
        // Core has chosen to play. OS 27 recommendations are independent of the legacy end event
        // BASS observes, so activation cannot depend on that deprecated notification arriving.
        if (!audioSession.setActive(true, error = null)) return failure("Apple playback audio session activation failed")
        return bassResult("BASS_ChannelPlay failed") { BASS_ChannelPlay(stream.uint, 0) != 0 }
    }

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
        val info = channelInfo(stream).getOrElse { return Result.failure(it) }
        val plan = planEqualizer(bandsDb, info.frequency)
        clearEqualizer(stream.uint)
        val effects = mutableListOf<UInt>()
        plan.forEach { band ->
            val effect = BASS_ChannelSetFX(stream.uint, BASS_FX_DX8_PARAMEQ.toUInt(), 0)
            if (effect == 0u) {
                effects.forEach { BASS_ChannelRemoveFX(stream.uint, it) }
                return failure("BASS_ChannelSetFX equalizer failed")
            }
            val configured = memScoped {
                val parameters = alloc<BASS_DX8_PARAMEQ>()
                parameters.fCenter = band.frequencyHz
                parameters.fBandwidth = band.bandwidthOctaves * 12f // DX8 ABI uses semitones.
                parameters.fGain = band.gainDb
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
            BASS_ChannelSetPosition(stream.uint, bytes, BASS_POS_BYTE.toUInt()) != 0
        }
    }

    override fun positionSeconds(stream: BassStreamHandle): Double? =
        BASS_ChannelGetPosition(stream.uint, BASS_POS_BYTE.toUInt())
            .takeUnless { it == ULong.MAX_VALUE }
            ?.let { BASS_ChannelBytes2Seconds(stream.uint, it) }
            ?.takeIf { it >= 0.0 }

    override fun durationSeconds(stream: BassStreamHandle): Double? =
        lengthBytes(stream)?.let { BASS_ChannelBytes2Seconds(stream.uint, it.toULong()) }?.takeIf { it >= 0.0 }

    override fun filePosition(stream: BassStreamHandle, position: BassFilePosition): Long? =
        BASS_StreamGetFilePosition(stream.uint, position.nativeValue.toUInt())
            .takeUnless { it == ULong.MAX_VALUE }?.toLong()

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

    override fun readMonoRmsLevel(stream: BassStreamHandle, seconds: Float): Result<Float> {
        val level = FloatArray(1)
        val success = level.usePinned { pinned ->
            BASS_ChannelGetLevelEx(
                stream.uint,
                pinned.addressOf(0),
                seconds,
                (BASS_LEVEL_MONO or BASS_LEVEL_RMS).toUInt(),
            )
        }
        return if (success != 0) Result.success(level[0]) else failure("BASS_ChannelGetLevelEx failed")
    }

    // BASS returns native NUL-separated UTF-8 tag arrays and a single ICY metadata block.
    // Only pointer translation lives here; Core parses and presents the returned fields.
    override fun streamMetadata(stream: BassStreamHandle): Map<String, String> = bassStreamProperties(buildList {
        for (kind in listOf(BASS_TAG_ICY, BASS_TAG_HTTP, BASS_TAG_OGG, BASS_TAG_APE, BASS_TAG_MP4)) {
            var cursor = BASS_ChannelGetTags(stream.uint, kind.toUInt())?.reinterpret<ByteVar>() ?: continue
            while (cursor[0] != 0.toByte()) {
                add(cursor.toKString())
                var length = 0
                while (cursor[length] != 0.toByte()) length++
                cursor = (cursor + length + 1) ?: break
            }
        }
        BASS_ChannelGetTags(stream.uint, BASS_TAG_META.toUInt())?.toKString()
            ?.takeIf { it.isNotEmpty() }?.let(::add)
    })

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
        codecPlugins.forEach { framework ->
            val path = "$frameworksDirectory/$framework.framework/$framework"
            val handle = BASS_PluginLoad(path, 0u)
            if (handle != 0u) {
                pluginHandles[framework] = handle
            } else {
                failedPlugins[framework] = BASS_ErrorGetCode()
            }
        }
    }

    private fun createFile(path: String, flags: UInt, message: String): Result<BassStreamHandle> {
        ensureCodecPluginsLoaded()
        return memScoped {
            BASS_StreamCreateFile(0u, path.cstr.ptr, 0uL, 0uL, flags).handleResult(message)
        }
    }

    private fun createUrl(url: String, flags: UInt, message: String): Result<BassStreamHandle> {
        ensureCodecPluginsLoaded()
        return memScoped {
            BASS_StreamCreateURL(url, 0u, flags, null, null).handleResult(message)
        }
    }

    protected inline fun bassResult(message: String, block: () -> Boolean): Result<Unit> =
        if (block()) Result.success(Unit) else failure(message)

    protected fun UInt.handleResult(message: String): Result<BassStreamHandle> =
        if (this != 0u) Result.success(BassStreamHandle(toInt())) else failure(message)

    protected fun <T> failure(message: String): Result<T> {
        val detail = bassFailureMessage(message)
        NSLog("Naviamp BASS: $detail")
        return Result.failure(IllegalStateException(detail))
    }

    protected val BassStreamHandle.uint: UInt
        get() = value.toUInt()
}

class AppleBassPlaybackEngineRuntime : BassPlaybackEngineRuntime {
    private val preparedLock = NSLock()

    override fun localFilePath(url: String): String? = applePlaybackLocalFilePath(url)

    override fun <T> withPreparedPlaybackLock(block: () -> T): T {
        preparedLock.lock()
        return try {
            block()
        } finally {
            preparedLock.unlock()
        }
    }
}

/** Native file-URL translation shared by Apple playback and waveform adapters. */
fun applePlaybackLocalFilePath(url: String): String? = when {
    url.startsWith("file:") -> NSURL.URLWithString(url)?.path
    url.startsWith("/") -> url
    else -> null
}

private class AppleBassEndSync(callback: (BassStreamHandle) -> Unit) {
    private val lock = NSLock()
    private var callback: ((BassStreamHandle) -> Unit)? = callback
    private var reference: StableRef<AppleBassEndSync>? = StableRef.create(this)

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

private val AppleBassEndSyncProc = staticCFunction {
        _: UInt,
        channel: UInt,
        _: UInt,
        user: COpaquePointer?,
    ->
    if (user != null) {
        user.asStableRef<AppleBassEndSync>().get().fire(channel)
    }
}

// Native FILEPROCS ABI only. Core owns byte fetching, cursor bounds and cancellation.
private class AppleBassFileRegistration(val callbacks: BassFileCallbacks, val closed: AtomicBoolean = AtomicBoolean(false))
private val AppleBassFileClose = staticCFunction { user: COpaquePointer? ->
    if (user != null) {
        val reference = user.asStableRef<AppleBassFileRegistration>()
        val state = reference.get()
        if (state.closed.compareAndSet(false, true)) {
            state.callbacks.close()
            reference.dispose()
        }
    }
}
private val AppleBassFileLength = staticCFunction { user: COpaquePointer? ->
    user?.asStableRef<AppleBassFileRegistration>()?.get()?.callbacks?.length()?.toULong() ?: 0uL
}
private val AppleBassFileRead = staticCFunction { buffer: COpaquePointer?, count: UInt, user: COpaquePointer? ->
    if (buffer == null || user == null || count > Int.MAX_VALUE.toUInt()) 0u else {
        val callbacks = user.asStableRef<AppleBassFileRegistration>().get().callbacks
        var copied = 0
        while (copied < count.toInt()) {
            val bytes = callbacks.read(count.toInt() - copied)
            if (bytes.isEmpty()) break
            bytes.usePinned { memcpy(buffer.reinterpret<ByteVar>() + copied, it.addressOf(0), bytes.size.toULong()) }
            copied += bytes.size
        }
        copied.toUInt()
    }
}
private val AppleBassFileSeek = staticCFunction { offset: ULong, user: COpaquePointer? ->
    if (user != null && offset <= Long.MAX_VALUE.toULong() &&
        user.asStableRef<AppleBassFileRegistration>().get().callbacks.seek(offset.toLong())) 1 else 0
}

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.playback

import app.naviamp.apple.playback.AppleBassAudioBackend
import app.naviamp.apple.playback.AppleBassPlaybackEngineRuntime
import app.naviamp.domain.bass.BassCodecPluginInventory
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.bass.bassFailureMessage
import app.naviamp.domain.playback.AudioMixingMatrix
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.apple.bass.native.BASS_SAMPLE_FLOAT
import app.naviamp.apple.bass.native.BASS_POS_BYTE
import app.naviamp.apple.bass.native.BASS_ChannelSeconds2Bytes
import app.naviamp.apple.bass.native.BASS_ChannelBytes2Seconds
import app.naviamp.apple.bass.native.BASS_ChannelSetPosition
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.math.max
import app.naviamp.ios.bass.native.BASS_MIXER_CHAN_NORAMPIN
import app.naviamp.ios.bass.native.BASS_MIXER_CHAN_MATRIX
import app.naviamp.ios.bass.native.BASS_MIXER_QUEUE
import app.naviamp.ios.bass.native.BASS_MIXER_POSEX
import app.naviamp.ios.bass.native.BASS_Mixer_ChannelRemove
import app.naviamp.ios.bass.native.BASS_Mixer_ChannelSetMatrix
import app.naviamp.ios.bass.native.BASS_Mixer_ChannelGetPosition
import app.naviamp.ios.bass.native.BASS_Mixer_ChannelSetPosition
import app.naviamp.ios.bass.native.BASS_Mixer_GetVersion
import app.naviamp.ios.bass.native.BASS_Mixer_StreamAddChannel
import app.naviamp.ios.bass.native.BASS_Mixer_StreamCreate
import app.naviamp.ios.bass.native.BASS_POS_MIXER_RESET

/** iOS-only BASSmix ABI extension; base BASS and Apple resource lifetimes are shared. */
class IosBassAudioBackend : AppleBassAudioBackend(IosBassFrameworks, IosBassCodecPlugins) {
    override val mixerVersion: Int
        get() = BASS_Mixer_GetVersion().toInt()

    override val supportsMixer: Boolean = true

    override fun createMixer(frequency: Int, channels: Int, queueSources: Boolean): Result<BassStreamHandle> {
        val flags = (BASS_SAMPLE_FLOAT or BASS_MIXER_POSEX or if (queueSources) BASS_MIXER_QUEUE else 0).toUInt()
        return BASS_Mixer_StreamCreate(max(1, frequency).toUInt(), max(1, channels).toUInt(), flags)
            .handleResult("BASS_Mixer_StreamCreate failed")
    }

    override fun addMixerChannel(mixer: BassStreamHandle, stream: BassStreamHandle): Result<Unit> =
        bassResult("BASS_Mixer_StreamAddChannel failed") {
            BASS_Mixer_StreamAddChannel(mixer.uint, stream.uint, BASS_MIXER_CHAN_NORAMPIN.toUInt()) != 0
        }

    override fun addMixerChannelWithMatrix(
        mixer: BassStreamHandle,
        stream: BassStreamHandle,
        matrix: AudioMixingMatrix,
    ): Result<Unit> = runCatching {
        val flags = (BASS_MIXER_CHAN_NORAMPIN or BASS_MIXER_CHAN_MATRIX).toUInt()
        check(BASS_Mixer_StreamAddChannel(mixer.uint, stream.uint, flags) != 0) {
            bassFailureMessage("BASS_Mixer_StreamAddChannel matrix failed")
        }
        val applied = matrix.coefficients.toFloatArray().usePinned { coefficients ->
            BASS_Mixer_ChannelSetMatrix(stream.uint, coefficients.addressOf(0)) != 0
        }
        if (!applied) {
            BASS_Mixer_ChannelRemove(stream.uint)
            error(bassFailureMessage("BASS stereo downmix matrix failed"))
        }
    }

    override fun removeMixerChannel(stream: BassStreamHandle): Result<Unit> =
        bassResult("BASS_Mixer_ChannelRemove failed") { BASS_Mixer_ChannelRemove(stream.uint) != 0 }

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

    override fun mixerSourcePositionSeconds(stream: BassStreamHandle): Double? =
        BASS_Mixer_ChannelGetPosition(stream.uint, BASS_POS_BYTE.toUInt())
            .takeUnless { it == ULong.MAX_VALUE }
            ?.let { BASS_ChannelBytes2Seconds(stream.uint, it) }
            ?.takeIf { it.isFinite() && it >= 0.0 }

}

fun createIosBassPlaybackEngine(): CoreBassPlaybackEngine = CoreBassPlaybackEngine(
    backendResult = Result.success(IosBassAudioBackend()),
    runtime = AppleBassPlaybackEngineRuntime(),
)

private val IosBassCodecPlugins = IosBassFrameworks.filter(BassCodecPluginInventory.stems::contains).toSet()

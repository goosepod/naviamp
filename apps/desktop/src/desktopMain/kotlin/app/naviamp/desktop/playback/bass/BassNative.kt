package app.naviamp.desktop.playback.bass

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File

class BassNative private constructor(
    private val directory: File,
    private val library: BassLibrary,
    private val platform: BassPlatform,
) {
    val version: Int
        get() = library.BASS_GetVersion()

    val libraryDirectory: File
        get() = directory

    fun init(device: Int = -1, frequency: Int = 44_100): Result<Unit> =
        check(library.BASS_Init(device, frequency, flags = 0, window = null, clsid = null), "BASS_Init failed")

    fun free(): Result<Unit> =
        check(library.BASS_Free(), "BASS_Free failed")

    fun loadAvailablePlugins(): List<BassPlugin> =
        BassPlugins.names.mapNotNull { stem ->
            val file = File(directory, platform.libraryName(stem))
            if (!file.isFile) {
                return@mapNotNull null
            }
            val handle = library.BASS_PluginLoad(file.absolutePath, flags = 0)
            BassPlugin(
                stem = stem,
                file = file,
                handle = handle,
                loaded = handle != 0,
                errorCode = if (handle == 0) errorCode() else null,
            )
        }

    fun freePlugin(plugin: BassPlugin): Result<Unit> =
        if (plugin.handle == 0) {
            Result.success(Unit)
        } else {
            check(library.BASS_PluginFree(plugin.handle), "BASS_PluginFree failed")
        }

    fun createUrlStream(url: String, flags: Int = 0): Result<Int> {
        val handle = library.BASS_StreamCreateURL(
            url,
            offset = 0,
            flags = flags or BassFlags.StreamStatus,
            downloadProc = null,
            user = null,
        )
        return handleResult(handle, "BASS_StreamCreateURL failed")
    }

    fun createFileStream(path: File, flags: Int = BassFlags.StreamPrescan): Result<Int> {
        val handle = library.BASS_StreamCreateFile(
            memory = false,
            file = path.absolutePath,
            offset = 0,
            length = 0,
            flags = flags,
        )
        return handleResult(handle, "BASS_StreamCreateFile failed")
    }

    fun play(channel: Int, restart: Boolean = false): Result<Unit> =
        check(library.BASS_ChannelPlay(channel, restart), "BASS_ChannelPlay failed")

    fun pause(channel: Int): Result<Unit> =
        check(library.BASS_ChannelPause(channel), "BASS_ChannelPause failed")

    fun stop(channel: Int): Result<Unit> =
        check(library.BASS_ChannelStop(channel), "BASS_ChannelStop failed")

    fun activeState(channel: Int): Int =
        library.BASS_ChannelIsActive(channel)

    fun isPlaying(channel: Int): Boolean =
        activeState(channel) == BassActive.Playing

    fun freeStream(channel: Int): Result<Unit> =
        check(library.BASS_StreamFree(channel), "BASS_StreamFree failed")

    fun setVolume(channel: Int, volume: Float): Result<Unit> =
        check(
            library.BASS_ChannelSetAttribute(channel, BassAttributes.Volume, volume.coerceIn(0f, 1f)),
            "BASS_ChannelSetAttribute volume failed",
        )

    fun positionSeconds(channel: Int): Double? =
        seconds(channel, library.BASS_ChannelGetPosition(channel, BassPosition.Byte))

    fun durationSeconds(channel: Int): Double? =
        seconds(channel, library.BASS_ChannelGetLength(channel, BassPosition.Byte))

    fun seek(channel: Int, seconds: Double): Result<Unit> {
        val bytes = library.BASS_ChannelSeconds2Bytes(channel, seconds)
        return check(
            library.BASS_ChannelSetPosition(channel, bytes, BassPosition.Byte),
            "BASS_ChannelSetPosition failed",
        )
    }

    fun errorCode(): Int = library.BASS_ErrorGetCode()

    fun errorMessage(prefix: String): String =
        "$prefix: ${bassErrorMessage(errorCode())}"

    fun streamMetadata(channel: Int): Map<String, String> =
        buildMap {
            stringListTags(channel, BassTags.Icy).forEach { line ->
                val index = line.indexOf(':')
                if (index > 0) {
                    put(line.substring(0, index).trim(), line.substring(index + 1).trim())
                }
            }
            stringListTags(channel, BassTags.Http).forEach { line ->
                val index = line.indexOf(':')
                if (index > 0) {
                    put(line.substring(0, index).trim(), line.substring(index + 1).trim())
                }
            }
            metaTag(channel)?.let { meta ->
                put("StreamTitle", parseIcyStreamTitle(meta) ?: meta)
            }
        }

    private fun seconds(channel: Int, bytes: Long): Double? {
        val seconds = library.BASS_ChannelBytes2Seconds(channel, bytes)
        return seconds.takeIf { it.isFinite() && it >= 0.0 }
    }

    private fun metaTag(channel: Int): String? =
        library.BASS_ChannelGetTags(channel, BassTags.Meta)
            ?.getString(0)
            ?.takeIf { it.isNotBlank() }

    private fun stringListTags(channel: Int, tag: Int): List<String> {
        val pointer = library.BASS_ChannelGetTags(channel, tag) ?: return emptyList()
        val tags = mutableListOf<String>()
        var offset = 0L
        while (true) {
            val value = pointer.getString(offset)
            if (value.isEmpty()) break
            tags += value
            offset += value.toByteArray(Charsets.UTF_8).size + 1
        }
        return tags
    }

    private fun check(ok: Boolean, message: String): Result<Unit> =
        if (ok) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(errorMessage(message)))
        }

    private fun handleResult(handle: Int, message: String): Result<Int> =
        if (handle != 0) {
            Result.success(handle)
        } else {
            Result.failure(IllegalStateException(errorMessage(message)))
        }

    companion object {
        fun load(
            resolver: BassLibraryResolver = BassLibraryResolver(),
            platform: BassPlatform = BassPlatform.current(),
        ): Result<BassNative> {
            val directory = resolver.resolve()
                ?: return Result.failure(
                    IllegalStateException(
                        "Could not find BASS. Set naviamp.bass.dir or NAVIAMP_BASS_DIR, " +
                            "or bundle ${platform.libraryName("bass")} under playback/bass/${platform.id}.",
                    ),
                )
            val libraryFile = File(directory, platform.libraryName("bass"))
            return runCatching {
                @Suppress("UNCHECKED_CAST")
                val options = if (platform.os == "windows") {
                    mapOf(Library.OPTION_CALLING_CONVENTION to Function.ALT_CONVENTION)
                } else {
                    emptyMap<String, Any>()
                }
                val library = Native.load(libraryFile.absolutePath, BassLibrary::class.java, options) as BassLibrary
                BassNative(directory = directory, library = library, platform = platform)
            }
        }
    }
}

data class BassPlugin(
    val stem: String,
    val file: File,
    val handle: Int,
    val loaded: Boolean,
    val errorCode: Int?,
)

private interface BassLibrary : Library {
    fun BASS_Init(device: Int, frequency: Int, flags: Int, window: Pointer?, clsid: Pointer?): Boolean
    fun BASS_Free(): Boolean
    fun BASS_GetVersion(): Int
    fun BASS_ErrorGetCode(): Int
    fun BASS_StreamCreateURL(url: String, offset: Long, flags: Int, downloadProc: Pointer?, user: Pointer?): Int
    fun BASS_StreamCreateFile(memory: Boolean, file: String, offset: Long, length: Long, flags: Int): Int
    fun BASS_StreamFree(handle: Int): Boolean
    fun BASS_PluginLoad(file: String, flags: Int): Int
    fun BASS_PluginFree(handle: Int): Boolean
    fun BASS_ChannelPlay(handle: Int, restart: Boolean): Boolean
    fun BASS_ChannelPause(handle: Int): Boolean
    fun BASS_ChannelStop(handle: Int): Boolean
    fun BASS_ChannelIsActive(handle: Int): Int
    fun BASS_ChannelSetAttribute(handle: Int, attribute: Int, value: Float): Boolean
    fun BASS_ChannelGetLength(handle: Int, mode: Int): Long
    fun BASS_ChannelGetPosition(handle: Int, mode: Int): Long
    fun BASS_ChannelSetPosition(handle: Int, position: Long, mode: Int): Boolean
    fun BASS_ChannelBytes2Seconds(handle: Int, position: Long): Double
    fun BASS_ChannelSeconds2Bytes(handle: Int, seconds: Double): Long
    fun BASS_ChannelGetTags(handle: Int, tags: Int): Pointer?
}

private object BassPosition {
    const val Byte: Int = 0
}

private object BassAttributes {
    const val Volume: Int = 2
}

object BassFlags {
    const val StreamPrescan: Int = 0x20000
    const val StreamStatus: Int = 0x800000
}

object BassActive {
    const val Stopped: Int = 0
    const val Playing: Int = 1
    const val Stalled: Int = 2
    const val Paused: Int = 3
}

private object BassTags {
    const val Http: Int = 3
    const val Icy: Int = 4
    const val Meta: Int = 5
}

private object BassPlugins {
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

fun bassErrorMessage(code: Int): String =
    when (code) {
        0 -> "no error"
        1 -> "memory error"
        2 -> "file open error"
        3 -> "driver error"
        4 -> "buffer lost"
        5 -> "invalid handle"
        6 -> "unsupported sample format"
        7 -> "invalid position"
        8 -> "BASS_Init has not been called"
        9 -> "BASS_Start has not been called"
        14 -> "already initialized"
        18 -> "no available channel"
        19 -> "illegal type"
        20 -> "illegal parameter"
        21 -> "no 3D support"
        22 -> "no EAX support"
        23 -> "illegal device"
        24 -> "not playing"
        25 -> "illegal sample rate"
        27 -> "not a file stream"
        29 -> "no hardware voices"
        31 -> "empty file"
        32 -> "non-internet file"
        33 -> "could not create file"
        34 -> "no effects available"
        37 -> "requested data/action is not available"
        38 -> "channel is decoding"
        39 -> "channel is not a decoding channel"
        40 -> "connection timed out"
        41 -> "unsupported file format"
        42 -> "speaker unavailable"
        43 -> "BASS version mismatch"
        44 -> "codec unavailable"
        45 -> "ended"
        46 -> "device busy"
        47 -> "unsupported protocol"
        48 -> "unsupported protocol"
        49 -> "access denied"
        50 -> "SSL unavailable"
        -1 -> "unknown BASS error"
        else -> "BASS error $code"
    }

private fun parseIcyStreamTitle(meta: String): String? {
    val key = "StreamTitle='"
    val start = meta.indexOf(key)
    if (start < 0) return null
    val titleStart = start + key.length
    val titleEnd = meta.indexOf("';", titleStart).takeIf { it >= 0 } ?: meta.indexOf("'", titleStart)
    if (titleEnd <= titleStart) return null
    return meta.substring(titleStart, titleEnd).trim().takeIf { it.isNotBlank() }
}

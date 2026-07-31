@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.playback

import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.audio.AudioTagReader
import app.naviamp.domain.audio.audioTagsFromAudioBytes
import app.naviamp.domain.playback.PlaybackLocalAudio
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread

/** POSIX file-read boundary; audio tag parsing and presentation remain shared in Core. */
class IosAudioTagReader : AudioTagReader {
    override suspend fun read(localAudio: PlaybackLocalAudio): List<AudioTag> {
        val handle = fopen(localAudio.path, "rb") ?: return emptyList()
        return try {
            val bytes = ByteArray(MaxAudioTagProbeBytes)
            val count = bytes.usePinned { pinned ->
                fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), handle).toInt()
            }
            if (count <= 0) emptyList() else audioTagsFromAudioBytes(bytes.copyOf(count))
        } finally {
            fclose(handle)
        }
    }
}

private const val MaxAudioTagProbeBytes = 2 * 1024 * 1024

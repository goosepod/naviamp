package app.naviamp.android.playback

import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.audio.AudioTagReader
import app.naviamp.domain.audio.audioTagsFromAudioBytes
import app.naviamp.domain.playback.PlaybackLocalAudio
import java.io.File

class AndroidAudioTagReader : AudioTagReader {
    override suspend fun read(localAudio: PlaybackLocalAudio): List<AudioTag> {
        val file = File(localAudio.path)
        if (!file.isFile) return emptyList()
        return runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(MaxAudioTagProbeBytes)
                val read = input.read(buffer)
                if (read <= 0) emptyList() else audioTagsFromAudioBytes(buffer.copyOf(read))
            }
        }.getOrDefault(emptyList())
    }
}

private const val MaxAudioTagProbeBytes = 2 * 1024 * 1024

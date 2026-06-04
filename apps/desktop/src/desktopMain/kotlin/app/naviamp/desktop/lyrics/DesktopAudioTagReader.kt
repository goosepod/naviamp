package app.naviamp.desktop

import app.naviamp.domain.audio.audioTagsFromAudioBytes
import java.nio.file.Path
import kotlin.io.path.exists

typealias AudioTag = app.naviamp.domain.audio.AudioTag

class DesktopAudioTagReader {
    fun read(path: Path): List<AudioTag> {
        if (!path.exists()) return emptyList()
        return runCatching {
            java.nio.file.Files.newInputStream(path).use { input ->
                audioTagsFromAudioBytes(input.readNBytes(MaxAudioTagProbeBytes))
            }
        }.getOrDefault(emptyList())
    }
}

private const val MaxAudioTagProbeBytes = 2 * 1024 * 1024

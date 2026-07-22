package app.naviamp.desktop

import app.naviamp.domain.audio.AudioTagReader
import app.naviamp.domain.audio.audioTagsFromAudioBytes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

typealias AudioTag = app.naviamp.domain.audio.AudioTag

/** JVM filesystem adapter. Tag parsing and all tag-driven product behavior remain in Core. */
class DesktopAudioTagReader : AudioTagReader {
    override suspend fun read(localAudio: app.naviamp.domain.playback.PlaybackLocalAudio): List<AudioTag> =
        read(Path.of(localAudio.path))

    fun read(path: Path): List<AudioTag> {
        if (!path.exists()) return emptyList()
        return runCatching {
            Files.newInputStream(path).use { input ->
                audioTagsFromAudioBytes(input.readNBytes(MaxAudioTagProbeBytes))
            }
        }.getOrDefault(emptyList())
    }
}

private const val MaxAudioTagProbeBytes = 2 * 1024 * 1024

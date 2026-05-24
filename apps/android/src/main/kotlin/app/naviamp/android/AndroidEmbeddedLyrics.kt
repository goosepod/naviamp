package app.naviamp.android

import app.naviamp.domain.Lyrics
import app.naviamp.domain.lyrics.embeddedLyricsFromId3v2
import java.io.File

fun embeddedLyricsFromAudioFile(file: File): Lyrics? =
    runCatching {
        if (!file.isFile) return null
        file.inputStream().use { input ->
            val buffer = ByteArray(MaxEmbeddedLyricsProbeBytes)
            val read = input.read(buffer)
            if (read <= 0) null else embeddedLyricsFromId3v2(buffer.copyOf(read))
        }
    }.getOrNull()

private const val MaxEmbeddedLyricsProbeBytes = 2 * 1024 * 1024

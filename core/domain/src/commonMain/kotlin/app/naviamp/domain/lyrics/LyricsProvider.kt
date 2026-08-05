package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track

interface LyricsProvider {
    val id: String
    val capabilities: Set<LyricsTiming>

    suspend fun lyrics(track: Track): Lyrics?
}

enum class LyricsTiming {
    Plain,
    LineSynced,
    WordSynced,
}

val Lyrics.timing: LyricsTiming
    get() = when {
        hasKaraokeCues -> LyricsTiming.WordSynced
        synced || hasTimedLines -> LyricsTiming.LineSynced
        else -> LyricsTiming.Plain
    }

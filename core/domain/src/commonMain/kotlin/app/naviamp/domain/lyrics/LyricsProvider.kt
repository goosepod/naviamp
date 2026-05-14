package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track

interface LyricsProvider {
    val id: String

    suspend fun lyrics(track: Track): Lyrics?
}

package app.naviamp.domain.lyrics

import app.naviamp.domain.Lyrics
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LyricsOffsetRepository

class LyricsOffsetController(
    private val lyricsOffsetRepository: LyricsOffsetRepository,
) {
    fun withSavedOffset(
        sourceId: String?,
        track: Track?,
        lyrics: Lyrics?,
    ): Lyrics? {
        if (sourceId == null || track == null || lyrics == null) return lyrics
        return lyrics.copy(
            offsetMillis = lyricsOffsetRepository.lyricsOffsetMillis(sourceId, track.id)
                ?: lyrics.offsetMillis,
        )
    }

    fun saveOffset(
        sourceId: String?,
        track: Track?,
        lyrics: Lyrics?,
        offsetMillis: Int,
    ): Lyrics? {
        if (sourceId == null || track == null) return lyrics
        lyricsOffsetRepository.saveLyricsOffsetMillis(sourceId, track.id, offsetMillis)
        return lyrics?.copy(offsetMillis = offsetMillis)
    }
}

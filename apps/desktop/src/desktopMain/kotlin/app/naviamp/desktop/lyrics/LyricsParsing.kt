package app.naviamp.desktop

import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.lyrics.lyricsFromTaggedText
import app.naviamp.domain.lyrics.lyricsFromText as domainLyricsFromText

fun lyricsFromAudioTags(tags: List<AudioTag>): Lyrics? {
    val lyricTag = tags.firstOrNull { lyricsFromTaggedText(it.key, it.value) != null } ?: return null
    return lyricsFromTaggedText(lyricTag.key, lyricTag.value)
}

fun lyricsFromText(
    source: LyricsSource,
    text: String,
): Lyrics? =
    domainLyricsFromText(source, text)

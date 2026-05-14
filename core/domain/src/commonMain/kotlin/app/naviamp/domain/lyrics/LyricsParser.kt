package app.naviamp.domain.lyrics

import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource

fun lyricsFromText(
    source: LyricsSource,
    text: String,
    displayArtist: String? = null,
    displayTitle: String? = null,
): Lyrics? {
    val normalizedText = text.trim()
    if (normalizedText.isBlank()) return null
    val lrcLines = parseLrcLines(normalizedText)
    if (lrcLines.isNotEmpty()) {
        return Lyrics(
            source = source,
            synced = true,
            lines = lrcLines,
            displayArtist = displayArtist,
            displayTitle = displayTitle,
        )
    }
    val plainLines = normalizedText
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { LyricLine(startMillis = null, text = it) }
    if (plainLines.isEmpty()) return null
    return Lyrics(
        source = source,
        synced = false,
        lines = plainLines,
        displayArtist = displayArtist,
        displayTitle = displayTitle,
    )
}

fun lyricsFromTaggedText(
    key: String,
    value: String,
    source: LyricsSource = LyricsSource.Embedded,
): Lyrics? =
    if (key.isLyricsTag()) lyricsFromText(source, value) else null

private fun parseLrcLines(text: String): List<LyricLine> {
    val parsed = mutableListOf<LyricLine>()
    text.lineSequence().forEach { rawLine ->
        val matches = LrcTimestampRegex.findAll(rawLine).toList()
        if (matches.isEmpty()) return@forEach
        val lineText = rawLine.replace(LrcTimestampRegex, "").trim()
        if (lineText.isBlank()) return@forEach
        matches.forEach { match ->
            parsed += LyricLine(
                startMillis = match.value.toLrcMillis(),
                text = lineText,
            )
        }
    }
    return parsed
        .filter { it.startMillis != null }
        .sortedBy { it.startMillis }
}

private fun String.toLrcMillis(): Long? {
    val match = LrcTimestampRegex.matchEntire(this) ?: return null
    val minutes = match.groupValues[1].toLongOrNull() ?: return null
    val seconds = match.groupValues[2].toLongOrNull() ?: return null
    val fraction = match.groupValues[3]
    val fractionMillis = when (fraction.length) {
        0 -> 0L
        1 -> fraction.toLongOrNull()?.times(100) ?: 0L
        2 -> fraction.toLongOrNull()?.times(10) ?: 0L
        else -> fraction.take(3).toLongOrNull() ?: 0L
    }
    return minutes * 60_000 + seconds * 1_000 + fractionMillis
}

private fun String.isLyricsTag(): Boolean =
    trim().lowercase().replace(" ", "").replace("_", "").replace("-", "") in LyricsTagKeys

private val LyricsTagKeys = setOf(
    "lyrics",
    "lyric",
    "unsyncedlyrics",
    "unsynchronizedlyrics",
    "syncedlyrics",
    "synchronizedlyrics",
)

private val LrcTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

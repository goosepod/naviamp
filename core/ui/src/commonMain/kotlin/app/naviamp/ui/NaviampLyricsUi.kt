package app.naviamp.ui

import app.naviamp.domain.LyricCue
import app.naviamp.domain.Lyrics

internal fun Lyrics.toNaviampLyricLinesUi(): List<NaviampLyricLineUi> {
    val cuesByLineIndex = cueLines.associateBy { it.lineIndex }
    return lines.mapIndexed { index, line ->
        val cueLine = cuesByLineIndex[index]
        NaviampLyricLineUi(
            startMillis = cueLine?.startMillis ?: line.startMillis,
            text = line.text,
            endMillis = cueLine?.endMillis,
            agentId = cueLine?.agentId,
            cues = cueLine?.cues.orEmpty().map { it.toNaviampLyricCueUi() },
        )
    }
}

private fun LyricCue.toNaviampLyricCueUi(): NaviampLyricCueUi =
    NaviampLyricCueUi(
        startMillis = startMillis,
        endMillis = endMillis,
        text = text,
        byteStart = byteStart,
        byteEnd = byteEnd,
    )

internal data class NaviampLyricHighlightSegment(
    val text: String,
    val progress: Float,
)

/**
 * A stable revision for word-synced text layout. Karaoke text only needs a new paragraph when a
 * cue begins; rebuilding it for every playback tick overwhelms native text-resource cleanup on
 * Desktop during long sessions.
 */
internal fun NaviampLyricLineUi.karaokeHighlightRevision(
    positionMillis: Long?,
    offsetMillis: Int,
): Int {
    val position = positionMillis ?: return 0
    return cues.count { cue ->
        cue.startMillis?.plus(offsetMillis.toLong())?.let { it < position } == true
    }
}

/**
 * Converts absolute karaoke cue timing into non-overlapping display segments for one lyric line.
 * The original line text is always preserved; malformed or partial cue ranges simply remain
 * unhighlighted instead of replacing the line with provider fragments.
 */
internal fun NaviampLyricLineUi.karaokeHighlightSegments(
    positionMillis: Long?,
    offsetMillis: Int,
): List<NaviampLyricHighlightSegment> {
    if (text.isEmpty() || cues.isEmpty()) {
        return listOf(NaviampLyricHighlightSegment(text, 0f))
    }

    val segments = mutableListOf<NaviampLyricHighlightSegment>()
    var cursor = 0
    var precedingProgress = 0f
    cues.forEachIndexed { index, cue ->
        val range = text.resolveCueRange(cue, cursor) ?: return@forEachIndexed
        if (range.start < cursor || range.endExclusive <= range.start) return@forEachIndexed

        val progress = cue.highlightProgress(
            positionMillis = positionMillis,
            offsetMillis = offsetMillis,
            fallbackEndMillis = cues.getOrNull(index + 1)?.startMillis ?: endMillis,
        )
        if (range.start > cursor) {
            segments += NaviampLyricHighlightSegment(
                text = text.substring(cursor, range.start),
                progress = if (progress > 0f) 1f else precedingProgress,
            )
        }
        segments += NaviampLyricHighlightSegment(
            text = text.substring(range.start, range.endExclusive),
            progress = progress,
        )
        cursor = range.endExclusive
        precedingProgress = progress
    }

    if (cursor < text.length) {
        segments += NaviampLyricHighlightSegment(
            text = text.substring(cursor),
            progress = precedingProgress,
        )
    }
    return segments.ifEmpty { listOf(NaviampLyricHighlightSegment(text, 0f)) }
}

private fun NaviampLyricCueUi.highlightProgress(
    positionMillis: Long?,
    offsetMillis: Int,
    fallbackEndMillis: Long?,
): Float {
    val position = positionMillis ?: return 0f
    val start = startMillis?.plus(offsetMillis.toLong()) ?: return 0f
    if (position <= start) return 0f
    val end = (endMillis ?: fallbackEndMillis)?.plus(offsetMillis.toLong())
    if (end == null || end <= start || position >= end) return 1f
    return ((position - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}

private fun String.resolveCueRange(cue: NaviampLyricCueUi, searchFrom: Int): LyricTextRange? {
    val byteRange = cue.byteRangeIn(this)
    if (byteRange != null &&
        byteRange.start >= searchFrom &&
        substring(byteRange.start, byteRange.endExclusive) == cue.text
    ) {
        return byteRange
    }

    val matchedStart = indexOf(cue.text, startIndex = searchFrom)
    return matchedStart.takeIf { it >= 0 }?.let { start ->
        LyricTextRange(start = start, endExclusive = start + cue.text.length)
    }
}

private fun NaviampLyricCueUi.byteRangeIn(line: String): LyricTextRange? {
    val startByte = byteStart ?: return null
    val endByteInclusive = byteEnd ?: return null
    if (startByte < 0 || endByteInclusive < startByte) return null
    val start = line.utf8ByteOffsetToCharIndex(startByte) ?: return null
    val endExclusive = line.utf8ByteOffsetToCharIndex(endByteInclusive + 1) ?: return null
    return LyricTextRange(start = start, endExclusive = endExclusive)
}

private fun String.utf8ByteOffsetToCharIndex(targetOffset: Int): Int? {
    if (targetOffset < 0) return null
    var charIndex = 0
    var byteOffset = 0
    while (charIndex < length) {
        if (byteOffset == targetOffset) return charIndex
        val charCount = if (this[charIndex].isHighSurrogate() &&
            charIndex + 1 < length &&
            this[charIndex + 1].isLowSurrogate()
        ) {
            2
        } else {
            1
        }
        byteOffset += substring(charIndex, charIndex + charCount).encodeToByteArray().size
        charIndex += charCount
        if (byteOffset > targetOffset) return null
    }
    return charIndex.takeIf { byteOffset == targetOffset }
}

private data class LyricTextRange(
    val start: Int,
    val endExclusive: Int,
)

package app.naviamp.ui

import app.naviamp.domain.LyricCue
import app.naviamp.domain.LyricCueLine
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaviampLyricsUiTest {
    @Test
    fun mapsProviderCueLinesIntoSharedUiWithoutFlatteningWordTiming() {
        val lyrics = Lyrics(
            source = LyricsSource.Provider,
            synced = true,
            lines = listOf(LyricLine(startMillis = 1_000, text = "Under the neon sky")),
            cueLines = listOf(
                LyricCueLine(
                    lineIndex = 0,
                    startMillis = 1_000,
                    endMillis = 3_400,
                    text = "Under the neon sky",
                    agentId = "lead",
                    cues = listOf(
                        LyricCue(1_000, 1_480, "Under", 0, 4),
                        LyricCue(1_580, 1_960, "the", 6, 8),
                    ),
                ),
            ),
        )

        val line = lyrics.toNaviampLyricLinesUi().single()

        assertEquals(1_000, line.startMillis)
        assertEquals(3_400, line.endMillis)
        assertEquals("lead", line.agentId)
        assertEquals(listOf("Under", "the"), line.cues.map { it.text })
    }

    @Test
    fun progressivelyHighlightsCueForItsFullDuration() {
        val line = NaviampLyricLineUi(
            startMillis = 8_200,
            endMillis = 12_400,
            text = "Oh",
            cues = listOf(NaviampLyricCueUi(8_200, 12_400, "Oh", 0, 1)),
        )

        val segment = line.karaokeHighlightSegments(positionMillis = 10_300, offsetMillis = 0).single()

        assertEquals("Oh", segment.text)
        assertProgress(0.5f, segment.progress)
    }

    @Test
    fun appliesLyricsOffsetOnceToCueTiming() {
        val line = NaviampLyricLineUi(
            startMillis = 8_200,
            endMillis = 12_400,
            text = "Oh",
            cues = listOf(NaviampLyricCueUi(8_200, 12_400, "Oh")),
        )

        assertProgress(0f, line.karaokeHighlightSegments(8_500, offsetMillis = 500).single().progress)
        assertProgress(0.5f, line.karaokeHighlightSegments(10_800, offsetMillis = 500).single().progress)
    }

    @Test
    fun preservesSpacesAndUnicodeWhenUsingUtf8ByteRanges() {
        val line = NaviampLyricLineUi(
            startMillis = 1_000,
            endMillis = 3_000,
            text = "Café déjà",
            cues = listOf(
                NaviampLyricCueUi(1_000, 1_800, "Café", byteStart = 0, byteEnd = 4),
                NaviampLyricCueUi(2_000, 3_000, "déjà", byteStart = 6, byteEnd = 11),
            ),
        )

        val segments = line.karaokeHighlightSegments(positionMillis = 1_900, offsetMillis = 0)

        assertEquals("Café déjà", segments.joinToString(separator = "") { it.text })
        assertEquals(listOf("Café", " ", "déjà"), segments.map { it.text })
        assertProgress(1f, segments[0].progress)
        assertProgress(0f, segments[2].progress)
    }

    @Test
    fun fallsBackToSequentialCueTextWhenByteOffsetsAreMissingOrInvalid() {
        val line = NaviampLyricLineUi(
            startMillis = 1_000,
            endMillis = 3_000,
            text = "go go",
            cues = listOf(
                NaviampLyricCueUi(1_000, 1_500, "go", byteStart = 99, byteEnd = 100),
                NaviampLyricCueUi(2_000, 2_500, "go"),
            ),
        )

        val segments = line.karaokeHighlightSegments(positionMillis = 1_750, offsetMillis = 0)

        assertEquals("go go", segments.joinToString(separator = "") { it.text })
        assertEquals(listOf("go", " ", "go"), segments.map { it.text })
        assertProgress(1f, segments.first().progress)
        assertProgress(0f, segments.last().progress)
    }

    @Test
    fun textLayoutRevisionOnlyChangesAtWordBoundaries() {
        val line = NaviampLyricLineUi(
            startMillis = 1_000,
            endMillis = 3_000,
            text = "one two",
            cues = listOf(
                NaviampLyricCueUi(1_000, 1_800, "one"),
                NaviampLyricCueUi(2_000, 3_000, "two"),
            ),
        )

        assertEquals(0, line.karaokeHighlightRevision(1_000, offsetMillis = 0))
        assertEquals(1, line.karaokeHighlightRevision(1_100, offsetMillis = 0))
        assertEquals(1, line.karaokeHighlightRevision(1_900, offsetMillis = 0))
        assertEquals(2, line.karaokeHighlightRevision(2_100, offsetMillis = 0))
    }

    @Test
    fun textLayoutRevisionAppliesTheLyricsOffset() {
        val line = NaviampLyricLineUi(
            startMillis = 1_000,
            text = "one",
            cues = listOf(NaviampLyricCueUi(1_000, 1_800, "one")),
        )

        assertEquals(0, line.karaokeHighlightRevision(1_400, offsetMillis = 500))
        assertEquals(1, line.karaokeHighlightRevision(1_600, offsetMillis = 500))
    }

    private fun assertProgress(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.0001f, "expected=$expected actual=$actual")
    }
}

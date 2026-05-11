package app.naviamp.desktop

import app.naviamp.domain.LyricsSource
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsParsingTest {
    @Test
    fun `parses synced lrc lines`() {
        val lyrics = lyricsFromText(
            source = LyricsSource.Embedded,
            text = """
                [00:10.50]First line
                [00:12.000]Second line
            """.trimIndent(),
        )

        assertEquals(true, lyrics?.synced)
        assertEquals(listOf(10_500L, 12_000L), lyrics?.lines?.map { it.startMillis })
        assertEquals(listOf("First line", "Second line"), lyrics?.lines?.map { it.text })
    }

    @Test
    fun `parses plain lyrics as unsynced lines`() {
        val lyrics = lyricsFromText(
            source = LyricsSource.Embedded,
            text = """
                First line

                Second line
            """.trimIndent(),
        )

        assertEquals(false, lyrics?.synced)
        assertEquals(listOf(null, null), lyrics?.lines?.map { it.startMillis })
        assertEquals(listOf("First line", "Second line"), lyrics?.lines?.map { it.text })
    }
}

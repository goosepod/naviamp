package app.naviamp.domain.library

import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryLetterJumpTest {
    @Test
    fun numbersAndUnicodeSymbolsDoNotMatchLetterGroups() {
        assertEquals(3, libraryLetterJumpIndex(listOf("’90s Rock Essentials", "10 000 Hz Legend", "25", "G I R L"), 'G'))
        assertEquals('#', libraryTitleLetter("’90s Rock Essentials"))
        assertEquals('#', libraryTitleLetter("25"))
    }

    @Test
    fun missingLetterUsesTheNearestFollowingGroup() {
        assertEquals(2, libraryLetterJumpIndex(listOf("’90s", "The Album", "Hotel", "Zebra"), 'g'))
        assertEquals(-1, libraryLetterJumpIndex(listOf("Album"), 'Z'))
        assertEquals(-1, libraryLetterJumpIndex(emptyList(), 'A'))
        assertEquals(0, libraryLetterJumpIndex(listOf("25", "Album"), '#'))
    }
}

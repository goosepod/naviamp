package app.naviamp.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderDescriptionTest {
    @Test
    fun preservesProviderLineAndParagraphBreaks() {
        assertEquals(
            "First line\nSecond line\n\nNext paragraph",
            "  First line\r\nSecond line\r\n\r\nNext paragraph  ".normalizedProviderDescription(),
        )
    }
}

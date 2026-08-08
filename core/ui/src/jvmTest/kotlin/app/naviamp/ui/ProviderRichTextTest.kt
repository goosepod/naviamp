package app.naviamp.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderRichTextTest {
    @Test
    fun rendersProviderBoldAndItalicMarkup() {
        val result = "<b>100 Best Albums</b> and <i>Back in Black</i>".toProviderRichText()

        assertEquals("100 Best Albums and Back in Black", result.text)
        assertTrue(result.spanStyles.any { range ->
            range.start == 0 && range.end == 15 && range.item.fontWeight == FontWeight.Bold
        })
        assertTrue(result.spanStyles.any { range ->
            range.start == 20 && range.end == 33 && range.item.fontStyle == FontStyle.Italic
        })
    }

    @Test
    fun stripsUnsupportedTagsAndDecodesEntities() {
        val result = "<a href=\"url\">Artist</a> &amp; Album<br>next&nbsp;line".toProviderRichText()

        assertEquals("Artist & Album\nnext line", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun supportsNestedStylesAndNumericEntities() {
        val result = "<strong><em>Both</em></strong> &#8217; &#x1F3B5;".toProviderRichText()

        assertEquals("Both ’ 🎵", result.text)
        assertTrue(result.spanStyles.any { range ->
            range.start == 0 && range.end == 4 &&
                range.item.fontWeight == FontWeight.Bold &&
                range.item.fontStyle == FontStyle.Italic
        })
    }
}

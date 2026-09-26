package app.naviamp.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderRichTextTest {
    @Test
    fun decodesArtistAndAlbumApostrophesIncludingEscapedNumericReferences() {
        for (reference in listOf("&#039;", "&#39;", "&#x27;", "&#X27;", "&apos;", "&amp;#039;", "&amp;#x27;", "&#38;#39;")) {
            assertEquals("Aether Elf's music", "Aether Elf${reference}s music".toProviderRichText().text)
        }
    }

    @Test
    fun supportsCaseSensitiveNamedEntitiesAndMultiCodePointReferences() {
        assertEquals("É é © — … ≂̸ ∳", "&Eacute; &eacute; &copy; &mdash; &hellip; &NotEqualTilde; &CounterClockwiseContourIntegral;".toProviderRichText().text)
        assertEquals("日本語 & café 🎵", "日本語 &amp; café &#x1F3B5;".toProviderRichText().text)
        assertEquals("; ¬", "&semi; &not;".toProviderRichText().text)
    }

    @Test
    fun preservesLiteralAmpersandsAndMalformedReferences() {
        val text = "R&B &unknown; &notanentity; &notit; &ampx; &#; &#x; &#xZZ; &#-1; &#+39; &#0; &#xD800; &#1114112; &#99999999999999999999; &amp no semicolon"
        assertEquals(text, text.toProviderRichText().text)
        assertEquals("Already decoded: Aether Elf's café & music", "Already decoded: Aether Elf's café & music".toProviderRichText().text)
    }

    @Test
    fun decodesOnlyTextWithoutReinterpretingDecodedMarkupOrRepeatedEscapes() {
        val result = "&lt;b&gt;literal&lt;/b&gt; &amp;amp; &amp;lt; &amp;#60;b&amp;#62;".toProviderRichText()
        assertEquals("<b>literal</b> &amp; &lt; <b>", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun preservesParagraphsAndComparisonTextWhileDiscardingNonContentMarkup() {
        val result = "<p>One &amp; two</p><p>Three<br>Four</p>\n5 < 7 > 3<!-- hidden -->".toProviderRichText()
        assertEquals("One & two\n\nThree\nFour\n5 < 7 > 3", result.text)
        assertEquals("Before link after", "Before <a title=\"a > b\">link</a><script>alert('bad')</script><style>.hidden{}</style> after".toProviderRichText().text)
    }

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

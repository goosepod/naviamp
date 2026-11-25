package app.naviamp.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.naviamp.domain.settings.InterfaceFontFamily
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.resolvedFontSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampInstalledFontPolicyTest {
    private val fonts = FakeInstalledFonts(
        mapOf(
            "Readable Sans" to FakeFace(drawsLatin = true, cmap = characterMap(3 to 1, 1 to 0)),
            "Unicode Full" to FakeFace(drawsLatin = true, cmap = characterMap(0 to 4)),
            "Opaque Sans" to FakeFace(drawsLatin = true, cmap = null),
            "Dingbats" to FakeFace(drawsLatin = false, cmap = characterMap(3 to 1)),
            "Legacy Symbol" to FakeFace(drawsLatin = true, cmap = characterMap(3 to 0, 1 to 0)),
            ".HiddenAlias" to FakeFace(drawsLatin = true, cmap = characterMap(3 to 1)),
        ),
    )

    @Test
    fun symbolOnlyFamiliesAreNeverOffered() {
        assertEquals(listOf("Opaque Sans", "Readable Sans", "Unicode Full"), usableInstalledFontFamilies(fonts))
    }

    @Test
    fun aStoredOrSyncedSymbolFamilyFallsBackToNunitoSansAndKeepsTheChoice() {
        for (symbol in listOf("Dingbats", "Legacy Symbol")) {
            val settings = InterfaceSettings(fontFamily = InterfaceFontFamily.Inter, installedFontFamily = symbol)
            val selection = settings.resolvedFontSelection(usableInstalledFamiliesMatching(fonts, symbol))
            assertEquals(InterfaceFontFamily.NunitoSans, selection.family)
            assertNull(selection.installedFamily)
            assertEquals(symbol, selection.unavailableInstalledFamily)
            assertEquals(symbol, settings.normalized().installedFontFamily)
        }
        val readable = InterfaceSettings(installedFontFamily = "readable sans")
        assertEquals("Readable Sans", readable.resolvedFontSelection(usableInstalledFamiliesMatching(fonts, "readable sans")).installedFamily)
    }

    @Test
    fun characterMapsWithoutUnicodeSubtablesAreSymbolOnly() {
        assertTrue(hasUnicodeCharacterMap(characterMap(3 to 10)))
        assertTrue(hasUnicodeCharacterMap(characterMap(0 to 3)))
        assertFalse(hasUnicodeCharacterMap(characterMap(3 to 0)))
        assertFalse(hasUnicodeCharacterMap(characterMap(1 to 0, 3 to 0)))
        // Truncated tables are not trusted as evidence of a symbol font.
        assertTrue(hasUnicodeCharacterMap(byteArrayOf(0, 0, 0, 5)))
        assertTrue(hasUnicodeCharacterMap(ByteArray(2)))
    }

    private data class FakeFace(val drawsLatin: Boolean, val cmap: ByteArray?)

    private class FakeInstalledFonts(private val faces: Map<String, FakeFace>) : NaviampInstalledFonts {
        override fun familyNames(): List<String> = faces.keys.toList()
        override fun fontFamily(name: String, weights: List<FontWeight>): FontFamily = FontFamily.Default
        override fun hasGlyphs(name: String, codePoints: IntArray): Boolean = faces[name]?.drawsLatin == true
        override fun characterMapTable(name: String): ByteArray? = faces[name]?.cmap
    }

    private fun characterMap(vararg records: Pair<Int, Int>): ByteArray {
        val bytes = ByteArray(4 + records.size * 8)
        fun put16(offset: Int, value: Int) {
            bytes[offset] = (value shr 8).toByte()
            bytes[offset + 1] = value.toByte()
        }
        put16(2, records.size)
        records.forEachIndexed { index, (platform, encoding) ->
            put16(4 + index * 8, platform)
            put16(6 + index * 8, encoding)
        }
        return bytes
    }
}

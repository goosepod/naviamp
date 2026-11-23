package app.naviamp.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** The host's font manager; which family is chosen, fallback and presentation stay shared. */
internal interface NaviampInstalledFonts {
    fun familyNames(): List<String>
    fun fontFamily(name: String, weights: List<FontWeight>): FontFamily

    /** Whether the family's regular face maps every code point to a glyph; false when it cannot be loaded. */
    fun hasGlyphs(name: String, codePoints: IntArray): Boolean

    /** The raw OpenType `cmap` table of the family's regular face, or null when it is unavailable. */
    fun characterMapTable(name: String): ByteArray?
}

/** Null where the host has no installed-font enumeration. */
internal expect fun naviampInstalledFonts(): NaviampInstalledFonts?

/** The family the host's own interface uses by default. */
internal expect fun naviampSystemFontFamily(weights: List<FontWeight>): FontFamily

package app.naviamp.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/** Lists, inspects and loads families through Skia's native font manager, the one Compose renders with. */
internal actual fun naviampInstalledFonts(): NaviampInstalledFonts? = DesktopInstalledFonts

private object DesktopInstalledFonts : NaviampInstalledFonts {
    override fun familyNames(): List<String> {
        val manager = FontMgr.default
        return (0 until manager.familiesCount).map(manager::getFamilyName)
    }

    @OptIn(ExperimentalTextApi::class)
    override fun fontFamily(name: String, weights: List<FontWeight>): FontFamily =
        FontFamily(weights.map { SystemFont(name, it) })

    override fun hasGlyphs(name: String, codePoints: IntArray): Boolean {
        val typeface = FontMgr.default.matchFamilyStyle(name, FontStyle.NORMAL) ?: return false
        return typeface.getUTF32Glyphs(codePoints).none { it.toInt() == 0 }
    }

    override fun characterMapTable(name: String): ByteArray? =
        FontMgr.default.matchFamilyStyle(name, FontStyle.NORMAL)?.getTableData("cmap")?.bytes
}

/**
 * Compose maps sans-serif to Segoe UI on Windows and the system UI font on macOS, but to a fixed
 * list on Linux, where fontconfig holds the desktop's configured default.
 */
internal actual fun naviampSystemFontFamily(weights: List<FontWeight>): FontFamily {
    if (hostOs != OS.Linux) return FontFamily.SansSerif
    val family = FontMgr.default.matchFamilyStyle("sans-serif", FontStyle.NORMAL)?.familyName
        ?: return FontFamily.SansSerif
    return DesktopInstalledFonts.fontFamily(family, weights)
}

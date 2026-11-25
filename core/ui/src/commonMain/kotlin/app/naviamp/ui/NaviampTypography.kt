package app.naviamp.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.naviamp.domain.settings.InterfaceFontFamily
import app.naviamp.domain.settings.InterfaceFontSelection
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.resolvedFontSelection
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.inter
import app.naviamp.ui.generated.resources.nunito_sans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.Font

/** The app typography for the font chosen in [interfaceSettings], with this device's fallback applied. */
@Composable
fun rememberNaviampTypography(interfaceSettings: InterfaceSettings = InterfaceSettings()): Typography =
    rememberNaviampTypography(rememberNaviampFontSelection(interfaceSettings))

@Composable
internal fun rememberNaviampTypography(selection: InterfaceFontSelection): Typography {
    val fontFamily = naviampFontFamily(selection)
    // Inter's tabular digits keep times, progress and counters from shifting as they change.
    val featureSettings = if (selection.installedFamily == null && selection.family == InterfaceFontFamily.Inter) "tnum" else null
    return remember(fontFamily, featureSettings) {
        Typography().withFontFamily(fontFamily, featureSettings)
    }
}

@Composable
internal fun rememberNaviampFontSelection(interfaceSettings: InterfaceSettings): InterfaceFontSelection {
    val requested = interfaceSettings.installedFontFamily
    // Only the stored family is inspected here, so startup never scans every installed font.
    val installed = requested?.let { name -> remember(name) { naviampInstalledFonts()?.let { usableInstalledFamiliesMatching(it, name) } } }
    return remember(interfaceSettings.fontFamily, requested, installed) {
        interfaceSettings.resolvedFontSelection(installed)
    }
}

/** Installed families that can be chosen, listed off the UI thread; null until listed or where the host cannot list them. */
@Composable
internal fun rememberNaviampInstalledFontFamilies(): List<String>? {
    val fonts = naviampInstalledFonts() ?: return null
    val families by produceState<List<String>?>(null, fonts) {
        value = withContext(Dispatchers.Default) { usableInstalledFontFamilies(fonts) }
    }
    return families
}

internal fun presentableInstalledFontFamilies(names: List<String>): List<String> =
    names.map(String::trim)
        // Leading-dot families are private system aliases that are not meant to be chosen directly.
        .filter { it.isNotEmpty() && !it.startsWith(".") }
        .distinctBy(String::lowercase)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

internal fun usableInstalledFontFamilies(fonts: NaviampInstalledFonts): List<String> =
    presentableInstalledFontFamilies(fonts.familyNames()).filter { isUsableInstalledFont(fonts, it) }

internal fun usableInstalledFamiliesMatching(fonts: NaviampInstalledFonts, name: String): List<String> =
    presentableInstalledFontFamilies(fonts.familyNames())
        .filter { it.equals(name.trim(), ignoreCase = true) && isUsableInstalledFont(fonts, it) }

/**
 * Symbol and dingbat fonts would make the whole interface unreadable, so a family must draw basic
 * Latin letters and digits and, when its character map is readable, map them as Unicode text.
 */
internal fun isUsableInstalledFont(fonts: NaviampInstalledFonts, name: String): Boolean {
    if (!fonts.hasGlyphs(name, ReadableTextProbe)) return false
    val characterMap = fonts.characterMapTable(name) ?: return true
    return hasUnicodeCharacterMap(characterMap)
}

/** True unless a readable OpenType `cmap` lists only non-Unicode (for example Microsoft Symbol) subtables. */
internal fun hasUnicodeCharacterMap(table: ByteArray): Boolean {
    fun u16(offset: Int) = ((table[offset].toInt() and 0xFF) shl 8) or (table[offset + 1].toInt() and 0xFF)
    if (table.size < 4) return true
    val count = u16(2)
    if (table.size < 4 + count * 8) return true
    return (0 until count).any { index ->
        val platform = u16(4 + index * 8)
        val encoding = u16(6 + index * 8)
        platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10))
    }
}

private val ReadableTextProbe: IntArray =
    (('A'..'Z') + ('a'..'z') + ('0'..'9')).map { it.code }.toIntArray()

@Composable
private fun naviampFontFamily(selection: InterfaceFontSelection): FontFamily {
    selection.installedFamily?.let { name ->
        val fonts = naviampInstalledFonts()
        if (fonts != null) return remember(name) { fonts.fontFamily(name, NaviampFontWeights) }
    }
    return when (selection.family) {
        InterfaceFontFamily.NunitoSans -> FontFamily(NaviampFontWeights.map { Font(Res.font.nunito_sans, weight = it) })
        InterfaceFontFamily.Inter -> FontFamily(NaviampFontWeights.map { Font(Res.font.inter, weight = it) })
        InterfaceFontFamily.System -> remember { naviampSystemFontFamily(NaviampFontWeights) }
    }
}

/** Keeps a font picker readable even when the chosen font renders badly, such as a symbol font. */
@Composable
internal fun NaviampDefaultTypographyScope(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = rememberNaviampTypography(),
        content = content,
    )
}

/** Applies the chosen typography to surfaces composed outside the app shell, such as dialogs. */
@Composable
fun NaviampTypographyScope(interfaceSettings: InterfaceSettings, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = rememberNaviampTypography(interfaceSettings),
    ) {
        NaviampFontSizeScope(interfaceSettings.generalFontSize, includeStandardScale = true, content = content)
    }
}

/**
 * A standalone [TextStyle] replaces the theme style entirely, which would drop the chosen font.
 * This carries only the font, not the body text size or spacing.
 */
@Composable
@ReadOnlyComposable
internal fun TextStyle.withNaviampFont(): TextStyle {
    val theme = LocalTextStyle.current
    return copy(
        fontFamily = fontFamily ?: theme.fontFamily,
        fontFeatureSettings = fontFeatureSettings ?: theme.fontFeatureSettings,
    )
}

private val NaviampFontWeights =listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

private fun Typography.withFontFamily(fontFamily: FontFamily, featureSettings: String?): Typography = copy(
    displayLarge = displayLarge.withFontFamily(fontFamily, featureSettings),
    displayMedium = displayMedium.withFontFamily(fontFamily, featureSettings),
    displaySmall = displaySmall.withFontFamily(fontFamily, featureSettings),
    headlineLarge = headlineLarge.withFontFamily(fontFamily, featureSettings),
    headlineMedium = headlineMedium.withFontFamily(fontFamily, featureSettings),
    headlineSmall = headlineSmall.withFontFamily(fontFamily, featureSettings),
    titleLarge = titleLarge.withFontFamily(fontFamily, featureSettings),
    titleMedium = titleMedium.withFontFamily(fontFamily, featureSettings),
    titleSmall = titleSmall.withFontFamily(fontFamily, featureSettings),
    bodyLarge = bodyLarge.withFontFamily(fontFamily, featureSettings),
    bodyMedium = bodyMedium.withFontFamily(fontFamily, featureSettings),
    bodySmall = bodySmall.withFontFamily(fontFamily, featureSettings),
    labelLarge = labelLarge.withFontFamily(fontFamily, featureSettings),
    labelMedium = labelMedium.withFontFamily(fontFamily, featureSettings),
    labelSmall = labelSmall.withFontFamily(fontFamily, featureSettings),
)

private fun TextStyle.withFontFamily(fontFamily: FontFamily, featureSettings: String?): TextStyle =
    copy(fontFamily = fontFamily, fontFeatureSettings = featureSettings)

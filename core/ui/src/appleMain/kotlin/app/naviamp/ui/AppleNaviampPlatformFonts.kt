package app.naviamp.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** The Apple hosts do not enumerate installed families; a remembered one uses the shared fallback. */
internal actual fun naviampInstalledFonts(): NaviampInstalledFonts? = null

/** Compose resolves the generic sans-serif family to the Apple system UI font. */
internal actual fun naviampSystemFontFamily(weights: List<FontWeight>): FontFamily = FontFamily.SansSerif

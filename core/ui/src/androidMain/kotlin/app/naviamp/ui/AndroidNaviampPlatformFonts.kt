package app.naviamp.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Android exposes no installed-family catalog that Compose can load by family name. */
internal actual fun naviampInstalledFonts(): NaviampInstalledFonts? = null

/** Android's default typeface, which follows the device or manufacturer font style. */
internal actual fun naviampSystemFontFamily(weights: List<FontWeight>): FontFamily = FontFamily.Default

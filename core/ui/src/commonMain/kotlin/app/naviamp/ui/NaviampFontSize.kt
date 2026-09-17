package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.naviamp.domain.settings.InterfaceFontSize

internal const val NaviampStandardFontScale = 1.08f

internal fun naviampFontScaleMultiplier(
    fontSize: InterfaceFontSize,
    relativeTo: InterfaceFontSize? = null,
    includeStandardScale: Boolean = false,
): Float = fontSize.scale / (relativeTo?.scale ?: 1f) *
    if (includeStandardScale) NaviampStandardFontScale else 1f

/** Applies a font-size preference without changing physical layout density or system accessibility scaling. */
@Composable
internal fun NaviampFontSizeScope(
    fontSize: InterfaceFontSize,
    relativeTo: InterfaceFontSize? = null,
    includeStandardScale: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val multiplier = naviampFontScaleMultiplier(fontSize, relativeTo, includeStandardScale)
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * multiplier,
        ),
        content = content,
    )
}

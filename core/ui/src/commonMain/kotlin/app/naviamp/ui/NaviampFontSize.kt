package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.naviamp.domain.settings.InterfaceFontSize
import app.naviamp.ui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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

@Composable
internal fun InterfaceFontSize.label(): String = when (this) {
    InterfaceFontSize.Small -> stringResource(Res.string.settings_font_size_small)
    InterfaceFontSize.Standard -> stringResource(Res.string.settings_font_size_standard)
    InterfaceFontSize.Large -> stringResource(Res.string.settings_font_size_large)
}

@Composable
internal fun InterfaceFontSize.subtitle(): String = when (this) {
    InterfaceFontSize.Small -> stringResource(Res.string.settings_font_size_small_subtitle)
    InterfaceFontSize.Standard -> stringResource(Res.string.settings_font_size_standard_subtitle)
    InterfaceFontSize.Large -> stringResource(Res.string.settings_font_size_large_subtitle)
}

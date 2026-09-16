package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Native presentation boundary only: content, motion, actions, and accessibility remain in Core. */
internal interface NaviampAnimationSurface {
    @Composable
    fun Content(modifier: Modifier, content: @Composable () -> Unit)
}

internal val LocalNaviampAnimationSurface = staticCompositionLocalOf<NaviampAnimationSurface> {
    NaviampInlineAnimationSurface
}

internal object NaviampInlineAnimationSurface : NaviampAnimationSurface {
    @Composable
    override fun Content(modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier) { content() }
    }
}

/** A surface owns all nested animation; avoid allocating nested native surfaces. */
@Composable
internal fun NaviampAnimationRegion(modifier: Modifier, content: @Composable () -> Unit) {
    LocalNaviampAnimationSurface.current.Content(modifier) {
        CompositionLocalProvider(LocalNaviampAnimationSurface provides NaviampInlineAnimationSurface) {
            content()
        }
    }
}

package app.naviamp.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.settings_keep_screen_awake
import org.jetbrains.compose.resources.stringResource

/** Capability and native failure state for the currently mounted app surface. */
data class NaviampScreenAwakeUi(val available: Boolean = false, val failed: Boolean = false)
val LocalNaviampScreenAwakeUi = staticCompositionLocalOf { NaviampScreenAwakeUi() }

@Composable
fun naviampScreenAwakeReason(): String = stringResource(Res.string.settings_keep_screen_awake)

package app.naviamp.ios

import androidx.compose.ui.window.ComposeUIViewController
import app.naviamp.ui.NaviampBootstrapScreen
import platform.UIKit.UIViewController

/** Thin UIKit boundary around the shared Core-owned bootstrap surface. */
fun NaviampIosViewController(): UIViewController = ComposeUIViewController {
    NaviampBootstrapScreen()
}

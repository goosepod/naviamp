package app.naviamp.ui

import androidx.compose.runtime.Composable
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

@Composable
internal actual fun rememberNaviampPlatformReducedMotionSource(): NaviampReducedMotionSource? = AppleReducedMotionSource

private object AppleReducedMotionSource : NaviampReducedMotionSource {
    override val reduced: Boolean
        get() = UIAccessibilityIsReduceMotionEnabled()

    override fun observe(onChange: () -> Unit): () -> Unit {
        val center = NSNotificationCenter.defaultCenter
        val token = center.addObserverForName(
            UIAccessibilityReduceMotionStatusDidChangeNotification,
            null,
            NSOperationQueue.mainQueue,
        ) { _ -> onChange() }
        return { center.removeObserver(token) }
    }
}

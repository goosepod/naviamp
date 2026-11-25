package app.naviamp.ui

import androidx.compose.runtime.Composable

// The JVM has no portable reduce-motion API, so Desktop reports no preference.
@Composable
internal actual fun rememberNaviampPlatformReducedMotionSource(): NaviampReducedMotionSource? = null

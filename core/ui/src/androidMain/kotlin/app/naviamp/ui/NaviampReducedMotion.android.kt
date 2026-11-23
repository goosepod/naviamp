package app.naviamp.ui

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberNaviampPlatformReducedMotionSource(): NaviampReducedMotionSource? {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { AndroidReducedMotionSource(resolver) }
}

/** Android exposes reduced motion as the accessibility "Remove animations" animator scale of zero. */
private class AndroidReducedMotionSource(private val resolver: ContentResolver) : NaviampReducedMotionSource {
    override val reduced: Boolean
        get() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    override fun observe(onChange: () -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        return { resolver.unregisterContentObserver(observer) }
    }
}

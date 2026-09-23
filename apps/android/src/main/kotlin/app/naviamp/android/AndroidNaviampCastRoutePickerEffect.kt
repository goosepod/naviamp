package app.naviamp.android

import androidx.mediarouter.app.MediaRouteButton
import java.lang.ref.WeakReference

/** Invokes the Cast SDK's native picker through an attached Android media route button. */
object AndroidNaviampCastRoutePickerEffect {
    private var routeButton: WeakReference<MediaRouteButton>? = null

    fun attach(button: MediaRouteButton) {
        routeButton = WeakReference(button)
    }

    fun show() {
        routeButton?.get()?.takeIf { it.isAttachedToWindow }?.let { button ->
            button.post { button.performClick() }
        }
    }
}

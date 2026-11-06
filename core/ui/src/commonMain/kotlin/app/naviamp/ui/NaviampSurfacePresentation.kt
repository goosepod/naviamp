package app.naviamp.ui

/** Request a frame when committed content becomes visible, without a repaint timer. */
internal class NaviampSurfacePresentation {
    private var attached = false
    private var ready = false

    fun contentAttached(visible: Boolean): Boolean {
        attached = true
        return visibilityChanged(visible)
    }

    fun visibilityChanged(visible: Boolean): Boolean {
        val next = attached && visible
        val requestFrame = next && !ready
        ready = next
        return requestFrame
    }
}

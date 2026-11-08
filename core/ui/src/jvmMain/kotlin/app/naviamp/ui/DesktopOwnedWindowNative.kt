package app.naviamp.ui

import java.awt.Window

/** Skiko NSWindow handles and AppKit child-window attachment are macOS native operations. */
fun attachNaviampNativeOwnedWindow(window: Window, owner: Window) {
    if (!NaviampOwnedWindowPolicy.followsOwnerStack ||
        !System.getProperty("os.name").contains("Mac") ||
        !NativeMetalVisualizerHost.libraryAvailable()) return
    val childHandle = findSkiaLayer(window)?.windowHandle ?: return
    val ownerHandle = findSkiaLayer(owner)?.windowHandle ?: return
    if (childHandle != 0L && ownerHandle != 0L) {
        DesktopOwnedWindowNative.attach(childHandle, ownerHandle)
    }
}

private object DesktopOwnedWindowNative {
    external fun attach(child: Long, owner: Long)
}

package app.naviamp.desktop.platform

import app.naviamp.app.NaviampScreenAwakeEffect
import app.naviamp.app.NaviampScreenAwakeLease
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/** Reports native API availability only; lifecycle, settings, and failure policy belong to Core. */
fun desktopScreenAwakeEffect(): NaviampScreenAwakeEffect? = runCatching {
    when {
        Platform.isWindows() -> WindowsScreenAwakeEffect(Native.load("kernel32", WindowsPowerRequests::class.java))
        Platform.isMac() -> MacScreenAwakeEffect(Native.load("IOKit", MacDisplayAssertions::class.java))
        Platform.isLinux() && !System.getenv("DISPLAY").isNullOrBlank() &&
            System.getenv("WAYLAND_DISPLAY").isNullOrBlank() && System.getenv("XDG_SESSION_TYPE") != "wayland" -> {
            val effect = X11ScreenAwakeEffect(X11.INSTANCE, Native.load("Xss", X11ScreenSaver::class.java))
            effect.takeIf { it.available() }
        }
        else -> null
    }
}.getOrNull()

/** Handle-based Win32 requests avoid SetThreadExecutionState's thread-lifetime dependency. */
private class WindowsScreenAwakeEffect(private val native: WindowsPowerRequests) : NaviampScreenAwakeEffect {
    override fun acquire(reason: String): NaviampScreenAwakeLease? {
        val text = Memory((reason.length + 1L) * Native.WCHAR_SIZE).apply { setWideString(0, reason) }
        // REASON_CONTEXT: two DWORDs and the union's largest (Detailed) member, on both Win32/Win64.
        val context = Memory(16L + 2L * Native.POINTER_SIZE).apply {
            clear()
            setInt(0, 0) // POWER_REQUEST_CONTEXT_VERSION
            setInt(4, 1) // POWER_REQUEST_CONTEXT_SIMPLE_STRING
            setPointer(8, text)
        }
        var handle: HANDLE? = null
        var retained = false
        try {
            handle = native.PowerCreateRequest(context)
            if (handle == null || Pointer.nativeValue(handle.pointer) == -1L) return null
            // Windows requires the system request alongside the display request for this scenario.
            if (!native.PowerSetRequest(handle, 0) || !native.PowerSetRequest(handle, 1)) return null
            val owned = handle
            var released = false
            retained = true
            return NaviampScreenAwakeLease {
                if (!released) {
                    native.PowerClearRequest(owned, 0)
                    native.PowerClearRequest(owned, 1)
                    check(native.CloseHandle(owned))
                    released = true
                    context.close()
                    text.close()
                }
            }
        } finally {
            if (!retained) {
                handle?.takeIf { Pointer.nativeValue(it.pointer) != -1L }?.let { native.CloseHandle(it) }
                context.close()
                text.close()
            }
        }
    }
}

private interface WindowsPowerRequests : StdCallLibrary {
    fun PowerCreateRequest(context: Pointer): HANDLE?
    fun PowerSetRequest(handle: HANDLE, requestType: Int): Boolean
    fun PowerClearRequest(handle: HANDLE, requestType: Int): Boolean
    fun CloseHandle(handle: HANDLE): Boolean
}

/** IOKit owns a process-scoped assertion ID; releasing it does not alter system power preferences. */
private class MacScreenAwakeEffect(private val native: MacDisplayAssertions) : NaviampScreenAwakeEffect {
    override fun acquire(reason: String): NaviampScreenAwakeLease? {
        val type = CFStringRef.createCFString("PreventUserIdleDisplaySleep")
        val name = CFStringRef.createCFString(reason)
        val id = IntByReference()
        try {
            if (native.IOPMAssertionCreateWithName(type, 255, name, id) != 0) return null
        } finally {
            CoreFoundation.INSTANCE.CFRelease(name)
            CoreFoundation.INSTANCE.CFRelease(type)
        }
        var released = false
        return NaviampScreenAwakeLease {
            if (!released) {
                check(native.IOPMAssertionRelease(id.value) == 0)
                released = true
            }
        }
    }
}

private interface MacDisplayAssertions : Library {
    fun IOPMAssertionCreateWithName(type: CFStringRef, level: Int, name: CFStringRef, id: IntByReference): Int
    fun IOPMAssertionRelease(id: Int): Int
}

/** Xss 1.1 suspension belongs to its X connection and is removed automatically on disconnect. */
private class X11ScreenAwakeEffect(private val x11: X11, private val native: X11ScreenSaver) : NaviampScreenAwakeEffect {
    fun available(): Boolean {
        val display = x11.XOpenDisplay(null) ?: return false
        return try { supportsSuspend(display) } finally { x11.XCloseDisplay(display) }
    }

    override fun acquire(reason: String): NaviampScreenAwakeLease? {
        val display = x11.XOpenDisplay(null) ?: return null
        var retained = false
        try {
            if (!supportsSuspend(display)) return null
            native.XScreenSaverSuspend(display, 1)
            x11.XFlush(display)
            var released = false
            retained = true
            return NaviampScreenAwakeLease {
                if (!released) {
                    native.XScreenSaverSuspend(display, 0)
                    x11.XFlush(display)
                    x11.XCloseDisplay(display)
                    released = true
                }
            }
        } finally {
            if (!retained) x11.XCloseDisplay(display)
        }
    }

    private fun supportsSuspend(display: X11.Display): Boolean {
        val major = IntByReference()
        val minor = IntByReference()
        return native.XScreenSaverQueryVersion(display, major, minor) != 0 &&
            (major.value > 1 || major.value == 1 && minor.value >= 1)
    }
}

private interface X11ScreenSaver : Library {
    fun XScreenSaverQueryVersion(display: X11.Display, major: IntByReference, minor: IntByReference): Int
    fun XScreenSaverSuspend(display: X11.Display, suspend: Int)
}

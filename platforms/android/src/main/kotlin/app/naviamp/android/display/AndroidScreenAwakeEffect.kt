package app.naviamp.android

import android.os.Looper
import android.view.Window
import android.view.WindowManager
import app.naviamp.app.NaviampScreenAwakeEffect
import app.naviamp.app.NaviampScreenAwakeLease

/** The Android Window flag is scoped to this Activity; Core supplies visibility and user policy. */
class AndroidScreenAwakeEffect(private val window: Window) : NaviampScreenAwakeEffect {
    override fun acquire(reason: String): NaviampScreenAwakeLease {
        check(Looper.myLooper() == Looper.getMainLooper())
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        val alreadyHeld = window.attributes.flags and flag != 0
        window.addFlags(flag)
        var released = false
        return NaviampScreenAwakeLease {
            check(Looper.myLooper() == Looper.getMainLooper())
            if (!released) {
                if (!alreadyHeld) window.clearFlags(flag)
                released = true
            }
        }
    }
}

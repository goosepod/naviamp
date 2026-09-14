package app.naviamp.ios

import app.naviamp.app.NaviampScreenAwakeEffect
import app.naviamp.app.NaviampScreenAwakeLease
import platform.UIKit.UIApplication

/** UIKit's application idle timer is the only native operation; Core supplies visible-surface policy. */
class IosScreenAwakeEffect : NaviampScreenAwakeEffect {
    override fun acquire(reason: String): NaviampScreenAwakeLease {
        val application = UIApplication.sharedApplication
        val previouslyDisabled = application.idleTimerDisabled
        application.idleTimerDisabled = true
        var released = false
        return NaviampScreenAwakeLease {
            if (!released) {
                application.idleTimerDisabled = previouslyDisabled
                released = true
            }
        }
    }
}

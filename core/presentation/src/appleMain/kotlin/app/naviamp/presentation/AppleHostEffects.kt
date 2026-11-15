package app.naviamp.presentation

import app.naviamp.app.NaviampScreenAwakeEffect
import app.naviamp.app.NaviampScreenAwakeLease
import app.naviamp.domain.home.HomeDate
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** Foundation's current calendar is the native source of the local date and hour. */
val AppleHomeDateSource = NaviampCoreHomeDateSource {
    val now = NSDate()
    val calendar = NSCalendar.currentCalendar
    HomeDate(calendar.component(NSCalendarUnitYear, now).toInt(),
        calendar.ordinalityOfUnit(NSCalendarUnitDay, NSCalendarUnitYear, now).toInt(),
        calendar.component(NSCalendarUnitHour, now).toInt())
}

/** Executes a Core-approved URI through UIKit. */
class AppleCoreExternalUriPort : NaviampCoreExternalUriPort {
    override fun open(uri: String) { NSURL.URLWithString(uri)?.let(UIApplication.sharedApplication::openURL) }
}

/** Core decides when a visible surface needs a lease; UIKit only changes its native idle timer. */
class AppleScreenAwakeEffect : NaviampScreenAwakeEffect {
    override fun acquire(reason: String): NaviampScreenAwakeLease {
        val application = UIApplication.sharedApplication
        val previous = application.idleTimerDisabled
        application.idleTimerDisabled = true
        var released = false
        return NaviampScreenAwakeLease {
            if (!released) { application.idleTimerDisabled = previous; released = true }
        }
    }
}

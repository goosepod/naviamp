package app.naviamp.ios.platform

import app.naviamp.domain.home.HomeDate
import app.naviamp.presentation.NaviampCoreHomeDateSource
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

/** Maps Apple's local calendar into Core's platform-neutral Home date value. */
val IosHomeDateSource = NaviampCoreHomeDateSource {
    val now = NSDate()
    val calendar = NSCalendar.currentCalendar
    HomeDate(
        year = calendar.component(NSCalendarUnitYear, fromDate = now).toInt(),
        dayOfYear = calendar.ordinalityOfUnit(
            smaller = NSCalendarUnitDay,
            inUnit = NSCalendarUnitYear,
            forDate = now,
        ).toInt(),
        hourOfDay = calendar.component(NSCalendarUnitHour, fromDate = now).toInt(),
    )
}

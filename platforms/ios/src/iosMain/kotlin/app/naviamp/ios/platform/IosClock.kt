package app.naviamp.ios.platform

import app.naviamp.domain.home.HomeDate
import app.naviamp.presentation.NaviampCoreHomeDateSource
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object IosClock {
    fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

    fun nowIso8601(): String = Clock.System.now().toString()

    val homeDate = NaviampCoreHomeDateSource {
        val now = NSDate()
        val calendar = NSCalendar.currentCalendar
        HomeDate(
            year = calendar.component(NSCalendarUnitYear, fromDate = now).toInt(),
            dayOfYear = calendar.ordinalityOfUnit(
                smaller = NSCalendarUnitDay,
                inUnit = NSCalendarUnitYear,
                forDate = now,
            ).toInt(),
        )
    }
}

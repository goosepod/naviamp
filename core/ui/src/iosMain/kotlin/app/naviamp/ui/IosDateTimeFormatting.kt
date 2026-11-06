package app.naviamp.ui

import androidx.compose.runtime.Composable
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone
import platform.Foundation.timeZoneWithName

/** Foundation owns the user's regional calendar, date format and hour-cycle preference. */
@Composable
internal actual fun formatNaviampDateTime(epochMillis: Long, includeTime: Boolean, timeZoneId: String?): String {
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterMediumStyle
    formatter.timeStyle = if (includeTime) NSDateFormatterShortStyle else NSDateFormatterNoStyle
    formatter.timeZone = timeZoneId?.let { NSTimeZone.timeZoneWithName(it) } ?: NSTimeZone.localTimeZone
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}

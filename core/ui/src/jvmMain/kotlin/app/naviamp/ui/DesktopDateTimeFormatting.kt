package app.naviamp.ui

import androidx.compose.runtime.Composable
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** JVM locale, calendar and system time-zone integration. */
@Composable
internal actual fun formatNaviampDateTime(epochMillis: Long, includeTime: Boolean, timeZoneId: String?): String {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    val formatter = if (includeTime) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        else DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    formatter.timeZone = timeZoneId?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()
    return formatter.format(Date(epochMillis))
}

package app.naviamp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import java.util.Date
import java.util.TimeZone

/** Android's regional date format and the user's system 12/24-hour preference. */
@Composable
internal actual fun formatNaviampDateTime(epochMillis: Long, includeTime: Boolean, timeZoneId: String?): String {
    val context = LocalContext.current
    val zone = timeZoneId?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()
    val date = Date(epochMillis)
    val day = DateFormat.getMediumDateFormat(context).apply { timeZone = zone }.format(date)
    if (!includeTime) return day
    val time = DateFormat.getTimeFormat(context).apply { timeZone = zone }.format(date)
    return "$day $time"
}

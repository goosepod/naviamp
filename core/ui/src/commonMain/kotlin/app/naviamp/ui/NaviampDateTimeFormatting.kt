package app.naviamp.ui

import androidx.compose.runtime.Composable
import kotlin.time.Instant

/** Native formatting only: the caller owns parsing, precision and date-versus-instant policy. */
@Composable
internal expect fun formatNaviampDateTime(epochMillis: Long, includeTime: Boolean, timeZoneId: String?): String

@Composable
internal fun discoveryTimestamp(value: String?): String? {
    val instant = value?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    return formatNaviampDateTime(instant.toEpochMilliseconds(), includeTime = true, timeZoneId = null)
}

/** A release calendar date must not move to the previous day in a western time zone. */
@Composable
internal fun discoveryCalendarDate(value: String?): String? {
    if (value == null || !value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return value
    val instant = runCatching { Instant.parse(value + "T00:00:00Z") }.getOrNull() ?: return value
    return formatNaviampDateTime(instant.toEpochMilliseconds(), includeTime = false, timeZoneId = "UTC")
}

package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import java.util.Locale
import java.util.TimeZone
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDateTimeFormattingTest {
    @Test fun americanDatesUseLocalTimeWhileCalendarDatesNeverShift() = withRegion(Locale.US, "America/Los_Angeles") {
        runComposeUiTest {
            var stamp: String? = null; var day: String? = null; var rollover: String? = null
            setContent {
                stamp = discoveryTimestamp("2026-11-05T16:29:29.854Z")
                day = discoveryCalendarDate("2026-11-05")
                rollover = discoveryTimestamp("2026-11-05T00:30:00Z")
                Column { Text(stamp.orEmpty()); Text(day.orEmpty()); Text(rollover.orEmpty()) }
            }
            runOnIdle {
                assertEquals("Nov 5, 2026, 8:29 AM", stamp?.replace('\u202f', ' '))
                assertEquals("Nov 5, 2026", day)
                assertEquals("Nov 4, 2026, 4:30 PM", rollover?.replace('\u202f', ' '))
            }
        }
    }
    @Test fun germanDatesUseRegionalOrderAndTwentyFourHourTime() = withRegion(Locale.GERMANY, "Europe/Berlin") {
        runComposeUiTest {
            var stamp: String? = null
            setContent { stamp = discoveryTimestamp("2026-11-05T16:29:29.854Z"); Text(stamp.orEmpty()) }
            runOnIdle { assertEquals("05.11.2026, 17:29", stamp) }
        }
    }
    @Test fun invalidTimestampsAreNotExposedAndPartialReleasePrecisionIsRetained() = runComposeUiTest {
        var invalid: String? = "pending"; var year: String? = null; var month: String? = null
        setContent {
            invalid = discoveryTimestamp("unparseable")
            year = discoveryCalendarDate("2027")
            month = discoveryCalendarDate("2027-03")
        }
        runOnIdle { assertNull(invalid); assertEquals("2027", year); assertEquals("2027-03", month) }
    }
    private fun withRegion(locale: Locale, zone: String, block: () -> Unit) {
        val original = Locale.getDefault(); val originalZone = TimeZone.getDefault()
        try { Locale.setDefault(locale); TimeZone.setDefault(TimeZone.getTimeZone(zone)); block() }
        finally { Locale.setDefault(original); TimeZone.setDefault(originalZone) }
    }
}

internal fun calendarDateExpectation(value: String): String = java.text.DateFormat.getDateInstance(
    java.text.DateFormat.MEDIUM, java.util.Locale.getDefault(java.util.Locale.Category.FORMAT)).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date.from(java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()))

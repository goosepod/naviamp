package app.naviamp.domain.smartplaylist

import kotlin.test.*

class SmartPlaylistCalendarDateTest {
    @Test fun calendarDatesRoundTripWithoutTimezoneOffsets() {
        assertEquals(0L, smartPlaylistDateToUtcMillis("1970-01-01"))
        assertEquals(-86_400_000L, smartPlaylistDateToUtcMillis("1969-12-31"))
        assertEquals(951782400000L, smartPlaylistDateToUtcMillis("2000-02-29"))
        for (year in listOf(1, 99, 400, 1600, 1900, 1969, 1970, 2000, 2024, 2100, 9999)) {
            for (month in 1..12) for (day in 1..31) {
                val date = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
                val millis = smartPlaylistDateToUtcMillis(date) ?: continue
                assertEquals(date, smartPlaylistDateFromUtcMillis(millis))
                assertEquals(date, smartPlaylistDateFromUtcMillis(millis + 86_399_999))
            }
        }
        assertEquals("1969-12-31", smartPlaylistDateFromUtcMillis(-1))
    }
    @Test fun invalidDatesAndOutOfRangeTimestampsAreRejected() {
        for (date in listOf("", "2025-02-29", "1900-02-29", "2024-13-01", "01/02/2024", "0000-01-01")) {
            assertNull(smartPlaylistDateToUtcMillis(date), date)
        }
        assertNull(smartPlaylistDateFromUtcMillis(Long.MIN_VALUE))
        assertNull(smartPlaylistDateFromUtcMillis(Long.MAX_VALUE))
        assertNull(smartPlaylistDateFromUtcMillis(smartPlaylistDateToUtcMillis("0001-01-01")!! - 1))
        assertNull(smartPlaylistDateFromUtcMillis(smartPlaylistDateToUtcMillis("9999-12-31")!! + 86_400_000))
    }
    @Test fun onlyAbsoluteDateOperandsUseCalendars() {
        SmartPlaylistFieldCatalog.fields.filter { it.valueType == SmartPlaylistValueType.Date }.forEach { field ->
            field.operators.forEach { operator ->
                assertEquals(operator == SmartPlaylistOperator.Before || operator == SmartPlaylistOperator.After,
                    SmartPlaylistConditionDraft(field = field, operator = operator).usesCalendarDate())
            }
        }
        assertFalse(SmartPlaylistConditionDraft().usesCalendarDate())
    }
}

package app.naviamp.domain.smartplaylist

private const val CalendarMillisPerDay = 86_400_000L
private const val DaysBeforeUnixEpoch = 719162L

/** Calendar selectors use UTC midnight; date operands must never shift with the device zone. */
fun smartPlaylistDateToUtcMillis(value: String): Long? {
    val text = value.trim()
    if (!isSmartPlaylistCalendarDate(text)) return null
    val year = text.substring(0, 4).toInt()
    val month = text.substring(5, 7).toInt()
    val day = text.substring(8, 10).toInt()
    val days = daysBeforeYear(year) + (1 until month).sumOf { daysInMonth(year, it) } + day - 1
    return (days - DaysBeforeUnixEpoch) * CalendarMillisPerDay
}

fun smartPlaylistDateFromUtcMillis(millis: Long): String? {
    val days = millis.floorDiv(CalendarMillisPerDay) + DaysBeforeUnixEpoch
    if (days < 0 || days >= daysBeforeYear(10000)) return null
    var low = 1
    var high = 10000
    while (low + 1 < high) {
        val middle = (low + high) / 2
        if (daysBeforeYear(middle) <= days) low = middle else high = middle
    }
    val year = low
    var day = (days - daysBeforeYear(year)).toInt()
    var month = 1
    while (day >= daysInMonth(year, month)) {
        day -= daysInMonth(year, month)
        month++
    }
    return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${(day + 1).toString().padStart(2, '0')}"
}

private fun daysBeforeYear(year: Int): Long {
    val previous = year - 1L
    return previous * 365 + previous / 4 - previous / 100 + previous / 400
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

fun SmartPlaylistConditionDraft.usesCalendarDate(): Boolean = field.valueType == SmartPlaylistValueType.Date &&
    operator !in listOf(SmartPlaylistOperator.InTheLast, SmartPlaylistOperator.NotInTheLast,
        SmartPlaylistOperator.IsMissing, SmartPlaylistOperator.IsPresent)

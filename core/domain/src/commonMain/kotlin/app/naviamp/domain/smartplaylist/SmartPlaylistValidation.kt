package app.naviamp.domain.smartplaylist

/** Stable draft identities keep findings attached when rows are edited or moved. */
data class SmartPlaylistIssueLocation(val conditionId: Long? = null, val groupId: Long? = null)

enum class SmartPlaylistIssueSeverity { Error, Warning }

enum class SmartPlaylistIssueCode {
    RequiredValue, InvalidNumber, InvalidBoolean, InvalidDate, InvalidDays,
    ReversedRange, OutOfBounds, InvalidChoice, UnsupportedOperator,
    EmptyGroup, NoRules, RequiredName, InvalidLimit, InvalidRefreshDelay, InvalidSort,
}

data class SmartPlaylistValidationIssue(
    val code: SmartPlaylistIssueCode,
    val location: SmartPlaylistIssueLocation = SmartPlaylistIssueLocation(),
    val severity: SmartPlaylistIssueSeverity = SmartPlaylistIssueSeverity.Error,
)

class SmartPlaylistValidationException(val issues: List<SmartPlaylistValidationIssue>) :
    IllegalArgumentException(issues.joinToString { it.code.name })

data class SmartPlaylistFieldConstraints(
    val minimum: Double? = null,
    val maximum: Double? = null,
    val textValues: Set<String>? = null,
    // Thresholds outside a count's value domain can still be useful: playcount > -1.
    val allowOutsideDomainThresholds: Boolean = false,
)

internal fun smartPlaylistFieldConstraints(field: String): SmartPlaylistFieldConstraints = when (field) {
    SmartPlaylistFields.Rating, SmartPlaylistFields.AverageRating,
    SmartPlaylistFields.AlbumRating, SmartPlaylistFields.ArtistRating ->
        SmartPlaylistFieldConstraints(minimum = 0.0, maximum = 5.0)
    SmartPlaylistFields.PlayCount, SmartPlaylistFields.AlbumPlayCount,
    SmartPlaylistFields.ArtistPlayCount, SmartPlaylistFields.Size,
    SmartPlaylistFields.AlbumSize, SmartPlaylistFields.Duration,
    SmartPlaylistFields.AlbumDuration, SmartPlaylistFields.AlbumSongCount ->
        SmartPlaylistFieldConstraints(minimum = 0.0, allowOutsideDomainThresholds = true)
    SmartPlaylistFields.ExplicitStatus -> SmartPlaylistFieldConstraints(textValues = setOf("", "c", "e"))
    else -> SmartPlaylistFieldConstraints()
}

fun SmartPlaylistDraft.validate(): List<SmartPlaylistValidationIssue> = buildList {
    if (name.isBlank()) add(SmartPlaylistValidationIssue(SmartPlaylistIssueCode.RequiredName))
    var activeRules = 0
    conditions.filterNot { it.isUntouchedStarter }.forEach { condition ->
        activeRules++
        addAll(condition.validate())
    }
    groups.forEach { group ->
        activeRules++
        if (group.conditions.isEmpty()) add(SmartPlaylistValidationIssue(
            SmartPlaylistIssueCode.EmptyGroup, SmartPlaylistIssueLocation(groupId = group.id),
        ))
        group.conditions.forEach { addAll(it.validate(group.id)) }
    }
    if (activeRules == 0) add(SmartPlaylistValidationIssue(SmartPlaylistIssueCode.NoRules))
    if (limitMode != SmartPlaylistLimitMode.Unlimited &&
        (limit <= 0 || (limitMode == SmartPlaylistLimitMode.Percent && limit > 100))) {
        add(SmartPlaylistValidationIssue(SmartPlaylistIssueCode.InvalidLimit))
    }
    if (refreshDelay.isNotBlank() && !SmartPlaylistRefreshDelay.isValid(refreshDelay.trim())) {
        add(SmartPlaylistValidationIssue(SmartPlaylistIssueCode.InvalidRefreshDelay))
    }
    if (sort.any { !it.field.sortable }) add(SmartPlaylistValidationIssue(SmartPlaylistIssueCode.InvalidSort))
}

fun SmartPlaylistConditionDraft.validate(groupId: Long? = null): List<SmartPlaylistValidationIssue> {
    val location = SmartPlaylistIssueLocation(conditionId = id, groupId = groupId)
    fun issue(code: SmartPlaylistIssueCode) = listOf(SmartPlaylistValidationIssue(code, location))
    if (operator !in field.operators) return issue(SmartPlaylistIssueCode.UnsupportedOperator)
    if (operator == SmartPlaylistOperator.IsMissing || operator == SmartPlaylistOperator.IsPresent) {
        return if (value.isBlank() || value.trim().lowercase() in setOf("true", "false")) emptyList()
        else issue(SmartPlaylistIssueCode.InvalidBoolean)
    }
    val values = if (operator == SmartPlaylistOperator.InTheRange) listOf(value, secondValue) else listOf(value)
    values.forEach { raw ->
        val text = raw.trim()
        if (text.isEmpty() && text !in field.constraints.textValues.orEmpty()) {
            return issue(SmartPlaylistIssueCode.RequiredValue)
        }
        if (operator == SmartPlaylistOperator.InTheLast || operator == SmartPlaylistOperator.NotInTheLast) {
            if (text.toIntOrNull()?.let { it > 0 } != true) return issue(SmartPlaylistIssueCode.InvalidDays)
            return@forEach
        }
        when (field.valueType) {
            SmartPlaylistValueType.Date -> if (!isSmartPlaylistCalendarDate(text)) {
                return issue(SmartPlaylistIssueCode.InvalidDate)
            }
            SmartPlaylistValueType.Integer, SmartPlaylistValueType.Decimal -> {
                val number = when (field.valueType) {
                    SmartPlaylistValueType.Integer -> text.toLongOrNull()?.toDouble()
                    else -> text.toDoubleOrNull()?.takeIf { it.isFinite() }
                } ?: return issue(SmartPlaylistIssueCode.InvalidNumber)
                val constraints = field.constraints
                val threshold = operator == SmartPlaylistOperator.GreaterThan || operator == SmartPlaylistOperator.LessThan
                if (!(constraints.allowOutsideDomainThresholds && threshold) &&
                    (constraints.minimum?.let { number < it } == true || constraints.maximum?.let { number > it } == true)) {
                    return issue(SmartPlaylistIssueCode.OutOfBounds)
                }
            }
            SmartPlaylistValueType.Boolean -> if (field.parseValue(text) == null) {
                return issue(SmartPlaylistIssueCode.InvalidBoolean)
            }
            SmartPlaylistValueType.Days -> if (text.toIntOrNull()?.let { it > 0 } != true) {
                return issue(SmartPlaylistIssueCode.InvalidDays)
            }
            else -> Unit
        }
        if (field.constraints.textValues?.let { text !in it } == true) {
            return issue(SmartPlaylistIssueCode.InvalidChoice)
        }
    }
    if (operator == SmartPlaylistOperator.InTheRange) {
        val reversed = when (field.valueType) {
            SmartPlaylistValueType.Integer -> value.trim().toLong() > secondValue.trim().toLong()
            SmartPlaylistValueType.Decimal -> value.trim().toDouble() > secondValue.trim().toDouble()
            SmartPlaylistValueType.Date -> value.trim() > secondValue.trim()
            else -> false
        }
        if (reversed) return issue(SmartPlaylistIssueCode.ReversedRange)
    }
    return emptyList()
}

/** Navidrome date operands use Gregorian YYYY-MM-DD, not arbitrary date text. */
internal fun isSmartPlaylistCalendarDate(text: String): Boolean {
    if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text)) return false
    val year = text.substring(0, 4).toInt()
    val month = text.substring(5, 7).toInt()
    val day = text.substring(8, 10).toInt()
    if (year < 1 || month !in 1..12) return false
    val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val days = when (month) {
        2 -> if (leap) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..days
}

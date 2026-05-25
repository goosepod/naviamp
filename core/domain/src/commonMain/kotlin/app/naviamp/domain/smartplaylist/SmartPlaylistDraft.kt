package app.naviamp.domain.smartplaylist

data class SmartPlaylistDraft(
    val name: String = "New Smart Playlist",
    val comment: String = "",
    val match: SmartPlaylistMatch = SmartPlaylistMatch.All,
    val conditions: List<SmartPlaylistConditionDraft> = listOf(SmartPlaylistConditionDraft()),
    val sort: List<SmartPlaylistSortDraft> = listOf(SmartPlaylistSortDraft()),
    val limitMode: SmartPlaylistLimitMode = SmartPlaylistLimitMode.TrackCount,
    val limit: Int = 100,
    val isPublic: Boolean = false,
) {
    fun toDefinition(): SmartPlaylistDefinition {
        val validConditions = conditions.mapNotNull { it.toConditionOrNull() }
        require(validConditions.isNotEmpty()) { "Add at least one complete rule." }

        return SmartPlaylistDefinition(
            name = name,
            comment = comment,
            match = match,
            rules = validConditions,
            sort = sort.mapNotNull { it.toSortOrNull() },
            limit = limit.takeIf { limitMode == SmartPlaylistLimitMode.TrackCount },
            limitPercent = limit.takeIf { limitMode == SmartPlaylistLimitMode.Percent },
            isPublic = isPublic,
        )
    }
}

data class SmartPlaylistConditionDraft(
    val field: SmartPlaylistFieldOption = SmartPlaylistFieldCatalog.defaultField,
    val operator: SmartPlaylistOperator = SmartPlaylistFieldCatalog.defaultField.operators.first(),
    val value: String = "",
    val secondValue: String = "",
) {
    fun toConditionOrNull(): SmartPlaylistCondition? {
        val parsedValue = parseValue() ?: return null
        return SmartPlaylistCondition(
            operator = operator,
            field = field.field,
            value = parsedValue,
        )
    }

    private fun parseValue(): SmartPlaylistValue? {
        val first = value.trim()
        val second = secondValue.trim()
        if (operator == SmartPlaylistOperator.InTheRange) {
            val start = field.parseValue(first) ?: return null
            val end = field.parseValue(second) ?: return null
            return SmartPlaylistValue.Range(start, end)
        }
        return field.parseValue(first)
    }
}

data class SmartPlaylistSortDraft(
    val field: SmartPlaylistFieldOption = SmartPlaylistFieldCatalog.sortableFields.first(),
    val descending: Boolean = true,
) {
    fun toSortOrNull(): SmartPlaylistSort? =
        field.takeIf { it.sortable }?.let { SmartPlaylistSort(it.field, descending) }
}

enum class SmartPlaylistLimitMode {
    TrackCount,
    Percent,
}

data class SmartPlaylistFieldOption(
    val field: String,
    val label: String,
    val valueType: SmartPlaylistValueType,
    val operators: List<SmartPlaylistOperator>,
    val sortable: Boolean = true,
) {
    fun parseValue(rawValue: String): SmartPlaylistValue? {
        if (rawValue.isBlank()) return null
        return when (valueType) {
            SmartPlaylistValueType.Text -> SmartPlaylistValue.Text(rawValue)
            SmartPlaylistValueType.Integer -> rawValue.toIntOrNull()?.let { SmartPlaylistValue.Number(it) }
            SmartPlaylistValueType.Decimal -> rawValue.toDoubleOrNull()?.let { SmartPlaylistValue.Decimal(it) }
            SmartPlaylistValueType.Boolean -> parseBoolean(rawValue)?.let { SmartPlaylistValue.Flag(it) }
            SmartPlaylistValueType.Days -> rawValue.toIntOrNull()?.takeIf { it > 0 }?.let { SmartPlaylistValue.Number(it) }
            SmartPlaylistValueType.Date -> SmartPlaylistValue.Text(rawValue)
            SmartPlaylistValueType.PlaylistId -> SmartPlaylistValue.Text(rawValue)
        }
    }

    private fun parseBoolean(rawValue: String): Boolean? =
        when (rawValue.trim().lowercase()) {
            "true", "yes", "y", "1", "loved", "favorite", "favorited" -> true
            "false", "no", "n", "0", "unloved", "not favorite", "not favorited" -> false
            else -> null
        }
}

enum class SmartPlaylistValueType {
    Text,
    Integer,
    Decimal,
    Boolean,
    Days,
    Date,
    PlaylistId,
}

object SmartPlaylistFieldCatalog {
    val textOperators = listOf(
        SmartPlaylistOperator.Is,
        SmartPlaylistOperator.IsNot,
        SmartPlaylistOperator.Contains,
        SmartPlaylistOperator.NotContains,
        SmartPlaylistOperator.StartsWith,
        SmartPlaylistOperator.EndsWith,
    )

    val comparableOperators = listOf(
        SmartPlaylistOperator.Is,
        SmartPlaylistOperator.IsNot,
        SmartPlaylistOperator.GreaterThan,
        SmartPlaylistOperator.LessThan,
        SmartPlaylistOperator.InTheRange,
    )

    val dateOperators = listOf(
        SmartPlaylistOperator.Before,
        SmartPlaylistOperator.After,
        SmartPlaylistOperator.InTheLast,
        SmartPlaylistOperator.NotInTheLast,
    )

    val fields = listOf(
        SmartPlaylistFieldOption(SmartPlaylistFields.Title, "Title", SmartPlaylistValueType.Text, textOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Artist, "Artist", SmartPlaylistValueType.Text, textOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Album, "Album", SmartPlaylistValueType.Text, textOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Genre, "Genre", SmartPlaylistValueType.Text, textOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Year, "Year", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Rating, "Rating", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.PlayCount, "Play Count", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Bitrate, "Bitrate", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Codec, "Codec", SmartPlaylistValueType.Text, textOperators),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.Loved,
            "Favorite",
            SmartPlaylistValueType.Boolean,
            listOf(SmartPlaylistOperator.Is, SmartPlaylistOperator.IsNot),
        ),
        SmartPlaylistFieldOption(SmartPlaylistFields.DateLoved, "Date Favorited", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.DateAdded, "Date Added", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.LastPlayed, "Last Played", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(
            "playlist",
            "Playlist",
            SmartPlaylistValueType.PlaylistId,
            listOf(SmartPlaylistOperator.InPlaylist, SmartPlaylistOperator.NotInPlaylist),
            sortable = false,
        ),
    )

    val defaultField: SmartPlaylistFieldOption = fields.first { it.field == SmartPlaylistFields.Artist }

    val sortableFields: List<SmartPlaylistFieldOption> =
        fields.filter { it.sortable } + SmartPlaylistFieldOption(
            SmartPlaylistFields.Random,
            "Random",
            SmartPlaylistValueType.Text,
            emptyList(),
        )
}

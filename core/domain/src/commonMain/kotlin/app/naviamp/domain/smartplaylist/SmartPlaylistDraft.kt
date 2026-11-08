package app.naviamp.domain.smartplaylist

import kotlin.random.Random

data class SmartPlaylistDraft(
    val name: String = "New Smart Playlist",
    val comment: String = "",
    val match: SmartPlaylistMatch = SmartPlaylistMatch.All,
    val conditions: List<SmartPlaylistConditionDraft> = listOf(SmartPlaylistConditionDraft(isStarter = true)),
    val groups: List<SmartPlaylistGroupDraft> = emptyList(),
    val sort: List<SmartPlaylistSortDraft> = listOf(SmartPlaylistSortDraft()),
    val limitMode: SmartPlaylistLimitMode = SmartPlaylistLimitMode.TrackCount,
    val limit: Int = 100,
    val isPublic: Boolean = false,
    val selectedLibraryIds: List<String>? = null,
    val refreshDelay: String = "",
) {
    fun toDefinition(): SmartPlaylistDefinition {
        val issues = validate().filter { it.severity == SmartPlaylistIssueSeverity.Error }
        if (issues.isNotEmpty()) throw SmartPlaylistValidationException(issues)
        val validConditions = conditions.filterNot { it.isUntouchedStarter }.map { requireNotNull(it.toRuleOrNull()) }
        val validGroups = groups.map { requireNotNull(it.toRuleOrNull()) }
        val validRules = validConditions + validGroups
        require(validRules.isNotEmpty()) { "Add at least one complete rule or group." }

        return SmartPlaylistDefinition(
            name = name,
            comment = comment,
            match = match,
            rules = validRules,
            sort = sort.mapNotNull { it.toSortOrNull() },
            limit = limit.takeIf { limitMode == SmartPlaylistLimitMode.TrackCount },
            limitPercent = limit.takeIf { limitMode == SmartPlaylistLimitMode.Percent },
            isPublic = isPublic,
            libraryIds = selectedLibraryIds,
            refreshDelay = refreshDelay.trim().takeIf { it.isNotEmpty() },
        )
    }

    companion object {
        fun fromDefinition(definition: SmartPlaylistDefinition): SmartPlaylistDraft {
            require(definition.rules.all { rule ->
                rule is SmartPlaylistCondition ||
                    (rule is SmartPlaylistGroup && rule.rules.all { it is SmartPlaylistCondition })
            }) { "The builder supports condition-only groups; nested rules cannot be edited safely." }
            val topLevelConditions = definition.rules.mapNotNull { it as? SmartPlaylistCondition }
            val groups = definition.rules.mapNotNull { it as? SmartPlaylistGroup }.map { group ->
                SmartPlaylistGroupDraft(
                    match = group.match,
                    conditions = group.rules.mapNotNull { rule ->
                        (rule as? SmartPlaylistCondition)?.toDraft()
                    }.ifEmpty { listOf(SmartPlaylistConditionDraft()) },
                )
            }
            val limitMode = when {
                definition.limitPercent != null -> SmartPlaylistLimitMode.Percent
                definition.limit != null -> SmartPlaylistLimitMode.TrackCount
                else -> SmartPlaylistLimitMode.Unlimited
            }

            return SmartPlaylistDraft(
                name = definition.name,
                comment = definition.comment.orEmpty(),
                match = definition.match,
                conditions = topLevelConditions.map { it.toDraft() }.ifEmpty { listOf(SmartPlaylistConditionDraft(isStarter = true)) },
                groups = groups,
                sort = definition.sort.map { it.toDraft() },
                limitMode = limitMode,
                limit = definition.limitPercent ?: definition.limit ?: 100,
                isPublic = definition.isPublic ?: false,
                selectedLibraryIds = definition.libraryIds,
                refreshDelay = definition.refreshDelay.orEmpty(),
            )
        }
    }
}

data class SmartPlaylistGroupDraft(
    val match: SmartPlaylistMatch = SmartPlaylistMatch.Any,
    val conditions: List<SmartPlaylistConditionDraft> = listOf(SmartPlaylistConditionDraft()),
    val id: Long = Random.nextLong(),
) {
    fun toRuleOrNull(): SmartPlaylistGroup? {
        if (conditions.any { it.validate(id).isNotEmpty() }) return null
        val validConditions = conditions.map { it.toRuleOrNull() ?: return null }
        if (validConditions.isEmpty()) return null
        return SmartPlaylistGroup(match = match, rules = validConditions)
    }
}

data class SmartPlaylistConditionDraft(
    val field: SmartPlaylistFieldOption = SmartPlaylistFieldCatalog.defaultField,
    val operator: SmartPlaylistOperator = SmartPlaylistFieldCatalog.defaultField.operators.first(),
    val value: String = "",
    val secondValue: String = "",
    val genreSelection: SmartPlaylistGenreSelection? = null,
    val id: Long = Random.nextLong(),
    val isStarter: Boolean = false,
) {
    val isUntouchedStarter: Boolean
        get() = isStarter && this.field == SmartPlaylistFieldCatalog.defaultField &&
            operator == SmartPlaylistFieldCatalog.defaultField.operators.first() &&
            value.isEmpty() && secondValue.isEmpty() && genreSelection == null

    fun toConditionOrNull(): SmartPlaylistCondition? {
        if (validate().isNotEmpty()) return null
        val parsedValue = parseValue() ?: return null
        return SmartPlaylistCondition(
            operator = operator,
            field = field.field,
            value = parsedValue,
        )
    }

    fun toRuleOrNull(): SmartPlaylistRule? {
        val condition = toConditionOrNull() ?: return null
        val selection = genreSelection
            ?.takeIf { field.field == SmartPlaylistFields.Genre }
            ?.takeUnless {
                operator == SmartPlaylistOperator.IsMissing ||
                    operator == SmartPlaylistOperator.IsPresent
            }
            ?: return condition
        val providerNames = selection.libraryGenreNames
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
        if (providerNames.isEmpty()) return condition.copy(
            value = SmartPlaylistValue.Text(selection.canonicalName),
        )
        if (providerNames.size == 1) return condition.copy(
            value = SmartPlaylistValue.Text(providerNames.single()),
        )
        val match = when (operator) {
            SmartPlaylistOperator.IsNot,
            SmartPlaylistOperator.NotContains,
            -> SmartPlaylistMatch.All
            else -> SmartPlaylistMatch.Any
        }
        return SmartPlaylistGroup(
            match = match,
            rules = providerNames.map { providerName ->
                condition.copy(value = SmartPlaylistValue.Text(providerName))
            },
        )
    }

    private fun parseValue(): SmartPlaylistValue? {
        val first = value.trim()
        val second = secondValue.trim()
        if (operator == SmartPlaylistOperator.IsMissing || operator == SmartPlaylistOperator.IsPresent) {
            return SmartPlaylistValue.Flag(
                when (first.lowercase()) {
                    "false" -> false
                    else -> true
                },
            )
        }
        if (operator == SmartPlaylistOperator.InTheRange) {
            val start = field.parseValue(first) ?: return null
            val end = field.parseValue(second) ?: return null
            return SmartPlaylistValue.Range(start, end)
        }
        if (operator == SmartPlaylistOperator.InTheLast || operator == SmartPlaylistOperator.NotInTheLast) {
            return first.toIntOrNull()?.takeIf { it > 0 }?.let { SmartPlaylistValue.Number(it) }
        }
        return field.parseValue(first)
    }
}

data class SmartPlaylistSortDraft(
    val field: SmartPlaylistFieldOption = SmartPlaylistFieldCatalog.sortableFields.first(),
    val descending: Boolean = true,
    val id: Long = Random.nextLong(),
) {
    fun toSortOrNull(): SmartPlaylistSort? =
        field.takeIf { it.sortable }?.let { SmartPlaylistSort(it.field, descending) }
}

private fun SmartPlaylistCondition.toDraft(): SmartPlaylistConditionDraft {
    require((operator == SmartPlaylistOperator.InTheRange) == (value is SmartPlaylistValue.Range)) {
        "Imported smart playlist range values must use the range operator."
    }
    if (value is SmartPlaylistValue.Range) {
        require(value.start !is SmartPlaylistValue.Range && value.end !is SmartPlaylistValue.Range) {
            "Imported smart playlist ranges cannot contain nested ranges."
        }
    }
    val fieldOption = SmartPlaylistFieldCatalog.fields.firstOrNull { it.field == field }
        ?: field.smartPlaylistCustomTagFieldOptionOrNull(operator)
        ?: throw IllegalArgumentException("Imported smart playlist field '$field' is not supported by the builder.")
    val draft = SmartPlaylistConditionDraft(
        field = fieldOption,
        operator = operator,
        value = value.firstDraftValue(),
        secondValue = value.secondDraftValue(),
    )
    require(operator in fieldOption.operators && draft.toConditionOrNull() != null) {
        "Imported smart playlist value cannot be represented safely for field '$field'."
    }
    return draft
}

private fun SmartPlaylistSort.toDraft(): SmartPlaylistSortDraft =
    SmartPlaylistSortDraft(
        field = SmartPlaylistFieldCatalog.sortableFields.first { it.field == field },
        descending = descending,
    )

private fun SmartPlaylistValue.firstDraftValue(): String =
    when (this) {
        is SmartPlaylistValue.Text -> value
        is SmartPlaylistValue.Number -> value.toString()
        is SmartPlaylistValue.Decimal -> value.toString()
        is SmartPlaylistValue.Flag -> value.toString()
        is SmartPlaylistValue.Range -> start.firstDraftValue()
    }

private fun SmartPlaylistValue.secondDraftValue(): String =
    when (this) {
        is SmartPlaylistValue.Range -> end.firstDraftValue()
        else -> ""
    }

enum class SmartPlaylistLimitMode {
    Unlimited,
    TrackCount,
    Percent,
}

data class SmartPlaylistFieldOption(
    val field: String,
    val label: String,
    val valueType: SmartPlaylistValueType,
    val operators: List<SmartPlaylistOperator>,
    val sortable: Boolean = true,
    val constraints: SmartPlaylistFieldConstraints = smartPlaylistFieldConstraints(field),
) {
    fun parseValue(rawValue: String): SmartPlaylistValue? {
        if (rawValue.isBlank() && rawValue !in constraints.textValues.orEmpty()) return null
        return when (valueType) {
            SmartPlaylistValueType.Text -> SmartPlaylistValue.Text(rawValue)
            SmartPlaylistValueType.Integer -> rawValue.toLongOrNull()?.let { SmartPlaylistValue.Number(it) }
            SmartPlaylistValueType.Decimal -> rawValue.toLongOrNull()?.let { SmartPlaylistValue.Number(it) }
                ?: rawValue.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { SmartPlaylistValue.Decimal(it) }
            SmartPlaylistValueType.Boolean -> parseBoolean(rawValue)?.let { SmartPlaylistValue.Flag(it) }
            SmartPlaylistValueType.Days -> rawValue.toIntOrNull()?.takeIf { it > 0 }?.let { SmartPlaylistValue.Number(it) }
            SmartPlaylistValueType.Date -> rawValue.takeIf(::isSmartPlaylistCalendarDate)?.let { SmartPlaylistValue.Text(it) }
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

internal fun String.smartPlaylistCustomTagFieldOptionOrNull(operator: SmartPlaylistOperator): SmartPlaylistFieldOption? {
    if (operator != SmartPlaylistOperator.IsMissing && operator != SmartPlaylistOperator.IsPresent) return null
    return SmartPlaylistFieldOption(
        field = this,
        label = this,
        valueType = SmartPlaylistValueType.Boolean,
        operators = listOf(SmartPlaylistOperator.IsMissing, SmartPlaylistOperator.IsPresent),
        sortable = false,
    )
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

    private val presenceOperators = listOf(
        SmartPlaylistOperator.IsMissing,
        SmartPlaylistOperator.IsPresent,
    )

    private val nullableTextOperators = textOperators + presenceOperators

    val comparableOperators = listOf(
        SmartPlaylistOperator.Is,
        SmartPlaylistOperator.IsNot,
        SmartPlaylistOperator.GreaterThan,
        SmartPlaylistOperator.LessThan,
        SmartPlaylistOperator.InTheRange,
    )

    private val nullableComparableOperators = comparableOperators + presenceOperators

    val dateOperators = listOf(
        SmartPlaylistOperator.Before,
        SmartPlaylistOperator.After,
        SmartPlaylistOperator.InTheLast,
        SmartPlaylistOperator.NotInTheLast,
    )

    val fields = listOf(
        SmartPlaylistFieldOption(SmartPlaylistFields.Title, "Title", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Artist, "Artist", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Album, "Album", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Genre, "Genre", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.SortTitle, "Sort Title", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.SortArtist, "Sort Artist", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.SortAlbum, "Sort Album", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.SortAlbumArtist, "Sort Album Artist", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Comment, "Comment", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Lyrics, "Lyrics", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.DiscSubtitle, "Disc Subtitle", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.CatalogNumber, "Catalog Number", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.FilePath, "File Path", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.FileType, "File Type", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.ExplicitStatus, "Explicit Status", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.AlbumComment, "Album Comment", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.MusicBrainzAlbumId, "MusicBrainz Album ID", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.MusicBrainzAlbumArtistId,
            "MusicBrainz Album Artist ID",
            SmartPlaylistValueType.Text,
            nullableTextOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.MusicBrainzArtistId,
            "MusicBrainz Artist ID",
            SmartPlaylistValueType.Text,
            nullableTextOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.MusicBrainzRecordingId,
            "MusicBrainz Recording ID",
            SmartPlaylistValueType.Text,
            nullableTextOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.MusicBrainzReleaseTrackId,
            "MusicBrainz Release Track ID",
            SmartPlaylistValueType.Text,
            nullableTextOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.MusicBrainzReleaseGroupId,
            "MusicBrainz Release Group ID",
            SmartPlaylistValueType.Text,
            nullableTextOperators,
        ),
        SmartPlaylistFieldOption(SmartPlaylistFields.Year, "Year", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.OriginalYear, "Original Year", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.ReleaseYear, "Release Year", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.TrackNumber, "Track Number", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.DiscNumber, "Disc Number", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Rating, "Rating", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.AverageRating, "Average Rating", SmartPlaylistValueType.Decimal, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.PlayCount, "Play Count", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Size, "File Size", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Duration, "Duration", SmartPlaylistValueType.Decimal, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Bitrate, "Bitrate", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.BitDepth, "Bit Depth", SmartPlaylistValueType.Integer, nullableComparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.SampleRate, "Sample Rate", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Bpm, "BPM", SmartPlaylistValueType.Integer, nullableComparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Channels, "Channels", SmartPlaylistValueType.Integer, comparableOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.Codec, "Codec", SmartPlaylistValueType.Text, nullableTextOperators),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.ReplayGainAlbumGain,
            "ReplayGain Album Gain",
            SmartPlaylistValueType.Decimal,
            nullableComparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.ReplayGainAlbumPeak,
            "ReplayGain Album Peak",
            SmartPlaylistValueType.Decimal,
            nullableComparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.ReplayGainTrackGain,
            "ReplayGain Track Gain",
            SmartPlaylistValueType.Decimal,
            nullableComparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.ReplayGainTrackPeak,
            "ReplayGain Track Peak",
            SmartPlaylistValueType.Decimal,
            nullableComparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.Loved,
            "Favorite",
            SmartPlaylistValueType.Boolean,
            listOf(SmartPlaylistOperator.Is, SmartPlaylistOperator.IsNot),
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.HasCoverArt,
            "Has Cover Art",
            SmartPlaylistValueType.Boolean,
            listOf(SmartPlaylistOperator.Is, SmartPlaylistOperator.IsNot),
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.Compilation,
            "Compilation",
            SmartPlaylistValueType.Boolean,
            listOf(SmartPlaylistOperator.Is, SmartPlaylistOperator.IsNot),
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.Missing,
            "Missing File",
            SmartPlaylistValueType.Boolean,
            listOf(SmartPlaylistOperator.Is, SmartPlaylistOperator.IsNot),
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.AlbumRating,
            "Album Rating",
            SmartPlaylistValueType.Integer,
            comparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.AlbumLoved,
            "Album Favorite",
            SmartPlaylistValueType.Boolean,
            listOf(SmartPlaylistOperator.Is, SmartPlaylistOperator.IsNot),
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.AlbumPlayCount,
            "Album Play Count",
            SmartPlaylistValueType.Integer,
            comparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.ArtistRating,
            "Artist Rating",
            SmartPlaylistValueType.Integer,
            comparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.ArtistLoved,
            "Artist Favorite",
            SmartPlaylistValueType.Boolean,
            listOf(SmartPlaylistOperator.Is, SmartPlaylistOperator.IsNot),
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.ArtistPlayCount,
            "Artist Play Count",
            SmartPlaylistValueType.Integer,
            comparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.LibraryId,
            "Library ID",
            SmartPlaylistValueType.Integer,
            comparableOperators,
        ),
        SmartPlaylistFieldOption(SmartPlaylistFields.Date, "Recording Date", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.OriginalDate, "Original Date", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.ReleaseDate, "Release Date", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.DateLoved, "Date Favorited", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.DateAdded, "Date Added", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.DateModified, "Date Modified", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.DateRated, "Date Rated", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.LastPlayed, "Last Played", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.AlbumLastPlayed, "Album Last Played", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.AlbumDateLoved, "Album Date Favorited", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.AlbumDateRated, "Album Date Rated", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.AlbumDateAdded,
            SmartPlaylistFields.AlbumDateAdded,
            SmartPlaylistValueType.Date,
            dateOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.AlbumDateModified,
            SmartPlaylistFields.AlbumDateModified,
            SmartPlaylistValueType.Date,
            dateOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.AlbumDuration,
            SmartPlaylistFields.AlbumDuration,
            SmartPlaylistValueType.Decimal,
            comparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.AlbumSongCount,
            SmartPlaylistFields.AlbumSongCount,
            SmartPlaylistValueType.Integer,
            comparableOperators,
        ),
        SmartPlaylistFieldOption(
            SmartPlaylistFields.AlbumSize,
            SmartPlaylistFields.AlbumSize,
            SmartPlaylistValueType.Integer,
            comparableOperators,
        ),
        SmartPlaylistFieldOption(SmartPlaylistFields.ArtistLastPlayed, "Artist Last Played", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.ArtistDateLoved, "Artist Date Favorited", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(SmartPlaylistFields.ArtistDateRated, "Artist Date Rated", SmartPlaylistValueType.Date, dateOperators),
        SmartPlaylistFieldOption(
            "id",
            "Playlist ID",
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

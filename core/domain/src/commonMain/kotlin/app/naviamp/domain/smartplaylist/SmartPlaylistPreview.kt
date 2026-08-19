package app.naviamp.domain.smartplaylist

import app.naviamp.domain.Track

data class SmartPlaylistPreview(
    val matchingTrackCount: Int? = null,
    val resultTrackCount: Int? = null,
    val exampleTracks: List<Track> = emptyList(),
    val unsupportedFields: List<String> = emptyList(),
    val message: String? = null,
) {
    val available: Boolean
        get() = matchingTrackCount != null
}

fun previewSmartPlaylist(
    definition: SmartPlaylistDefinition,
    tracks: List<Track>,
    exampleLimit: Int = 5,
    nowEpochMillis: Long,
): SmartPlaylistPreview {
    val unsupported = definition.rules
        .flatMapTo(linkedSetOf(), SmartPlaylistRule::unsupportedPreviewFields)
    if (definition.libraryIds != null && tracks.any { it.musicFolderId == null }) {
        unsupported += SmartPlaylistFields.LibraryId
    }
    if (unsupported.isNotEmpty()) {
        return SmartPlaylistPreview(unsupportedFields = unsupported.sorted())
    }

    val selectedLibraryIds = definition.libraryIds?.toSet()
    val matches = tracks.asSequence()
        .filter { track -> selectedLibraryIds == null || track.musicFolderId in selectedLibraryIds }
        .filter { track ->
            when (definition.match) {
                SmartPlaylistMatch.All -> definition.rules.all { it.matches(track, nowEpochMillis) }
                SmartPlaylistMatch.Any -> definition.rules.any { it.matches(track, nowEpochMillis) }
            }
        }
        .toList()
    val resultCount = when {
        definition.limit != null -> minOf(matches.size, definition.limit)
        definition.limitPercent != null -> minOf(
            matches.size,
            (matches.size * definition.limitPercent + 99) / 100,
        )
        else -> matches.size
    }
    return SmartPlaylistPreview(
        matchingTrackCount = matches.size,
        resultTrackCount = resultCount,
        exampleTracks = matches.take(minOf(exampleLimit.coerceAtLeast(0), resultCount)),
    )
}

private fun SmartPlaylistRule.unsupportedPreviewFields(): Set<String> =
    when (this) {
        is SmartPlaylistCondition -> when {
            field !in PreviewSupportedFields -> setOf(field)
            field in PreviewDateFields && (
                operator == SmartPlaylistOperator.InTheLast ||
                    operator == SmartPlaylistOperator.NotInTheLast
                ) -> setOf(field)
            else -> emptySet()
        }
        is SmartPlaylistGroup -> rules.flatMapTo(linkedSetOf(), SmartPlaylistRule::unsupportedPreviewFields)
    }

private fun SmartPlaylistRule.matches(track: Track, nowEpochMillis: Long): Boolean =
    when (this) {
        is SmartPlaylistCondition -> matches(track, nowEpochMillis)
        is SmartPlaylistGroup -> when (match) {
            SmartPlaylistMatch.All -> rules.all { it.matches(track, nowEpochMillis) }
            SmartPlaylistMatch.Any -> rules.any { it.matches(track, nowEpochMillis) }
        }
    }

private fun SmartPlaylistCondition.matches(track: Track, nowEpochMillis: Long): Boolean {
    val values = track.previewValues(field)
    if (operator == SmartPlaylistOperator.IsMissing || operator == SmartPlaylistOperator.IsPresent) {
        val missing = values.isEmpty() || values.all { it == null || it.toString().isBlank() }
        val requested = (value as? SmartPlaylistValue.Flag)?.value ?: true
        return when (operator) {
            SmartPlaylistOperator.IsMissing -> missing == requested
            SmartPlaylistOperator.IsPresent -> !missing == requested
        }
    }
    return when (operator) {
        SmartPlaylistOperator.IsNot,
        SmartPlaylistOperator.NotContains,
        SmartPlaylistOperator.NotInTheLast,
        -> values.all { candidate -> !candidate.matchesPositive(operator.positive(), value, nowEpochMillis) }
        else -> values.any { candidate -> candidate.matchesPositive(operator, value, nowEpochMillis) }
    }
}

private fun Any?.matchesPositive(
    operator: SmartPlaylistOperator,
    requested: SmartPlaylistValue,
    nowEpochMillis: Long,
): Boolean {
    return when (requested) {
    is SmartPlaylistValue.Text -> {
        val candidate = this?.toString().orEmpty()
        when (operator) {
            SmartPlaylistOperator.Is -> candidate.equals(requested.value, ignoreCase = true)
            SmartPlaylistOperator.Contains -> candidate.contains(requested.value, ignoreCase = true)
            SmartPlaylistOperator.StartsWith -> candidate.startsWith(requested.value, ignoreCase = true)
            SmartPlaylistOperator.EndsWith -> candidate.endsWith(requested.value, ignoreCase = true)
            SmartPlaylistOperator.Before -> candidate < requested.value
            SmartPlaylistOperator.After -> candidate > requested.value
            else -> false
        }
    }
    is SmartPlaylistValue.Number -> {
        val candidate = (this as? Number)?.toLong() ?: return false
        when (operator) {
            SmartPlaylistOperator.Is -> candidate == requested.value
            SmartPlaylistOperator.GreaterThan -> candidate > requested.value
            SmartPlaylistOperator.LessThan -> candidate < requested.value
            SmartPlaylistOperator.InTheLast -> candidate >= nowEpochMillis - requested.value * MillisPerDay
            else -> false
        }
    }
    is SmartPlaylistValue.Decimal -> {
        val candidate = (this as? Number)?.toDouble() ?: return false
        when (operator) {
            SmartPlaylistOperator.Is -> candidate == requested.value
            SmartPlaylistOperator.GreaterThan -> candidate > requested.value
            SmartPlaylistOperator.LessThan -> candidate < requested.value
            else -> false
        }
    }
    is SmartPlaylistValue.Flag -> {
        val candidate = this as? Boolean ?: return false
        operator == SmartPlaylistOperator.Is && candidate == requested.value
    }
    is SmartPlaylistValue.Range -> {
        val candidate = (this as? Number)?.toDouble() ?: return false
        val start = requested.start.numberValue() ?: return false
        val end = requested.end.numberValue() ?: return false
        operator == SmartPlaylistOperator.InTheRange && candidate in minOf(start, end)..maxOf(start, end)
    }
    }
}

private fun SmartPlaylistValue.numberValue(): Double? = when (this) {
    is SmartPlaylistValue.Number -> value.toDouble()
    is SmartPlaylistValue.Decimal -> value
    else -> null
}

private fun SmartPlaylistOperator.positive(): SmartPlaylistOperator = when (this) {
    SmartPlaylistOperator.IsNot -> SmartPlaylistOperator.Is
    SmartPlaylistOperator.NotContains -> SmartPlaylistOperator.Contains
    SmartPlaylistOperator.NotInTheLast -> SmartPlaylistOperator.InTheLast
    else -> this
}

private fun Track.previewValues(field: String): List<Any?> = when (field) {
    SmartPlaylistFields.Title -> listOf(title)
    SmartPlaylistFields.Artist -> listOf(artistName)
    SmartPlaylistFields.Album -> listOf(albumTitle)
    SmartPlaylistFields.Genre -> genres
    SmartPlaylistFields.Year,
    SmartPlaylistFields.ReleaseYear,
    -> listOf(albumReleaseYear)
    SmartPlaylistFields.Rating -> listOf(userRating)
    SmartPlaylistFields.PlayCount -> listOf(playCount ?: 0)
    SmartPlaylistFields.Duration -> listOf(durationSeconds)
    SmartPlaylistFields.Bitrate -> listOf(audioInfo?.bitrateKbps)
    SmartPlaylistFields.BitDepth -> listOf(audioInfo?.bitDepth)
    SmartPlaylistFields.SampleRate -> listOf(audioInfo?.samplingRateHz)
    SmartPlaylistFields.Bpm -> listOf(bpm)
    SmartPlaylistFields.Codec -> listOf(audioInfo?.codec)
    SmartPlaylistFields.Loved -> listOf(favoritedAtIso8601 != null)
    SmartPlaylistFields.HasCoverArt -> listOf(coverArtId != null)
    SmartPlaylistFields.LastPlayed -> listOf(lastPlayedAtIso8601)
    SmartPlaylistFields.DateLoved -> listOf(favoritedAtIso8601)
    SmartPlaylistFields.LibraryId -> listOf(musicFolderId)
    else -> emptyList()
}

private val PreviewSupportedFields = setOf(
    SmartPlaylistFields.Title,
    SmartPlaylistFields.Artist,
    SmartPlaylistFields.Album,
    SmartPlaylistFields.Genre,
    SmartPlaylistFields.Year,
    SmartPlaylistFields.ReleaseYear,
    SmartPlaylistFields.Rating,
    SmartPlaylistFields.PlayCount,
    SmartPlaylistFields.Duration,
    SmartPlaylistFields.Bitrate,
    SmartPlaylistFields.BitDepth,
    SmartPlaylistFields.SampleRate,
    SmartPlaylistFields.Bpm,
    SmartPlaylistFields.Codec,
    SmartPlaylistFields.Loved,
    SmartPlaylistFields.HasCoverArt,
    SmartPlaylistFields.LastPlayed,
    SmartPlaylistFields.DateLoved,
    SmartPlaylistFields.LibraryId,
)

private val PreviewDateFields = setOf(
    SmartPlaylistFields.LastPlayed,
    SmartPlaylistFields.DateLoved,
)

private const val MillisPerDay = 86_400_000L

package app.naviamp.domain.smartplaylist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val SmartPlaylistJson = Json {
    prettyPrint = true
    explicitNulls = false
}

data class SmartPlaylistDefinition(
    val name: String,
    val comment: String? = null,
    val match: SmartPlaylistMatch = SmartPlaylistMatch.All,
    val rules: List<SmartPlaylistRule>,
    val sort: List<SmartPlaylistSort> = emptyList(),
    val limit: Int? = null,
    val limitPercent: Int? = null,
    val isPublic: Boolean? = null,
) {
    init {
        require(name.isNotBlank()) { "Smart playlist name is required." }
        require(rules.isNotEmpty()) { "Smart playlist rules are required." }
        require(limit == null || limit > 0) { "Smart playlist limit must be greater than zero." }
        require(limitPercent == null || limitPercent in 1..100) {
            "Smart playlist limitPercent must be between 1 and 100."
        }
        require(limit == null || limitPercent == null) {
            "Smart playlist can use limit or limitPercent, not both."
        }
    }

    fun toJsonElement(): JsonObject = buildJsonObject {
        put("name", name.trim())
        comment?.trim()?.takeIf { it.isNotEmpty() }?.let { put("comment", it) }
        putRules()
        isPublic?.let { put("public", it) }
    }

    fun toRulesJsonElement(): JsonObject = buildJsonObject {
        putRules()
    }

    private fun JsonObjectBuilder.putRules() {
        put(match.jsonName, rules.toJsonArray())
        if (sort.isNotEmpty()) put("sort", sort.joinToString(",") { it.toNavidromeSortValue() })
        limit?.let { put("limit", it) }
        limitPercent?.let { put("limitPercent", it) }
    }

    fun toNspJson(): String = SmartPlaylistJson.encodeToString(JsonObject.serializer(), toJsonElement())

    fun defaultFileName(): String =
        name.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "smart-playlist" } + ".nsp"
}

enum class SmartPlaylistMatch(val jsonName: String) {
    All("all"),
    Any("any"),
}

sealed interface SmartPlaylistRule {
    fun toJsonElement(): JsonObject
}

data class SmartPlaylistCondition(
    val operator: SmartPlaylistOperator,
    val field: String,
    val value: SmartPlaylistValue,
) : SmartPlaylistRule {
    init {
        require(field.isNotBlank()) { "Smart playlist field is required." }
    }

    override fun toJsonElement(): JsonObject = buildJsonObject {
        put(
            operator.jsonName,
            buildJsonObject {
                put(field.trim(), value.toJsonElement())
            },
        )
    }
}

data class SmartPlaylistGroup(
    val match: SmartPlaylistMatch,
    val rules: List<SmartPlaylistRule>,
) : SmartPlaylistRule {
    init {
        require(rules.isNotEmpty()) { "Smart playlist group rules are required." }
    }

    override fun toJsonElement(): JsonObject = buildJsonObject {
        put(match.jsonName, rules.toJsonArray())
    }
}

enum class SmartPlaylistOperator(val jsonName: String) {
    Is("is"),
    IsNot("isNot"),
    GreaterThan("gt"),
    LessThan("lt"),
    Contains("contains"),
    NotContains("notContains"),
    StartsWith("startsWith"),
    EndsWith("endsWith"),
    InTheRange("inTheRange"),
    Before("before"),
    After("after"),
    InTheLast("inTheLast"),
    NotInTheLast("notInTheLast"),
    InPlaylist("inPlaylist"),
    NotInPlaylist("notInPlaylist"),
}

sealed interface SmartPlaylistValue {
    fun toJsonElement(): JsonElement

    data class Text(val value: String) : SmartPlaylistValue {
        override fun toJsonElement(): JsonElement = JsonPrimitive(value)
    }

    data class Number(val value: Long) : SmartPlaylistValue {
        constructor(value: Int) : this(value.toLong())

        override fun toJsonElement(): JsonElement = JsonPrimitive(value)
    }

    data class Decimal(val value: Double) : SmartPlaylistValue {
        override fun toJsonElement(): JsonElement = JsonPrimitive(value)
    }

    data class Flag(val value: Boolean) : SmartPlaylistValue {
        override fun toJsonElement(): JsonElement = JsonPrimitive(value)
    }

    data class Range(val start: SmartPlaylistValue, val end: SmartPlaylistValue) : SmartPlaylistValue {
        override fun toJsonElement(): JsonElement = JsonArray(listOf(start.toJsonElement(), end.toJsonElement()))
    }
}

data class SmartPlaylistSort(
    val field: String,
    val descending: Boolean = false,
) {
    init {
        require(field.isNotBlank()) { "Smart playlist sort field is required." }
    }

    fun toNavidromeSortValue(): String = "${if (descending) "-" else ""}${field.trim()}"
}

object SmartPlaylistFields {
    const val Title = "title"
    const val Album = "album"
    const val HasCoverArt = "hascoverart"
    const val TrackNumber = "tracknumber"
    const val DiscNumber = "discnumber"
    const val Artist = "artist"
    const val Genre = "genre"
    const val Year = "year"
    const val Date = "date"
    const val OriginalYear = "originalyear"
    const val OriginalDate = "originaldate"
    const val ReleaseYear = "releaseyear"
    const val ReleaseDate = "releasedate"
    const val Size = "size"
    const val Compilation = "compilation"
    const val Missing = "missing"
    const val ExplicitStatus = "explicitstatus"
    const val DateAdded = "dateadded"
    const val DateModified = "datemodified"
    const val DiscSubtitle = "discsubtitle"
    const val Comment = "comment"
    const val Lyrics = "lyrics"
    const val SortTitle = "sorttitle"
    const val SortAlbum = "sortalbum"
    const val SortArtist = "sortartist"
    const val SortAlbumArtist = "sortalbumartist"
    const val AlbumComment = "albumcomment"
    const val CatalogNumber = "catalognumber"
    const val FilePath = "filepath"
    const val FileType = "filetype"
    const val Loved = "loved"
    const val DateLoved = "dateloved"
    const val LastPlayed = "lastplayed"
    const val DateRated = "daterated"
    const val PlayCount = "playcount"
    const val Rating = "rating"
    const val AverageRating = "averagerating"
    const val AlbumRating = "albumrating"
    const val AlbumLoved = "albumloved"
    const val AlbumPlayCount = "albumplaycount"
    const val AlbumLastPlayed = "albumlastplayed"
    const val AlbumDateLoved = "albumdateloved"
    const val AlbumDateRated = "albumdaterated"
    const val ArtistRating = "artistrating"
    const val ArtistLoved = "artistloved"
    const val ArtistPlayCount = "artistplaycount"
    const val ArtistLastPlayed = "artistlastplayed"
    const val ArtistDateLoved = "artistdateloved"
    const val ArtistDateRated = "artistdaterated"
    const val MusicBrainzAlbumId = "mbz_album_id"
    const val MusicBrainzAlbumArtistId = "mbz_album_artist_id"
    const val MusicBrainzArtistId = "mbz_artist_id"
    const val MusicBrainzRecordingId = "mbz_recording_id"
    const val MusicBrainzReleaseTrackId = "mbz_release_track_id"
    const val MusicBrainzReleaseGroupId = "mbz_release_group_id"
    const val Bitrate = "bitrate"
    const val BitDepth = "bitdepth"
    const val SampleRate = "samplerate"
    const val Bpm = "bpm"
    const val Channels = "channels"
    const val Duration = "duration"
    const val Codec = "codec"
    const val LibraryId = "library_id"
    const val Random = "random"
}

object SmartPlaylistTemplates {
    fun recentlyPlayed(days: Int = 30, limit: Int = 100): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "Recently Played",
            comment = "Recently played tracks",
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.InTheLast,
                    SmartPlaylistFields.LastPlayed,
                    SmartPlaylistValue.Number(days),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.LastPlayed, descending = true)),
            limit = limit,
        )

    fun favorites(limit: Int = 500): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "Favorites",
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.Is,
                    SmartPlaylistFields.Loved,
                    SmartPlaylistValue.Flag(true),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.DateLoved, descending = true)),
            limit = limit,
        )

    fun topSongsFromTheEighties(limit: Int = 25): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "80s Top Songs",
            rules = listOf(
                SmartPlaylistGroup(
                    SmartPlaylistMatch.Any,
                    listOf(
                        SmartPlaylistCondition(
                            SmartPlaylistOperator.Is,
                            SmartPlaylistFields.Loved,
                            SmartPlaylistValue.Flag(true),
                        ),
                        SmartPlaylistCondition(
                            SmartPlaylistOperator.GreaterThan,
                            SmartPlaylistFields.Rating,
                            SmartPlaylistValue.Number(3),
                        ),
                    ),
                ),
                SmartPlaylistCondition(
                    SmartPlaylistOperator.InTheRange,
                    SmartPlaylistFields.Year,
                    SmartPlaylistValue.Range(
                        SmartPlaylistValue.Number(1981),
                        SmartPlaylistValue.Number(1990),
                    ),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.Year, descending = true)),
            limit = limit,
        )

    fun randomLibrarySongs(limit: Int = 1000): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "Random Library Songs",
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.GreaterThan,
                    SmartPlaylistFields.PlayCount,
                    SmartPlaylistValue.Number(-1),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.Random)),
            limit = limit,
        )

    fun recentlyAdded(days: Int = 30, limit: Int = 100): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "Recently Added",
            comment = "Tracks added in the last $days days",
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.InTheLast,
                    SmartPlaylistFields.DateAdded,
                    SmartPlaylistValue.Number(days),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.DateAdded, descending = true)),
            limit = limit,
        )

    fun highRated(minimumRating: Int = 4, limit: Int = 250): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "High Rated",
            comment = "Tracks rated $minimumRating or higher",
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.GreaterThan,
                    SmartPlaylistFields.Rating,
                    SmartPlaylistValue.Number(minimumRating - 1),
                ),
            ),
            sort = listOf(
                SmartPlaylistSort(SmartPlaylistFields.Rating, descending = true),
                SmartPlaylistSort(SmartPlaylistFields.Title),
            ),
            limit = limit,
        )

    fun unplayedFavorites(limit: Int = 250): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "Unplayed Favorites",
            comment = "Loved tracks that have not been played yet",
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.Is,
                    SmartPlaylistFields.Loved,
                    SmartPlaylistValue.Flag(true),
                ),
                SmartPlaylistCondition(
                    SmartPlaylistOperator.Is,
                    SmartPlaylistFields.PlayCount,
                    SmartPlaylistValue.Number(0),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.DateLoved, descending = true)),
            limit = limit,
        )

    fun genreRadioSeedList(genre: String = "Electronic", limit: Int = 100): SmartPlaylistDefinition =
        SmartPlaylistDefinition(
            name = "$genre Radio Seeds",
            comment = "Loved or highly rated $genre tracks for radio seeding",
            rules = listOf(
                SmartPlaylistCondition(
                    SmartPlaylistOperator.Contains,
                    SmartPlaylistFields.Genre,
                    SmartPlaylistValue.Text(genre),
                ),
                SmartPlaylistGroup(
                    SmartPlaylistMatch.Any,
                    listOf(
                        SmartPlaylistCondition(
                            SmartPlaylistOperator.Is,
                            SmartPlaylistFields.Loved,
                            SmartPlaylistValue.Flag(true),
                        ),
                        SmartPlaylistCondition(
                            SmartPlaylistOperator.GreaterThan,
                            SmartPlaylistFields.Rating,
                            SmartPlaylistValue.Number(3),
                        ),
                    ),
                ),
            ),
            sort = listOf(SmartPlaylistSort(SmartPlaylistFields.Random)),
            limit = limit,
        )
}

private fun List<SmartPlaylistRule>.toJsonArray(): JsonArray =
    buildJsonArray {
        forEach { add(it.toJsonElement()) }
    }

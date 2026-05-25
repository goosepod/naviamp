package app.naviamp.domain.smartplaylist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
        put(match.jsonName, rules.toJsonArray())
        if (sort.isNotEmpty()) put("sort", sort.joinToString(",") { it.toNavidromeSortValue() })
        limit?.let { put("limit", it) }
        limitPercent?.let { put("limitPercent", it) }
        isPublic?.let { put("public", it) }
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

    data class Number(val value: Int) : SmartPlaylistValue {
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
    const val Artist = "artist"
    const val Genre = "genre"
    const val Year = "year"
    const val DateAdded = "dateadded"
    const val Loved = "loved"
    const val DateLoved = "dateloved"
    const val LastPlayed = "lastplayed"
    const val PlayCount = "playcount"
    const val Rating = "rating"
    const val Bitrate = "bitrate"
    const val Codec = "codec"
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
}

private fun List<SmartPlaylistRule>.toJsonArray(): JsonArray =
    buildJsonArray {
        forEach { add(it.toJsonElement()) }
    }
